const assert = require('assert')
const fs = require('fs')
const path = require('path')

const source = fs.readFileSync(path.resolve(__dirname, '../src/views/ProfilePage.vue'), 'utf8')
const globalStyles = fs.readFileSync(path.resolve(__dirname, '../src/styles.css'), 'utf8')

assert.match(source, /function switchProfileTab\(tab\)/, 'profile tabs share one transition entry point')
assert.doesNotMatch(
  source,
  /@click="profileTab = '[^']+'"/,
  'main tab buttons do not bypass the transition entry point'
)
assert.match(source, /ref="profileTabHost"/, 'tab views share a measurable height host')
assert.match(source, /class="[^"]*profile-tab-view/, 'each active tab uses the same full-width view')
assert.match(source, /PROFILE_TAB_TRANSITION_MS\s*=\s*280/, 'height and entrance motion use the agreed 280ms duration')
assert.match(source, /translateY\(10px\)/, 'new tab content enters from 10px below')
assert.match(
  source,
  /\.profile-tab-host[\s\S]*?width:\s*100%;[\s\S]*?min-width:\s*0;/,
  'the transition host keeps every tab at one stable width'
)
assert.match(source, /prefers-reduced-motion:\s*reduce/, 'reduced-motion users get an immediate switch')
assert.match(source, /clearProfileTabTransition/, 'pending transition cleanup is centralized')
assert.match(
  globalStyles,
  /\.main-container\s*\{[^}]*width:\s*100%;[^}]*\}/,
  'the shared page container cannot shrink to the active tab intrinsic width'
)
