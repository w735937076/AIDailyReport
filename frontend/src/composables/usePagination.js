import { ref } from 'vue'

export function usePagination(fetchPage, initialSize = 10) {
  const page = ref(1)
  const size = ref(initialSize)
  const total = ref(0)
  const items = ref([])
  const loading = ref(false)
  const error = ref('')

  async function load() {
    loading.value = true
    error.value = ''
    try {
      const data = await fetchPage({ page: page.value, size: size.value })
      items.value = data.items || []
      total.value = data.total ?? 0
      page.value = data.page ?? page.value
      size.value = data.size ?? size.value
    } catch (e) {
      error.value = e.message || '加载失败'
      items.value = []
    } finally {
      loading.value = false
    }
  }

  function goToPage(p) {
    page.value = p
    return load()
  }

  function changeSize(s) {
    size.value = s
    page.value = 1
    return load()
  }

  function refresh() {
    return load()
  }

  return { page, size, total, items, loading, error, load, goToPage, changeSize, refresh }
}
