const assert = require('assert')
const fs = require('fs')
const path = require('path')

const sourcePath = path.resolve(__dirname, '../src/views/CommunityPage.vue')
const source = fs.readFileSync(sourcePath, 'utf8')

assert.match(
  source,
  /async function loadSideData\(\)\s*\{[\s\S]*Promise\.allSettled/,
  'loads optional side data with allSettled so notice/news failures do not become uncaught runtime errors'
)

assert.match(
  source,
  /onMounted\(async \(\) => \{[\s\S]*Promise\.allSettled/,
  'mounting the community page does not let optional request failures escape to the dev overlay'
)
