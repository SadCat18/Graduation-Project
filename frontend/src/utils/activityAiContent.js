function cleanText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function cleanList(value) {
  return Array.isArray(value) ? value.map(cleanText).filter(Boolean) : []
}

function pushTextSection(sections, title, text) {
  if (text) {
    sections.push(`${title}\n${text}`)
  }
}

function pushListSection(sections, title, items) {
  if (items.length) {
    sections.push(`${title}\n${items.map(item => `- ${item}`).join('\n')}`)
  }
}

export function buildActivityAiFields(aiResult) {
  if (!aiResult) {
    return { activityDesc: '', content: '' }
  }

  const sections = []
  const activityDesc = cleanText(aiResult.description)
  const suitableFor = cleanText(aiResult.suitableFor)
  const highlights = cleanList(aiResult.highlights)
  const tips = cleanList(aiResult.tips)
  const riskTips = cleanList(aiResult.riskTips)

  pushTextSection(sections, '适合人群', suitableFor)
  pushListSection(sections, '活动亮点', highlights)
  pushListSection(sections, '注意事项', tips)
  pushListSection(sections, '风险提示', riskTips)

  return {
    activityDesc,
    content: sections.join('\n\n')
  }
}

export function resolveActivitySubmitText(form) {
  const activityDesc = cleanText(form?.activityDesc)
  const content = cleanText(form?.content) || activityDesc

  return {
    activityDesc: activityDesc || content,
    content
  }
}
