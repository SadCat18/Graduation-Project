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

const galleryPath = path.resolve(__dirname, '../src/components/growth/BadgeGalleryPanel.vue')
assert.ok(fs.existsSync(galleryPath), 'badge gallery panel exists')
const gallery = fs.existsSync(galleryPath) ? fs.readFileSync(galleryPath, 'utf8') : ''

assert.match(gallery, /defineProps\([\s\S]*badges/, 'gallery receives badges without refetching')
assert.match(gallery, /statusFilter/, 'gallery owns status filtering')
assert.match(gallery, /rarityFilter/, 'gallery owns rarity filtering')
assert.match(gallery, /filteredBadges/, 'gallery derives a filtered list')
assert.match(gallery, /unlockedCount/, 'gallery derives completion totals')
assert.match(gallery, /rarityCounts/, 'gallery derives rarity totals')
assert.match(gallery, /aria-live="polite"/, 'filter results are announced')
assert.match(gallery, /<BadgeDetailDrawer/, 'gallery opens the detail drawer')
assert.match(gallery, /grid-template-columns:\s*repeat\(4/, 'desktop gallery supports four columns')
assert.match(gallery, /grid-template-columns:\s*repeat\(2/, 'tablet gallery uses two columns')
assert.match(gallery, /grid-template-columns:\s*1fr/, 'mobile gallery uses one column')
