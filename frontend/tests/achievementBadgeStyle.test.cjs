const assert = require('assert')
const fs = require('fs')
const path = require('path')

const read = relativePath => fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')

const constants = read('../src/constants/badges.js')
const badge = read('../src/components/growth/AchievementBadge.vue')
const modal = read('../src/components/growth/BadgeUnlockModal.vue')
const panel = read('../src/components/growth/GrowthCenterPanel.vue')

for (const iconCode of [
  'first-checkin', 'streak-seven', 'checkin-thirty', 'first-post',
  'post-ten', 'first-activity', 'activity-three', 'place-explorer'
]) {
  assert.match(constants, new RegExp(`['"]${iconCode}['"]\\s*:`), `${iconCode} has a local SVG path entry`)
}
assert.match(constants, /badgeIconPaths\s*=\s*\{/, 'badge icons are a plain map')
assert.doesNotMatch(constants, /https?:|<img|emoji/i, 'badge artwork has no remote or emoji assets')

assert.match(badge, /viewBox="0 0 64 64"/, 'artwork uses the approved viewBox')
assert.match(badge, /50% 0,\s*94% 18%,\s*86% 72%,\s*50% 100%,\s*14% 72%,\s*6% 18%/, 'badge uses the irregular shield polygon')
for (const [rarity, color] of Object.entries({ COMMON: '#cbd5e1', RARE: '#60a5fa', EPIC: '#c084fc', LEGENDARY: '#fbbf24' })) {
  assert.match(badge, new RegExp(`${rarity}[\\s\\S]*${color}`, 'i'), `${rarity} uses ${color}`)
}
assert.match(badge, /普通|稀有|史诗|传说/, 'rarity has the approved Chinese labels')
assert.doesNotMatch(badge, /传奇/, 'legendary does not use the unapproved label')
assert.match(badge, /badgeIconPaths\[.*iconCode/, 'artwork resolves backend iconCode through local paths')
assert.match(badge, /fallback/i, 'unknown icons have a hand-drawn fallback')
assert.match(badge, /<title>/, 'badge SVG exposes an accessible title')
assert.match(badge, /locked/, 'locked artwork has a distinct state')
assert.match(badge, /grayscale\(1\)/, 'locked artwork is grayscale')

assert.match(modal, /<Teleport\s+to="body"/, 'unlock modal teleports above the page')
assert.match(modal, /role="dialog"/, 'unlock modal exposes dialog role')
assert.match(modal, /aria-modal="true"/, 'unlock modal is marked modal')
assert.match(modal, /:aria-labelledby=/, 'unlock modal has a labelled title')
assert.match(modal, /aria-label="关闭/, 'unlock modal close control has a label')
assert.match(modal, /Escape/, 'Escape closes the unlock modal')
assert.match(modal, /Shift|shiftKey/, 'focus trap handles reverse tabbing')
assert.match(modal, /returnFocusTo/, 'modal accepts an explicit return-focus target')
assert.match(modal, /returnFocusTo[^\n]*isConnected|isConnected[^\n]*returnFocusTo/, 'modal prefers the connected explicit focus target')
assert.match(modal, /document\.body\.style\.overflow/, 'body scroll handling is explicit')
assert.match(modal, /@media\s*\(prefers-reduced-motion:\s*reduce\)/, 'reduced motion is honored')

assert.match(panel, /import\s+AchievementBadge\s+from/, 'growth center imports badge artwork')
assert.match(panel, /import\s+BadgeUnlockModal\s+from/, 'growth center imports unlock modal')
assert.match(panel, /<AchievementBadge/, 'badge wall renders the artwork component')
assert.match(panel, /unlockQueue/, 'growth center maintains an unlock queue')
assert.match(panel, /currentUnlock/, 'growth center presents one current unlock')
assert.match(panel, /findNewlyUnlocked/, 'growth center computes unlock diffs')
assert.match(panel, /<BadgeUnlockModal/, 'growth center presents the unlock modal')
assert.match(panel, /event\?\.currentTarget|event\.currentTarget/, 'check-in captures the actual trigger element')
assert.match(panel, /:return-focus-to="checkinTrigger"/, 'check-in trigger is passed explicitly to the modal')
assert.match(panel, /:key="currentUnlock\.code"/, 'each queued badge mounts a freshly focused dialog')
assert.match(panel, /checkinTrigger\.value\s*=\s*null/, 'retained trigger references are cleared')
assert.doesNotMatch(panel, /onMounted\([\s\S]{0,160}findNewlyUnlocked/, 'initial load does not trigger unlock presentation')

console.log('achievement badge style and source contracts passed')
