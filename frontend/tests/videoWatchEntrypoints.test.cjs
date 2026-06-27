const assert = require('assert')
const fs = require('fs')
const path = require('path')

const coachPath = path.resolve(__dirname, '../src/views/AiCoachPage.vue')
const profilePath = path.resolve(__dirname, '../src/views/ProfilePage.vue')

const coachSource = fs.readFileSync(coachPath, 'utf8')
const profileSource = fs.readFileSync(profilePath, 'utf8')

assert.match(coachSource, /function openVideo\(video\)/, 'AI coach opens a full video object')
assert.match(coachSource, /router\.push\(`\/videos\/\$\{video\.videoId\}`\)/, 'AI coach routes videos into the station watch page')
assert.doesNotMatch(coachSource, /window\.open\(target,\s*'_blank'\)/, 'AI coach no longer opens related videos in a new tab')
assert.match(coachSource, /@click="openVideo\(video\)"/, 'AI coach passes the selected video object to the opener')

assert.match(profileSource, /function goVideoWatch\(videoId\)/, 'profile page exposes a video watch navigation helper')
assert.match(profileSource, /router\.push\(`\/videos\/\$\{videoId\}`\)/, 'profile page routes videos into the station watch page')
assert.match(profileSource, /@click="goVideoWatch\(v\.videoId\)"/, 'profile teaching videos link to the watch page')
