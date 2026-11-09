export function buildReportFullText(content, tomorrowPlan) {
  const parts = []
  if (content && String(content).trim()) parts.push(String(content).trim())
  if (tomorrowPlan && String(tomorrowPlan).trim()) {
    if (parts.length) parts.push('')
    parts.push('【明日计划】')
    parts.push(String(tomorrowPlan).trim())
  }
  return parts.join('\n')
}
