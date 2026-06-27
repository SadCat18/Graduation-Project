const assert = require('assert')
const fs = require('fs')
const path = require('path')

const source = fs.readFileSync(path.resolve(__dirname, '../src/views/ActivitiesPage.vue'), 'utf8')
const mapCardBlock = source.match(/\.map-card\s*\{[\s\S]*?\n\}/)?.[0] || ''

assert.match(
  source,
  /\.right-pane\s*\{[\s\S]*position:\s*sticky[\s\S]*top:\s*74px[\s\S]*max-height:\s*calc\(100vh - 90px\)[\s\S]*overflow-y:\s*auto/,
  'the whole map/sidebar column is sticky and scrolls within the viewport'
)

assert.doesNotMatch(
  mapCardBlock,
  /position:\s*sticky/,
  'the map card itself is not sticky, so it cannot cover the place recommendations below it'
)

assert.match(
  source,
  /@media \(max-width:\s*980px\)[\s\S]*\.right-pane\s*\{[\s\S]*position:\s*static[\s\S]*max-height:\s*none[\s\S]*overflow:\s*visible/,
  'tablet and mobile layouts disable the sticky sidebar'
)
