const assert = require('assert')
const fs = require('fs')
const path = require('path')

const sourcePath = path.resolve(__dirname, '../src/views/PlaceDetailPage.vue')
const source = fs.readFileSync(sourcePath, 'utf8')

assert.match(source, /relatedVideos\s*=\s*ref\(\[\]\)/, 'place detail should keep related video state')
assert.match(source, /api\.videos\(\)/, 'place detail should reuse public videos for related video matching')
assert.match(source, /place\.suitableLevel/, 'place detail should render backend suitable level guidance')
assert.match(source, /place\.obstacleTags/, 'place detail should render backend obstacle tags')
assert.match(source, /place\.safetyTips/, 'place detail should render backend safety tips')
assert.match(source, /class="related-videos"/, 'place detail should render a related videos section')
assert.match(source, /goVideo\(item\.videoId\)/, 'related videos should navigate to the video watch page')
