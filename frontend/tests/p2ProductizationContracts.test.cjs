const assert = require('assert')
const fs = require('fs')
const path = require('path')

const read = relative => fs.readFileSync(path.resolve(__dirname, relative), 'utf8')
const videoWatch = read('../src/views/VideoWatchPage.vue')
const activityDetail = read('../src/views/ActivityDetailPage.vue')
const profile = read('../src/views/ProfilePage.vue')

assert.match(videoWatch, /creatorSeries/, 'video watch page should render creator series metadata')
assert.match(videoWatch, /trickTags/, 'video watch page should render server-side trick tags')
assert.match(videoWatch, /qualityScore/, 'video watch page should render creator quality score')

assert.match(activityDetail, /safetyCompanion/, 'activity detail should render safety companion data')
assert.match(activityDetail, /shareLocationHint/, 'activity safety companion should mention location sharing')
assert.match(activityDetail, /arrivalConfirmationHint/, 'activity safety companion should mention arrival confirmation')

assert.match(profile, /videoForm\s*=\s*reactive\([^)]*creatorSeries/s, 'creator upload form should include series metadata')
assert.match(profile, /videoForm\s*=\s*reactive\([^)]*trickTags/s, 'creator upload form should include trick tags metadata')
assert.match(profile, /creatorSeries:\s*videoForm\.creatorSeries\.trim\(\)/, 'video create payload should submit creator series')
assert.match(profile, /trickTags:\s*videoForm\.trickTags\.trim\(\)/, 'video create payload should submit trick tags')

console.log('p2 productization frontend contracts passed')
