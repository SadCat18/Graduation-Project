// Missing/invalid summaries remain unavailable, never a synthetic zero or legacy fallback.
export function localReviewSummary(place) {
  const s = place?.localReviewSummary
  if (!s || s.source !== 'LOCAL_REVIEWS' || !Number.isSafeInteger(s.count) || s.count < 0) return null
  if (s.count === 0) return s.score === null ? s : null
  return typeof s.score === 'number' && Number.isFinite(s.score) && s.score >= 0 && s.score <= 5 ? s : null
}
export function reviewScoreText(place) {
  const s = localReviewSummary(place)
  return !s ? '评分资料暂不可用' : s.count === 0 ? '暂无本站评分' : s.score.toFixed(1)
}
export function reviewCountText(place) {
  const s = localReviewSummary(place)
  return s ? `${s.count} 条本站评价` : '评价资料暂不可用'
}
