const assert = require('assert')
const fs = require('fs')
const path = require('path')

const sourcePath = path.resolve(__dirname, '../src/utils/activityAiContent.js')
let source = fs.readFileSync(sourcePath, 'utf8')
source = source
  .replace(/export function /g, 'function ')
  .replace(/export \{[^}]+\}\s*$/m, '')

const moduleScope = new Function(`${source}; return { buildActivityAiFields, resolveActivitySubmitText };`)()
const { buildActivityAiFields, resolveActivitySubmitText } = moduleScope

const fields = buildActivityAiFields({
  description: '一起在学校里轻松约滑，毕业前留个纪念。',
  suitableFor: '毕业生和刚毕业的滑板爱好者',
  highlights: ['校内场地熟悉', '小范围组队拍照'],
  tips: ['带饮用水', '准时到场'],
  riskTips: ['下雨顺延', '全程佩戴护具']
})

assert.strictEqual(
  fields.activityDesc,
  '一起在学校里轻松约滑，毕业前留个纪念。',
  'puts only the generated activity introduction into activityDesc'
)

assert.strictEqual(
  fields.content,
  [
    '适合人群',
    '毕业生和刚毕业的滑板爱好者',
    '',
    '活动亮点',
    '- 校内场地熟悉',
    '- 小范围组队拍照',
    '',
    '注意事项',
    '- 带饮用水',
    '- 准时到场',
    '',
    '风险提示',
    '- 下雨顺延',
    '- 全程佩戴护具'
  ].join('\n'),
  'formats structured AI detail content without duplicating the introduction'
)

assert.ok(
  !fields.content.includes(fields.activityDesc),
  'does not duplicate the activity introduction in the detail content'
)

assert.deepStrictEqual(
  resolveActivitySubmitText({
    activityDesc: '一起在学校里轻松约滑，毕业前留个纪念。',
    content: '活动亮点\n- 校内场地熟悉'
  }),
  {
    activityDesc: '一起在学校里轻松约滑，毕业前留个纪念。',
    content: '活动亮点\n- 校内场地熟悉'
  },
  'keeps submit content and activityDesc separated when both fields are filled'
)

assert.deepStrictEqual(
  resolveActivitySubmitText({
    activityDesc: '只有活动说明',
    content: ''
  }),
  {
    activityDesc: '只有活动说明',
    content: '只有活动说明'
  },
  'falls back to activityDesc for content when the detail content field is empty'
)
