const assert = require('assert')
const fs = require('fs')
const path = require('path')

const sourcePath = path.resolve(__dirname, '../src/views/AdminPage.vue')
const source = fs.readFileSync(sourcePath, 'utf8')

assert.match(source, /REVIEW_DECISION_TEMPLATES/, 'admin review center should define reusable decision templates')
assert.match(source, /function\s+chooseReviewTemplate/, 'admin review center should select a template before writing review notes')
assert.match(source, /function\s+resolveReviewNote/, 'admin review center should resolve review notes without ad-hoc prompts')
assert.match(source, /template-chip/, 'admin review center should render template chips in the review drawer')
assert.doesNotMatch(source, /prompt\('请输入驳回说明/, 'report handling should not rely on a raw prompt for reject notes')
assert.doesNotMatch(source, /prompt\('请输入处理说明/, 'report handling should not rely on a raw prompt for handle notes')
assert.doesNotMatch(source, /prompt\('请输入驳回原因/, 'bulletin review should not rely on a raw prompt for reject reasons')
