function cleanText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function toTimestamp(value) {
  const timestamp = new Date(cleanText(value).replace(' ', 'T')).getTime()
  return Number.isFinite(timestamp) ? timestamp : 0
}

function toNumber(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

function activityMatchesPlace(activity, place) {
  if (!activity || !place) return false
  if (activity.placeId && place.placeId && Number(activity.placeId) === Number(place.placeId)) {
    return true
  }

  const placeName = cleanText(place.name)
  const activityPlace = cleanText(activity.place)
  const activityAddress = cleanText(activity.address)
  return Boolean(placeName && (activityPlace.includes(placeName) || activityAddress.includes(placeName)))
}

function buildPlaceTags(place, relatedCount) {
  const score = toNumber(place.score)
  const reviewCount = toNumber(place.reviewCount)
  const source = [
    cleanText(place.name),
    cleanText(place.address),
    cleanText(place.intro)
  ].join(' ')
  const tags = []

  if (relatedCount > 0) tags.push('有活动')
  if (score >= 4.5) tags.push('高评分')
  if (reviewCount >= 10) tags.push('人气场地')
  if (/新手|入门|平地|空旷/.test(source)) tags.push('新手友好')
  if (/街式|台阶|栏杆|道具|ledge|rail/i.test(source)) tags.push('街式')
  if (/碗池|碗|pool|bowl/i.test(source)) tags.push('碗池')
  if (/夜|灯|晚上|夜滑/.test(source)) tags.push('夜滑')
  if (!tags.length) tags.push('可探索')

  return tags.slice(0, 4)
}

function buildRecommendReason(place, relatedCount, latestActivity) {
  const score = toNumber(place.score)
  const reviewCount = toNumber(place.reviewCount)

  if (relatedCount > 0) {
    return `有 ${relatedCount} 个近期活动`
  }
  if (score >= 4.5) {
    return `评分 ${score.toFixed(1)}`
  }
  if (reviewCount > 0) {
    return `${reviewCount} 条场地评价`
  }
  if (latestActivity?.title) {
    return '最近有约板动态'
  }
  return '值得探索的新场地'
}

export function buildActivityPlaceRecommendations(places, activities, limit = 8) {
  const placeList = Array.isArray(places) ? places : []
  const activityList = Array.isArray(activities) ? activities : []

  const enriched = placeList.map((place) => {
    const relatedActivities = activityList
      .filter(activity => activityMatchesPlace(activity, place))
      .sort((a, b) => toTimestamp(b.activityTime) - toTimestamp(a.activityTime))

    return {
      ...place,
      relatedActivities,
      relatedCount: relatedActivities.length,
      latestActivity: relatedActivities[0] || null,
      hasActivityLink: relatedActivities.length > 0,
      placeTags: buildPlaceTags(place, relatedActivities.length),
      recommendReason: buildRecommendReason(place, relatedActivities.length, relatedActivities[0])
    }
  })

  return enriched
    .sort((a, b) => {
      if (Number(b.hasActivityLink) !== Number(a.hasActivityLink)) {
        return Number(b.hasActivityLink) - Number(a.hasActivityLink)
      }
      if (b.relatedCount !== a.relatedCount) return b.relatedCount - a.relatedCount
      if (toNumber(b.score) !== toNumber(a.score)) return toNumber(b.score) - toNumber(a.score)
      if (toNumber(b.reviewCount) !== toNumber(a.reviewCount)) return toNumber(b.reviewCount) - toNumber(a.reviewCount)
      return toTimestamp(b.latestActivity?.activityTime) - toTimestamp(a.latestActivity?.activityTime)
    })
    .slice(0, limit)
}
