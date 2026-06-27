const assert = require('assert')
const fs = require('fs')
const path = require('path')

const sourcePath = path.resolve(__dirname, '../src/App.vue')
const source = fs.readFileSync(sourcePath, 'utf8')

assert.match(source, /浙ICP备2026040697号-1/, 'shows the ICP filing number')
assert.match(source, /href="https:\/\/beian\.miit\.gov\.cn\/"/, 'links ICP filing number to MIIT')
assert.match(source, /浙公网安备33080202000739号/, 'shows the public security filing number')
assert.match(
  source,
  /href="https:\/\/beian\.mps\.gov\.cn\/#\/query\/webSearch\?code=33080202000739"/,
  'links public security filing number to the MPS query page'
)
assert.match(source, /target="_blank"/, 'opens filing links in a new tab')
