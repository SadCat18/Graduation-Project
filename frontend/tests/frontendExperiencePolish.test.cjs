const assert = require('assert')
const fs = require('fs')
const path = require('path')

const src = path.resolve(__dirname, '../src')
const read = (relativePath) => fs.readFileSync(path.join(src, relativePath), 'utf8')

const app = read('App.vue')
const navigation = read('components/SiteNavigation.vue')
const styles = read('styles.css')
const home = read('views/HomePage.vue')
const coach = read('views/AiCoachPage.vue')
const video = read('views/VideoWatchPage.vue')
const aiOverlay = read('components/AiLoadingOverlay.vue')
const toast = read('utils/toast.js')

const mojibakePattern = /[�]|[姝婊瑙鍙鎬鐨勭粌]/

assert.doesNotMatch(coach, mojibakePattern, 'AI coach page should not contain mojibake text')
assert.doesNotMatch(video, mojibakePattern, 'video watch page should not contain mojibake text')
assert.doesNotMatch(aiOverlay, mojibakePattern, 'AI loading overlay should not contain mojibake text')

assert.match(app, /mobile-bottom-nav/, 'adds a dedicated mobile bottom navigation')
assert.match(navigation, /移动端主导航/, 'mobile navigation is labeled for assistive technology')
assert.match(app, /<AppToast\s*\/>/, 'mounts global toast feedback')
assert.match(app, /installToastAlertBridge/, 'bridges legacy alert feedback into toast notifications')
assert.match(toast, /window\.alert\s*=\s*\(message\)\s*=>\s*notify\(message\)/, 'toast utility replaces alert with non-blocking feedback')

assert.match(home, /mobile-event-strip/, 'keeps latest skate news visible on mobile below the hero')
const mobileEventPanelBlock = home.match(
  /@media \(max-width:\s*980px\)[\s\S]*?\.event-panel\s*\{([\s\S]*?)\n\s*\}/
)?.[1] || ''
assert.doesNotMatch(
  mobileEventPanelBlock,
  /display:\s*none/,
  'mobile layout should transform hero news instead of hiding it completely'
)
assert.match(home, /name="arrow-left"/, 'carousel previous button uses an icon')
assert.match(home, /name="arrow-right"/, 'carousel next button uses an icon')
assert.match(home, /home-feed-grid/, 'home feed grid opts out of equal-height stretching')
assert.match(home, /align-items:\s*start/, 'home feed grid keeps empty cards compact')
assert.match(home, /feed-item-no-cover/, 'home feed cards collapse the media column when no cover image exists')
assert.match(home, /home-compact-empty/, 'home feed cards show compact empty states instead of blank panels')

assert.doesNotMatch(styles, /transition:\s*all\b/, 'global CSS avoids transition: all')
assert.doesNotMatch(styles, /will-change:\s*transform,\s*opacity/, 'card animations avoid permanent will-change hints')
