const BASE = ''

function pageQuery(page, size) {
  const p = new URLSearchParams()
  p.set('page', String(page))
  p.set('size', String(size))
  return p.toString()
}

function queryParams(obj) {
  const p = new URLSearchParams()
  Object.entries(obj).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') p.set(k, String(v))
  })
  return p.toString()
}

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options,
  })
  const json = await res.json()
  if (!json.success && json.message) {
    throw new Error(json.message)
  }
  return json.data
}

export const api = {
  health: () => request('/api/health'),
  projects: {
    list: (page = 1, size = 10) => request(`/api/projects?${pageQuery(page, size)}`),
    create: (body) => request('/api/projects', { method: 'POST', body: JSON.stringify(body) }),
    update: (id, body) => request(`/api/projects/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    remove: (id) => request(`/api/projects/${id}`, { method: 'DELETE' }),
  },
  ai: {
    listProviders: (page = 1, size = 10) => request(`/api/ai/providers?${pageQuery(page, size)}`),
    createProvider: (body) => request('/api/ai/providers', { method: 'POST', body: JSON.stringify(body) }),
    updateProvider: (id, body) => request(`/api/ai/providers/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    removeProvider: (id) => request(`/api/ai/providers/${id}`, { method: 'DELETE' }),
    setEnabled: (id, enabled) =>
      request(`/api/ai/providers/${id}/enabled`, { method: 'PATCH', body: JSON.stringify({ enabled }) }),
    getPolicy: () => request('/api/ai/policy'),
    savePolicy: (body) => request('/api/ai/policy', { method: 'PUT', body: JSON.stringify(body) }),
  },
  scheduler: {
    get: () => request('/api/scheduler'),
    save: (body) => request('/api/scheduler', { method: 'PUT', body: JSON.stringify(body) }),
  },
  jobs: {
    get: (jobId) => request(`/api/jobs/${jobId}`),
  },
  workItems: {
    list: (params) => request(`/api/work-items?${queryParams(params)}`),
    get: (id) => request(`/api/work-items/${id}`),
    repos: () => request('/api/work-items/repos'),
  },
  collect: {
    now: () => request('/api/collect/now', { method: 'POST' }),
    status: () => request('/api/collect/status'),
  },
  reports: {
    dailyList: (page = 1, size = 10) => request(`/api/reports/daily?${pageQuery(page, size)}`),
    getDailyById: (id) => request(`/api/reports/daily/id/${id}`),
    getDailyByDate: (date) => request(`/api/reports/daily/${date}`),
    getPeriodById: (id) => request(`/api/reports/period/id/${id}`),
    updateDaily: (id, body) =>
      request(`/api/reports/daily/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    updatePeriod: (id, body) =>
      request(`/api/reports/period/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    periodList: (type, page = 1, size = 10) =>
      request(`/api/reports/period/${type}?${pageQuery(page, size)}`),
    generateDaily: (body) => request('/api/reports/daily/generate', { method: 'POST', body: JSON.stringify(body || {}) }),
    generateWeekly: (body) => request('/api/reports/weekly/generate', { method: 'POST', body: JSON.stringify(body || {}) }),
    generateMonthly: (body) => request('/api/reports/monthly/generate', { method: 'POST', body: JSON.stringify(body || {}) }),
    exportDailyExcel: (date) => request(`/api/reports/daily/${date}/export-excel`, { method: 'POST' }),
    status: () => request('/api/reports/status'),
  },
  downloadUrl: (path) => `/api/export/download?path=${encodeURIComponent(path)}`,
  performance: {
    list: (page = 1, size = 10) => request(`/api/performance?${pageQuery(page, size)}`),
    get: (id) => request(`/api/performance/${id}`),
    exists: (yearMonth) => request(`/api/performance/exists?yearMonth=${encodeURIComponent(yearMonth)}`),
    update: (id, body) =>
      request(`/api/performance/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    generate: (body) => request('/api/performance/generate', { method: 'POST', body: JSON.stringify(body || {}) }),
    getConfig: () => request('/api/performance/config'),
    saveConfig: (body) => request('/api/performance/config', { method: 'PUT', body: JSON.stringify(body) }),
    listLocalFiles: () => request('/api/performance/local-files'),
  },
}

export const UNCHANGED_SECRET = '__UNCHANGED__'
