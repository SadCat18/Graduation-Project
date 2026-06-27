const assert = require('assert')
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const apiSource = fs.readFileSync(path.join(root, 'src/api/index.js'), 'utf8')
const routerSource = fs.readFileSync(path.join(root, 'src/router/index.js'), 'utf8')
const appSource = fs.readFileSync(path.join(root, 'src/App.vue'), 'utf8')
const pagePath = path.join(root, 'src/views/SkateProfessionalPage.vue')

assert.ok(fs.existsSync(pagePath), 'skate professional page exists')
assert.ok(routerSource.includes("path: '/skate'"), 'router exposes /skate')
assert.ok(appSource.includes("to: '/skate'"), 'top navigation links to skate module')

const requiredApiMethods = [
  'skateTricks',
  'skateTrickDetail',
  'submitTrickCheckin',
  'skateTrainingPlans',
  'joinTrainingPlan',
  'completeTrainingTask',
  'skateEvents',
  'signupSkateEvent',
  'skateEquipments',
  'favoriteEquipment'
]

for (const method of requiredApiMethods) {
  assert.ok(apiSource.includes(`${method}:`), `api exposes ${method}`)
}

const pageSource = fs.existsSync(pagePath) ? fs.readFileSync(pagePath, 'utf8') : ''
for (const label of ['动作打卡', '训练计划', '赛事活动', '装备库']) {
  assert.ok(pageSource.includes(label), `page contains ${label}`)
}

console.log('skate professional module frontend contract passed')
