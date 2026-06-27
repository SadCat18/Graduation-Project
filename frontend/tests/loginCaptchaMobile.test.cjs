const assert = require('assert')
const fs = require('fs')
const path = require('path')

const source = fs.readFileSync(path.resolve(__dirname, '../src/views/LoginPage.vue'), 'utf8')

assert.match(
  source,
  /\.captcha-row\s*\{[\s\S]*grid-template-columns:\s*minmax\(0,\s*1fr\)\s*120px/,
  'desktop captcha row keeps the input flexible instead of allowing overflow'
)

assert.match(
  source,
  /@media \(max-width:\s*520px\)[\s\S]*\.captcha-row\s*\{[\s\S]*grid-template-columns:\s*minmax\(0,\s*1fr\)\s*104px/,
  'mobile captcha row keeps input and image visible in two compact columns'
)

assert.match(
  source,
  /@media \(max-width:\s*360px\)[\s\S]*\.captcha-row\s*\{[\s\S]*grid-template-columns:\s*1fr/,
  'very narrow screens can stack the captcha controls safely'
)
