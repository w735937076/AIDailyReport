/** Lines that start a new section (full-width 【…】 or 明日计划 blocks). */
const BRACKET_HEADER = /^【[^】]+】\s*$/
const TOMORROW_HEADER = /^(【明日计划】|明日计划)\s*$/

export function isSectionHeaderLine(line) {
  const t = (line || '').trim()
  return BRACKET_HEADER.test(t) || TOMORROW_HEADER.test(t)
}

function trimTrailingBlankLines(text) {
  const lines = text.split(/\r?\n/)
  let end = lines.length
  while (end > 0 && lines[end - 1].trim() === '') end -= 1
  return lines.slice(0, end).join('\n')
}

/**
 * Split report plain text into titled sections. Body excludes the title line.
 * @param {string} text
 * @returns {{ id: string, title: string, body: string }[]}
 */
export function parseReportSections(text) {
  if (!text || !String(text).trim()) return []

  const lines = String(text).split(/\r?\n/)
  const sections = []
  let currentTitle = null
  let bodyLines = []
  let preamble = []

  const pushSection = (title, bodyRaw) => {
    const body = trimTrailingBlankLines(bodyRaw)
    if (!title && !body.trim()) return
    sections.push({
      id: `${title || '_'}_${sections.length}`,
      title: title || '',
      body,
    })
  }

  for (const line of lines) {
    const trimmed = line.trim()
    if (isSectionHeaderLine(trimmed)) {
      if (currentTitle === null && preamble.length) {
        pushSection('', preamble.join('\n'))
        preamble = []
      } else if (currentTitle !== null) {
        pushSection(currentTitle, bodyLines.join('\n'))
        bodyLines = []
      }
      currentTitle = trimmed
    } else if (currentTitle === null) {
      preamble.push(line)
    } else {
      bodyLines.push(line)
    }
  }

  if (currentTitle !== null) {
    pushSection(currentTitle, bodyLines.join('\n'))
  } else if (preamble.length) {
    pushSection('', preamble.join('\n'))
  }

  if (!sections.length) {
    return [{ id: 'full', title: '', body: trimTrailingBlankLines(text) }]
  }

  return sections
}
