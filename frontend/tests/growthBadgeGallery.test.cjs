const assert = require('assert')
const fs = require('fs')
const path = require('path')

const drawerPath = path.resolve(__dirname, '../src/components/growth/BadgeDetailDrawer.vue')
assert.ok(fs.existsSync(drawerPath), 'badge detail drawer exists')
const drawer = fs.existsSync(drawerPath) ? fs.readFileSync(drawerPath, 'utf8') : ''

assert.match(drawer, /<Teleport\s+to="body"/, 'drawer teleports above the page')
assert.match(drawer, /role="dialog"/, 'drawer exposes dialog semantics')
assert.match(drawer, /aria-modal="true"/, 'drawer is modal')
assert.match(drawer, /Escape/, 'Escape closes the drawer')
assert.match(drawer, /shiftKey/, 'drawer traps reverse tab navigation')
assert.match(drawer, /returnFocusTo/, 'drawer restores focus to its trigger')
assert.match(drawer, /document\.body\.style\.overflow/, 'drawer owns scroll locking')
assert.match(drawer, /@media\s*\(max-width:\s*640px\)/, 'mobile uses a bottom-sheet layout')
assert.match(drawer, /@media\s*\(prefers-reduced-motion:\s*reduce\)/, 'drawer honors reduced motion')
