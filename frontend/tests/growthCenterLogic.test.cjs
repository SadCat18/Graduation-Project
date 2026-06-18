const assert = require('assert')
const fs = require('fs')
const path = require('path')

const sourcePath = path.resolve(__dirname, '../src/utils/growthCenter.js')
assert.ok(fs.existsSync(sourcePath), 'growth center logic module exists')

const source = fs.readFileSync(sourcePath, 'utf8').replace(/export function /g, 'function ')
const { calculateLevelProgress, createLatestRequestGate } = new Function(
  `${source}; return { calculateLevelProgress, createLatestRequestGate };`
)()

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

console.log('growth center executable logic tests passed')
