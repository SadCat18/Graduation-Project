const assert = require('assert')
const fs = require('fs')
const path = require('path')

const sourcePath = path.resolve(__dirname, '../src/utils/activityExpiry.js')
let source = fs.readFileSync(sourcePath, 'utf8')
source = source
  .replace(/import \{ ACTIVITY_STATUS, normalizeActivityStatus \} from '..\/constants\/activity'\n/, '')
  .replace(/export const /g, 'const ')
  .replace(/export function /g, 'function ')
  .replace(/export \{[^}]+\}\s*$/m, '')

const activityConstants = {
  ACTIVITY_STATUS: {
    SIGNUP_OPEN: '2',
    FULL: '3',
    CANCELED: '4',
    ENDED: '5'
  },
  normalizeActivityStatus(raw) {
    if (raw === '0' || raw === '1') return raw === '0' ? '2' : '5'
    return raw || '0'
  }
}

const moduleScope = new Function(
  'ACTIVITY_STATUS',
  'normalizeActivityStatus',
  `${source}; return { ACTIVITY_EXPIRY_FILTER, filterActivitiesByExpiry, isActivityExpired, canSignActivity, canCancelSignActivity, getActivityStatusLabel };`
)(activityConstants.ACTIVITY_STATUS, activityConstants.normalizeActivityStatus)

const {
  ACTIVITY_EXPIRY_FILTER,
  filterActivitiesByExpiry,
  isActivityExpired,
  canSignActivity,
  canCancelSignActivity,
  getActivityStatusLabel
} = moduleScope

const now = new Date('2026-06-09T14:00:00').getTime()
const active = { activityId: 1, activityTime: '2026-06-10T10:00:00', activityStatus: '2', signed: false }
const expired = { activityId: 2, activityTime: '2026-06-08T10:00:00', activityStatus: '2', signed: false }
const canceled = { activityId: 3, activityTime: '2026-06-10T10:00:00', activityStatus: '4', signed: false }

assert.strictEqual(isActivityExpired(expired, now), true, 'past activityTime is expired')
assert.strictEqual(isActivityExpired(active, now), false, 'future activityTime is not expired')

assert.deepStrictEqual(
  filterActivitiesByExpiry([active, expired, canceled], ACTIVITY_EXPIRY_FILTER.ACTIVE, now).map(item => item.activityId),
  [1],
  'default active filter hides expired and canceled activities'
)

assert.deepStrictEqual(
  filterActivitiesByExpiry([active, expired, canceled], ACTIVITY_EXPIRY_FILTER.EXPIRED, now).map(item => item.activityId),
  [2],
  'expired filter shows only expired non-canceled activities'
)

assert.strictEqual(canSignActivity(expired, now), false, 'expired activities cannot be signed')
assert.strictEqual(canSignActivity(active, now), true, 'future signup-open activities can be signed')
assert.strictEqual(canCancelSignActivity({ ...expired, signed: true }, now), false, 'expired activities cannot be canceled by participant')
assert.strictEqual(getActivityStatusLabel(expired, now), '已过期', 'expired activity label overrides stale signup-open status')
