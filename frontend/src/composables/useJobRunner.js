import { ref, onUnmounted } from 'vue'
import { api } from '../api'

const POLL_MS = 500
const TERMINAL = new Set(['COMPLETED', 'FAILED'])

export function useJobRunner() {
  const running = ref(false)
  const job = ref(null)
  const status = ref('IDLE')
  const error = ref('')
  let timer = null

  function isPolling() {
    return timer != null
  }

  function stopPoll() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  onUnmounted(stopPoll)

  async function pollUntilDone(jobId) {
    return new Promise((resolve, reject) => {
      const tick = async () => {
        try {
          const data = await api.jobs.get(jobId)
          job.value = data
          status.value = data.status
          if (TERMINAL.has(data.status)) {
            stopPoll()
            running.value = false
            if (data.status === 'FAILED') {
              const msg = data.error || data.message || '任务失败'
              error.value = msg
              reject(new Error(msg))
            } else {
              error.value = ''
              resolve(data)
            }
          }
        } catch (e) {
          stopPoll()
          running.value = false
          status.value = 'FAILED'
          error.value = e.message
          job.value = {
            status: 'FAILED',
            percent: 100,
            message: '轮询任务状态失败',
            error: e.message,
          }
          reject(e)
        }
      }
      tick()
      timer = setInterval(tick, POLL_MS)
    })
  }

  /** Show progress before an async job id exists (e.g. preflight API checks). */
  function beginPending(message = '准备中…', percent = 5) {
    if (isPolling()) return
    running.value = true
    status.value = 'RUNNING'
    error.value = ''
    job.value = { status: 'PENDING', percent, message }
  }

  function updatePending(message, percent) {
    if (isPolling() || !job.value) return
    job.value = {
      ...job.value,
      status: 'PENDING',
      message,
      percent: percent != null ? percent : job.value.percent,
    }
  }

  function cancelPending() {
    if (isPolling()) return
    reset()
  }

  function failPending(message, detail) {
    if (isPolling()) return
    running.value = false
    status.value = 'FAILED'
    const errMsg = detail || message || '任务失败'
    error.value = errMsg
    job.value = {
      status: 'FAILED',
      percent: 100,
      message: message || '任务失败',
      error: errMsg,
    }
  }

  /** @param {() => Promise<{ jobId: string }>} startFn */
  async function run(startFn) {
    if (isPolling()) return null
    running.value = true
    status.value = 'RUNNING'
    error.value = ''
    job.value = { status: 'PENDING', percent: 0, message: '任务已创建…' }
    try {
      const { jobId } = await startFn()
      return await pollUntilDone(jobId)
    } catch (e) {
      running.value = false
      status.value = 'FAILED'
      const errMsg = error.value || e.message || '任务失败'
      error.value = errMsg
      job.value = {
        status: 'FAILED',
        percent: 100,
        message: '任务失败',
        error: errMsg,
      }
      throw e
    }
  }

  function reset() {
    stopPoll()
    running.value = false
    job.value = null
    status.value = 'IDLE'
    error.value = ''
  }

  return {
    running,
    job,
    status,
    error,
    run,
    reset,
    beginPending,
    updatePending,
    cancelPending,
    failPending,
  }
}
