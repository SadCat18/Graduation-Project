const assert = require('assert')
const fs = require('fs')
const path = require('path')

const sourcePath = path.resolve(__dirname, '../src/utils/growthCenter.js')
assert.ok(fs.existsSync(sourcePath), 'growth center logic module exists')

const source = fs.readFileSync(sourcePath, 'utf8').replace(/export function /g, 'function ')
const { calculateLevelProgress, createLatestRequestGate, findNewlyUnlocked, shanghaiDateKey, millisecondsUntilNextShanghaiMidnight } = new Function(
  `${source}; return { calculateLevelProgress, createLatestRequestGate, findNewlyUnlocked, shanghaiDateKey, millisecondsUntilNextShanghaiMidnight };`
)()

assert.strictEqual(shanghaiDateKey(new Date('2026-06-18T15:59:59.999Z')), '2026-06-18', 'Shanghai date stays on the old day before midnight')
assert.strictEqual(shanghaiDateKey(new Date('2026-06-18T16:00:00.000Z')), '2026-06-19', 'Shanghai date advances at midnight')
assert.strictEqual(millisecondsUntilNextShanghaiMidnight(new Date('2026-06-18T15:59:59.500Z')), 500, 'midnight delay reaches the exact next boundary')
assert.strictEqual(millisecondsUntilNextShanghaiMidnight(new Date('2026-06-18T16:00:00.000Z')), 86400000, 'midnight schedules the following day')

assert.strictEqual(
  calculateLevelProgress({ level: 2, exp: 150, nextLevelNeedExp: 200 }),
  50,
  'level 2 experience is measured inside the 100-200 band'
)
assert.strictEqual(calculateLevelProgress({ level: 10, exp: 900, nextLevelNeedExp: 1000 }), 100, 'max level is complete')
assert.strictEqual(calculateLevelProgress({ level: 2, exp: 75, nextLevelNeedExp: 200 }), 0, 'progress clamps below the band')
assert.strictEqual(calculateLevelProgress({ level: 2, exp: 240, nextLevelNeedExp: 200 }), 100, 'progress clamps above the band')

const gate = createLatestRequestGate()
const oldToken = gate.begin()
const latestToken = gate.begin()
assert.strictEqual(gate.isCurrent(oldToken), false, 'an older request token is rejected')
assert.strictEqual(gate.isCurrent(latestToken), true, 'the latest request token is accepted')
gate.invalidate()
assert.strictEqual(gate.isCurrent(latestToken), false, 'invalidation rejects an outstanding request')

const before = [
  { code: 'FIRST_CHECKIN', unlocked: true },
  { code: 'STREAK_7', unlocked: false }
]
const after = [
  { code: 'FIRST_CHECKIN', unlocked: true },
  { code: 'STREAK_7', unlocked: true },
  { code: 'CHECKIN_30', unlocked: true },
  { code: 'POST_10', unlocked: false }
]
assert.deepStrictEqual(
  findNewlyUnlocked(before, after).map(item => item.code),
  ['STREAK_7', 'CHECKIN_30'],
  'new unlocks preserve the catalog order from the refreshed response'
)
assert.deepStrictEqual(findNewlyUnlocked(after, after), [], 'already unlocked badges are not duplicated')
assert.deepStrictEqual(
  findNewlyUnlocked([], [
    { code: 'FIRST_POST', unlocked: true },
    { code: 'FIRST_POST', unlocked: true },
    { code: 'POST_10', unlocked: true }
  ]).map(item => item.code),
  ['FIRST_POST', 'POST_10'],
  'duplicate refreshed entries enqueue each badge only once'
)
assert.deepStrictEqual(findNewlyUnlocked(null, null), [], 'null badge arrays are tolerated')
assert.deepStrictEqual(
  findNewlyUnlocked(null, [{ code: 'FIRST_POST', unlocked: true }, null, { code: 'POST_10', unlocked: false }]).map(item => item.code),
  ['FIRST_POST'],
  'only unlocked entries from the refreshed catalog are returned'
)

console.log('growth center executable logic tests passed')
