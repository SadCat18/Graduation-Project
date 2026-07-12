const assert = require('assert')
const fs = require('fs')
const path = require('path')

const apiSource = fs.readFileSync(path.resolve(__dirname, '../src/api/index.js'), 'utf8')
const growthSource = fs.readFileSync(path.resolve(__dirname, '../src/components/growth/GrowthCenterPanel.vue'), 'utf8')
const adminSource = fs.readFileSync(path.resolve(__dirname, '../src/views/AdminPage.vue'), 'utf8')
const profileSource = fs.readFileSync(path.resolve(__dirname, '../src/views/ProfilePage.vue'), 'utf8')
const homeSource = fs.readFileSync(path.resolve(__dirname, '../src/views/HomePage.vue'), 'utf8')
const skateSource = fs.readFileSync(path.resolve(__dirname, '../src/views/SkateProfessionalPage.vue'), 'utf8')
const publishSource = fs.readFileSync(path.resolve(__dirname, '../src/views/PostPublishPage.vue'), 'utf8')
const detailSource = fs.readFileSync(path.resolve(__dirname, '../src/views/PostDetailPage.vue'), 'utf8')

assert.match(apiSource, /submitPlace:\s*\(data\)\s*=>\s*http\.post\('\/api\/user\/places'/, 'users should be able to submit a new place contribution')
assert.match(apiSource, /adminPendingPlaces:\s*\(\)\s*=>\s*http\.get\('\/api\/admin\/places\/pending'/, 'admins should have a pending place queue api')
assert.match(apiSource, /adminReviewPlace:\s*\(id,\s*data\)\s*=>\s*http\.put\(`\/api\/admin\/places\/\$\{id\}\/review`/, 'admins should be able to verify contributed places')
assert.match(apiSource, /adminReports:\s*\(params\)\s*=>\s*http\.get\('\/api\/admin\/reports',\s*\{\s*params\s*\}\)/, 'admin report queue should support filters')
assert.match(apiSource, /publicSearch:\s*\(params\)\s*=>\s*http\.get\('\/api\/public\/search'/, 'public unified search should be exposed')

for (const type of ['training', 'contribution', 'activity', 'creation']) {
  assert.match(growthSource, new RegExp(`key:\\s*'${type}'`), `growth center should expose ${type} leaderboard`)
}

assert.match(adminSource, /pendingPlaces/, 'admin page should keep pending place contributions in review state')
assert.match(adminSource, /type:\s*'placeContribution'/, 'admin review center should render place contributions as review cards')
assert.match(adminSource, /reviewPlaceContribution/, 'admin page should call a dedicated place verification action')
assert.match(adminSource, /reportFilters/, 'admin report queue should keep filter state')
assert.match(adminSource, /false_report/, 'admin report queue should support false report status')

assert.match(profileSource, /REVIEWABLE/, 'profile activity center should keep a reviewable post-event lane')
assert.match(homeSource, /runUnifiedSearch/, 'home page should provide unified search')
assert.match(skateSource, /learningStages/, 'skate page should render a staged learning path')
assert.match(skateSource, /askCoachForStage/, 'learning path should link a stage to AI Coach')
assert.match(publishSource, /publishWarnings/, 'post publish should show pre-publish warnings')
assert.match(detailSource, /commentWarnings/, 'comment form should show pre-publish warnings')
