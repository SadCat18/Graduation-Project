const assert = require('assert')
const fs = require('fs')
const path = require('path')

const src = path.resolve(__dirname, '../src')
const read = relativePath => fs.readFileSync(path.join(src, relativePath), 'utf8')

const app = read('App.vue')
const navigation = read('components/SiteNavigation.vue')
const styles = read('styles.css')
const skate = read('views/SkateProfessionalPage.vue')
const prefetch = read('utils/routePrefetch.js')

const buttonBlock = styles.match(/button\s*\{([\s\S]*?)\n\}/)?.[1] || ''
const cardAnimation = styles.match(/@keyframes card-rise-in\s*\{([\s\S]*?)\n\}/)?.[1] || ''

assert.doesNotMatch(buttonBlock, /backdrop-filter/, 'global buttons avoid per-element realtime blur while scrolling')
assert.doesNotMatch(cardAnimation, /filter:/, 'card entrance animation stays on compositor-friendly opacity and transforms')
assert.match(
  skate,
  /Promise\.all\(\[loadTricks\(\), loadVideos\(\), loadCurrentTraining\(\)\]\)/,
  'skate page initially requests only content required by the first viewport'
)
assert.match(skate, /async function activateTab\(tabKey\)/, 'secondary skate modules load on demand')
assert.doesNotMatch(skate, /v-show="activeTab ===/, 'inactive skate modules are not left mounted')
assert.match(skate, /v-if="activeTab === 'equipment'"/, 'equipment cards render only when their tab is active')
assert.match(skate, /loading="lazy" decoding="async"/, 'secondary skate images decode asynchronously and load lazily')
assert.match(skate, /<EquipmentTypeArt :category=/, 'knowledge cards use local category art rather than unrelated remote product photos')
assert.match(navigation, /@mouseenter="prefetch\(to\)"/, 'desktop route chunks warm on navigation intent')
assert.match(navigation, /@touchstart\.passive="prefetch\(to\)"/, 'mobile route chunks warm on touch intent')
assert.match(prefetch, /export function warmRouteChunk\(path\)/, 'route prefetching targets one intended route')
assert.doesNotMatch(app, /requestIdleCallback\(warmRoutes/, 'the app does not parse every likely route during initial scrolling')

console.log('page rendering performance contracts passed')
