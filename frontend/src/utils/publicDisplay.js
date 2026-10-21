const placeTags = {
  flat: '平地',
  'Check lighting before night sessions.': '夜滑前检查照明条件。',
  'Wear helmet and wrist guards before obstacle practice.': '练习障碍前佩戴头盔和护腕。',
  'Check ground, weather, and pedestrian flow before skating.': '滑行前检查路面、天气和人流。',
  'open-space': '开放平地', ledge: '台沿', rail: '栏杆', stairs: '台阶', bowl: '碗池',
  'mini-ramp': '小U池', ramp: '坡道', flatground: '平地', 'flat-ground': '平地',
  'wear-helmet': '佩戴头盔', 'wear-protection': '佩戴护具', 'watch-pedestrians': '注意行人',
  'check-surface': '检查路面', 'avoid-rain': '雨天慎滑', 'night-lighting': '留意夜间照明'
}
export const placeTagLabel = key => placeTags[key] || key
export function sourceLinkLabel(value) {
  try {
    const url = new URL(value)
    if (!['http:', 'https:'].includes(url.protocol)) return ''
    if (url.pathname === '/' && !url.search) return '站点首页'
    if (['taobao.com', 'tmall.com', 'jd.com'].some(host => url.hostname === host || url.hostname.endsWith(`.${host}`))) {
      return /(?:item|detail|product)\b/i.test(url.hostname + url.pathname) || url.searchParams.has('id') ? '商品来源' : '参考平台'
    }
    return '商品来源'
  } catch { return '' }
}
export const absoluteDate = value => value ? String(value).replace('T', ' ').slice(0, 10) : '发布日期未知'
export function bulletinTiming(item, now = Date.now()) {
  const time = new Date(item?.createTime).getTime()
  const relative = /本周|这周|本月|明天|后天|今天|今晚/.test(`${item?.title || ''} ${item?.content || ''}`)
  return relative && Number.isFinite(time) && now - time > 7 * 86400000 ? '历史活动通知 · 时效待确认' : '时效待确认'
}
