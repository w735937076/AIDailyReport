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
          reject(e)
        }
      }
      tick()
      timer = setInterval(tick, POLL_MS)
    })
  }

  /** @param {() => Promise<{ jobId: string }>} startFn */
  async function run(startFn) {
    if (running.value) return null
    running.value = true
    status.value = 'RUNNING'
    error.value = ''
    job.value = { status: 'PENDING', percent: 0, message: '任务已创建…' }
    try {
      const { jobId } = await startFn()
      return await pollUntilDone(jobId)
    } catch (e) {
      running.value = false
      if (status.value !== 'FAILED') status.value = 'FAILED'
      if (!error.value) error.value = e.message
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

  return { running, job, status, error, run, reset }
}
