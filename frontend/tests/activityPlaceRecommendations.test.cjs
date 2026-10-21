const assert = require('assert')
const fs = require('fs')
const path = require('path')

const sourcePath = path.resolve(__dirname, '../src/utils/activityPlaceRecommendations.js')
const summarySource = fs.readFileSync(path.resolve(__dirname, '../src/utils/localReviews.js'), 'utf8').replace(/export function /g, 'function ')
let source = fs.readFileSync(sourcePath, 'utf8').replace(/^import .*$/gm, '')
source = source
  .replace(/export function /g, 'function ')
  .replace(/export \{[^}]+\}\s*$/m, '')

const moduleScope = new Function(`${summarySource}; ${source}; return { buildActivityPlaceRecommendations };`)()
const { buildActivityPlaceRecommendations } = moduleScope

const places = [
  { placeId: 1, name: '评分最高公园', address: '上海市', score: 0, reviewCount: 0, localReviewSummary: {score:4.9,count:20,source:'LOCAL_REVIEWS'} },
  { placeId: 2, name: '成都校内滑点', address: '成都市武侯区', score: 5, reviewCount: 99, localReviewSummary: {score:4.5,count:8,source:'LOCAL_REVIEWS'} },
  { placeId: 3, name: '人民广场滑点', address: '成都市锦江区', score: 5, reviewCount: 999, localReviewSummary: {score:4.2,count:4,source:'LOCAL_REVIEWS'} }
]

const activities = [
  {
    activityId: 10,
    title: '毕业约滑',
    placeId: 2,
    place: '成都校内滑点',
    activityTime: '2026-06-12T10:00:00'
  },
  {
    activityId: 11,
    title: '广场刷街',
    place: '人民广场滑点',
    activityTime: '2026-06-11T10:00:00'
  }
]

const result = buildActivityPlaceRecommendations(places, activities, 8)

assert.deepStrictEqual(
  result.map(item => item.placeId),
  [2, 3],
  'prioritizes places related to the current activity list instead of global score order'
)

assert.strictEqual(result[0].relatedCount, 1, 'adds related activity count')
assert.strictEqual(result[0].latestActivity.title, '毕业约滑', 'adds latest related activity')
assert.strictEqual(result[0].hasActivityLink, true, 'marks activity-linked places')

const fallback = buildActivityPlaceRecommendations(places, [], 2)
assert.deepStrictEqual(
  fallback.map(item => item.placeId),
  [1, 2],
  'falls back to local-review-summary-ordered places when no current activities reference places'
)
