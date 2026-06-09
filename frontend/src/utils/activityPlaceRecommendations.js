function cleanText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function toTimestamp(value) {
  const timestamp = new Date(cleanText(value).replace(' ', 'T')).getTime()
  return Number.isFinite(timestamp) ? timestamp : 0
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
      hasActivityLink: relatedActivities.length > 0
    }
  })

  const linked = enriched
    .filter(item => item.hasActivityLink)
    .sort((a, b) => {
      if (b.relatedCount !== a.relatedCount) return b.relatedCount - a.relatedCount
      return toTimestamp(b.latestActivity?.activityTime) - toTimestamp(a.latestActivity?.activityTime)
    })

  if (linked.length) {
    return linked.slice(0, limit)
  }

  return enriched
    .sort((a, b) => Number(b.score || 0) - Number(a.score || 0))
    .slice(0, limit)
}
