/** YYYY-MM-DD for Asia/Shanghai (matches backend collect day bounds). */
export function todayInShanghai() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
}
