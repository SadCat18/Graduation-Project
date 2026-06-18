const assert = require('assert')
const fs = require('fs')
const path = require('path')

const read = relativePath => fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
const api = read('../src/api/index.js')
const profile = read('../src/views/ProfilePage.vue')
const componentPath = path.resolve(__dirname, '../src/components/growth/GrowthCenterPanel.vue')

assert.ok(fs.existsSync(componentPath), 'growth center component exists')
const growth = fs.existsSync(componentPath) ? fs.readFileSync(componentPath, 'utf8') : ''

const compact = source => source.replace(/\s+/g, '')
const compactApi = compact(api)
const compactGrowth = compact(growth)

assert.ok(compactApi.includes("dailyCheckin:()=>http.post('/api/user/checkin')"), 'dailyCheckin uses the exact endpoint')
assert.ok(compactApi.includes("checkinStatus:()=>http.get('/api/user/checkin/status')"), 'checkinStatus uses the exact endpoint')
assert.ok(compactApi.includes("checkinCalendar:(month)=>http.get('/api/user/checkin/calendar',{params:{month}})"), 'checkinCalendar sends only month')
assert.ok(compactApi.includes("growthLeaderboard:(params)=>http.get('/api/user/leaderboard',{params})"), 'growthLeaderboard forwards params')
assert.ok(compactApi.includes("badges:()=>http.get('/api/user/badges')"), 'badges uses the exact endpoint')

assert.match(profile, /import\s+GrowthCenterPanel\s+from\s+['"]\.\.\/components\/growth\/GrowthCenterPanel\.vue['"]/, 'profile imports panel')
assert.match(profile, />\s*成长中心\s*</, 'profile exposes growth tab')
assert.match(profile, /profileTab\s*===\s*['"]growth['"]/, 'profile mounts panel only on growth tab')
assert.match(profile, /<GrowthCenterPanel[^>]*:profile="profile"[^>]*@experience-changed="loadProfile"/s, 'profile passes profile and reload handler')
assert.ok(compact(profile).includes('profile.nextLevelNeedExp=Number(user?.nextLevelNeedExp??100)'), 'profile preserves a zero next-level sentinel')
assert.ok(compact(profile).includes('profile.remainToNextLevel=Number(user?.remainToNextLevel??100)'), 'profile preserves a zero remaining experience value')

for (const state of ['summaryLoading', 'summaryError', 'leaderboardLoading', 'leaderboardError', 'checkinPending', 'checkinError']) {
  assert.match(growth, new RegExp(`const\\s+${state}\\s*=\\s*ref\\(`), `${state} has an independent ref`)
}
assert.match(growth, /if\s*\(checkinPending\.value/, 'check-in has a pending guard')
assert.match(growth, /:disabled="[^\"]*checkinPending/, 'check-in control disables while pending')
assert.match(compactGrowth, /Promise\.all\(\[api\.checkinStatus\(\),api\.checkinCalendar\(currentMonth\.value\),api\.badges\(\)\]\)/, 'summary resources load together')
assert.match(compactGrowth, /api\.growthLeaderboard\(\{type:boardType\.value,page:page\.value,size:size\}\)/, 'leaderboard sends type, page and size')
assert.match(growth, /new\s+Date\([^)]*year[^)]*month[^)]*1[^)]*\)\.getDay\(\)/, 'calendar computes Sunday-start offset')
assert.match(growth, /checkedDates/, 'calendar is driven by checked dates')
assert.match(growth, /role="progressbar"/, 'experience progress is accessible')
assert.match(growth, /aria-valuenow/, 'experience progress exposes its value')
assert.match(compactGrowth, /Number\(props\.profile\.level\)>=10\|\|nextLevelNeedExp\.value<=0/, 'level 10 or a zero threshold is treated as max level')
assert.match(compactGrowth, /if\(isMaxLevel\.value\)return100/, 'max level progress is complete')
assert.match(growth, /最高等级/, 'max level has a clear UI label')
assert.match(growth, /重试/, 'failed sections expose retry actions')
assert.match(growth, /暂无[^<]*(排行|徽章)/, 'stable empty state is rendered')
assert.match(growth, /baseExp|baseExperience/, 'check-in success reports base experience')
assert.match(growth, /bonusExp|bonusExperience/, 'check-in success reports bonus experience')
assert.match(growth, /emit\(['"]experience-changed['"]\)/, 'successful check-in refreshes profile experience')

console.log('growth center source contracts passed')
