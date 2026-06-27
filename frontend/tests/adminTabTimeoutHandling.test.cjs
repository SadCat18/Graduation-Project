const assert = require('assert')
const fs = require('fs')
const path = require('path')

const sourcePath = path.resolve(__dirname, '../src/views/AdminPage.vue')
const source = fs.readFileSync(sourcePath, 'utf8')

assert.match(source, /const adminLoadError\s*=\s*ref\(''\)/, 'tracks admin tab load errors in component state')
assert.match(
  source,
  /async function loadComments\(\)\s*\{[\s\S]*try\s*\{[\s\S]*api\.adminComments[\s\S]*\}\s*catch/,
  'comment management catches adminComments failures instead of leaking request timeout errors'
)
assert.match(
  source,
  /comments\.value\s*=\s*\[\]/,
  'failed comment loading clears stale comment rows'
)
assert.match(
  source,
  /async function loadTabData\(tabKey, force = false\)\s*\{[\s\S]*try\s*\{[\s\S]*\}\s*catch/,
  'admin tab loading catches request failures before they reach the Vue runtime overlay'
)
assert.match(
  source,
  /v-if="adminLoadError"/,
  'renders admin load failures inside the page instead of relying on dev overlay'
)
