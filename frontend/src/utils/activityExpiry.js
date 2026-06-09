import { ACTIVITY_STATUS, normalizeActivityStatus } from '../constants/activity'

export const ACTIVITY_EXPIRY_FILTER = {
  ACTIVE: 'active',
  EXPIRED: 'expired'
}

function parseActivityTime(value) {
  if (!value) return Number.POSITIVE_INFINITY
  const normalized = String(value).trim().replace(' ', 'T')
  const timestamp = new Date(normalized).getTime()
  return Number.isFinite(timestamp) ? timestamp : Number.POSITIVE_INFINITY
}

export function isActivityExpired(item, now = Date.now()) {
  return parseActivityTime(item?.activityTime) < now
}

export function filterActivitiesByExpiry(items, expiryFilter = ACTIVITY_EXPIRY_FILTER.ACTIVE, now = Date.now()) {
  return (items || []).filter((item) => {
    const status = normalizeActivityStatus(item.activityStatus ?? item.status)
    if (status === ACTIVITY_STATUS.CANCELED) return false
    const expired = isActivityExpired(item, now)
    return expiryFilter === ACTIVITY_EXPIRY_FILTER.EXPIRED ? expired : !expired
  })
}

export function canSignActivity(item, now = Date.now()) {
  const status = normalizeActivityStatus(item?.activityStatus ?? item?.status)
  return Boolean(item) && !item.signed && !isActivityExpired(item, now) && status === ACTIVITY_STATUS.SIGNUP_OPEN
}

export function canCancelSignActivity(item, now = Date.now()) {
  if (!item?.signed || isActivityExpired(item, now)) return false
  const status = normalizeActivityStatus(item.activityStatus ?? item.status)
  return status !== ACTIVITY_STATUS.CANCELED && status !== ACTIVITY_STATUS.ENDED
}

export function getActivityStatusLabel(item, now = Date.now()) {
  if (isActivityExpired(item, now)) {
    return '已过期'
  }
  return null
}
