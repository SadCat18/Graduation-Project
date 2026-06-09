const assert = require('assert')
const fs = require('fs')
const path = require('path')

const sourcePath = path.resolve(__dirname, '../src/views/ActivityDetailPage.vue')
const source = fs.readFileSync(sourcePath, 'utf8')

assert.match(source, /activityIntro/, 'defines an activity introduction value')
assert.match(source, /activityContent/, 'defines a full activity content value')
assert.match(source, /活动说明/, 'renders the full activity description section')
assert.match(source, /未保存单独的完整说明/, 'explains old activities that do not have separate full content')
assert.doesNotMatch(
  source,
  /detail\.activityDesc\s*\|\|\s*detail\.content/,
  'does not collapse activityDesc and content into a single fallback field'
)
