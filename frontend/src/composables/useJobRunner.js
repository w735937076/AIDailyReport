import { onUnmounted, ref } from 'vue'
import { api } from '../api'

const POLL_MS = 500
const TERMINAL = new Set(['COMPLETED', 'FAILED'])
const PERSISTED_SCOPE_IDS = new Set(['performance', 'report', 'collect', 'interview'])

/** @type {Map<string, object>} */
const persistedScopes = new Map()

function getPersistedScope(id) {
  if (!persistedScopes.has(id)) {
    const scope = {
      running: ref(false),
      job: ref(null),
      status: ref('IDLE'),
      error: ref(''),
      preflight: ref(false),
      activeJobId: null,
      timer: null,
      pendingCompletion: null,
      pollPromise: null,
    }
    if (id === 'performance' || id === 'interview') {
      scope.contextYearMonth = ref('')
    }
    persistedScopes.set(id, scope)
  }
  return persistedScopes.get(id)
}

function stopPollScope(scope) {
  if (scope.timer) {
    clearInterval(scope.timer)
    scope.timer = null
  }
}

function isPollingScope(scope) {
  return scope.timer != null
}

function pollUntilDoneScoped(scope, jobId) {
  if (scope.pollPromise) {
    return scope.pollPromise
  }
  scope.activeJobId = jobId
  scope.pollPromise = new Promise((resolve, reject) => {
    const tick = async () => {
      try {
        const data = await api.jobs.get(jobId)
        scope.job.value = data
        scope.status.value = data.status
        if (TERMINAL.has(data.status)) {
          stopPollScope(scope)
          scope.running.value = false
          scope.preflight.value = false
          scope.activeJobId = null
          scope.pollPromise = null
          if (data.status === 'FAILED') {
            const msg = data.error || data.message || '任务失败'
            scope.error.value = msg
            reject(new Error(msg))
          } else {
            scope.error.value = ''
            scope.pendingCompletion = data
            resolve(data)
          }
        }
      } catch (e) {
        stopPollScope(scope)
        scope.running.value = false
        scope.preflight.value = false
        scope.activeJobId = null
        scope.pollPromise = null
        scope.status.value = 'FAILED'
        scope.error.value = e.message
        scope.job.value = {
          status: 'FAILED',
          percent: 100,
          message: '轮询任务状态失败',
          error: e.message,
        }
        reject(e)
      }
    }
    tick()
    scope.timer = setInterval(tick, POLL_MS)
  })
  return scope.pollPromise
}

function createPersistedRunner(scopeId) {
  const scope = getPersistedScope(scopeId)

  function beginPending(message = '准备中…', percent = 5) {
    if (isPollingScope(scope)) return
    scope.running.value = true
    scope.status.value = 'RUNNING'
    scope.error.value = ''
    scope.preflight.value = true
    scope.job.value = { status: 'PENDING', percent, message }
  }

  function updatePending(message, percent) {
    if (isPollingScope(scope) || !scope.job.value) return
    scope.job.value = {
      ...scope.job.value,
      status: 'PENDING',
      message,
      percent: percent != null ? percent : scope.job.value.percent,
    }
  }

  function cancelPending() {
    if (isPollingScope(scope)) return
    reset()
  }

  function failPending(message, detail) {
    if (isPollingScope(scope)) return
    scope.running.value = false
    scope.preflight.value = false
    scope.status.value = 'FAILED'
    const errMsg = detail || message || '任务失败'
    scope.error.value = errMsg
    scope.job.value = {
      status: 'FAILED',
      percent: 100,
      message: message || '任务失败',
      error: errMsg,
    }
  }

  async function run(startFn) {
    if (isPollingScope(scope)) {
      return scope.pollPromise
    }
    scope.running.value = true
    scope.status.value = 'RUNNING'
    scope.error.value = ''
    scope.preflight.value = false
    scope.job.value = { status: 'PENDING', percent: 0, message: '任务已创建…' }
    try {
      const { jobId } = await startFn()
      return await pollUntilDoneScoped(scope, jobId)
    } catch (e) {
      scope.running.value = false
      scope.preflight.value = false
      scope.status.value = 'FAILED'
      const errMsg = scope.error.value || e.message || '任务失败'
      scope.error.value = errMsg
      scope.job.value = {
        status: 'FAILED',
        percent: 100,
        message: '任务失败',
        error: errMsg,
      }
      throw e
    }
  }

  function reset() {
    stopPollScope(scope)
    scope.running.value = false
    scope.preflight.value = false
    scope.job.value = null
    scope.status.value = 'IDLE'
    scope.error.value = ''
    scope.activeJobId = null
    scope.pollPromise = null
    scope.pendingCompletion = null
  }

  function resumePolling() {
    if (scope.activeJobId && !scope.timer) {
      return pollUntilDoneScoped(scope, scope.activeJobId)
    }
    return scope.pollPromise
  }

  function takePendingCompletion() {
    const data = scope.pendingCompletion
    scope.pendingCompletion = null
    return data
  }

  function isActive() {
    return (
      scope.preflight.value ||
      scope.running.value ||
      scope.status.value === 'RUNNING' ||
      (scope.job.value && !TERMINAL.has(scope.job.value.status))
    )
  }

  const runner = {
    running: scope.running,
    job: scope.job,
    status: scope.status,
    error: scope.error,
    preflight: scope.preflight,
    run,
    reset,
    beginPending,
    updatePending,
    cancelPending,
    failPending,
    resumePolling,
    takePendingCompletion,
    isActive,
  }
  if (scope.contextYearMonth) {
    runner.contextYearMonth = scope.contextYearMonth
  }
  return runner
}

/** Ephemeral runner: per-page instance, stops polling on unmount. */
function createEphemeralRunner() {
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

export function useJobRunner(options) {
  const scope = options?.scope
  if (scope && PERSISTED_SCOPE_IDS.has(scope)) {
    return createPersistedRunner(scope)
  }
  return createEphemeralRunner()
}

export function usePerformanceJob() {
  return createPersistedRunner('performance')
}

export function useReportJob() {
  return createPersistedRunner('report')
}

export function useCollectJob() {
  return createPersistedRunner('collect')
}

export function useInterviewJob() {
  return createPersistedRunner('interview')
}
