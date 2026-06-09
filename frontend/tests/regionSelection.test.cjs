const assert = require('assert')
const fs = require('fs')
const path = require('path')

const sourcePath = path.resolve(__dirname, '../src/utils/regionSelection.js')
let source = fs.readFileSync(sourcePath, 'utf8')
source = source
  .replace(/export function /g, 'function ')
  .replace(/export \{[^}]+\}\s*$/m, '')

const moduleScope = new Function(`${source}; return { resolveCitySelection };`)()
const { resolveCitySelection } = moduleScope

assert.strictEqual(
  resolveCitySelection(['广元市', '成都市'], '四川省', '四川省', '成都市'),
  '成都市',
  'keeps the currently selected city when AMap only returns the province'
)

assert.strictEqual(
  resolveCitySelection(['广元市', '成都市'], '四川省', '四川省', ''),
  '',
  'does not silently choose the first city when the POI city is missing'
)

assert.strictEqual(
  resolveCitySelection(['广元市', '成都市'], '成都市', '四川省', ''),
  '成都市',
  'uses a valid POI city'
)

assert.strictEqual(
  resolveCitySelection(['北京市'], '', '北京市', ''),
  '北京市',
  'falls back to province name for municipality-style city lists'
)
