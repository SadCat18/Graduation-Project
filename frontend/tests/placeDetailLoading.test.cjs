const assert = require('assert')
const fs = require('fs')
const path = require('path')

const sourcePath = path.resolve(__dirname, '../src/views/PlaceDetailPage.vue')
const source = fs.readFileSync(sourcePath, 'utf8')

assert.match(source, /onMounted\s*\(\s*loadData\s*\)/, 'loads place detail when the page is mounted')
assert.match(source, /watch\s*\(\s*placeId\s*,/, 'reloads place detail when the route id changes')
assert.match(source, /loadError/, 'shows an error state when place detail loading fails')
