const assert = require('assert')
const fs = require('fs')
const path = require('path')

const componentPath = path.resolve(__dirname, '../src/components/growth/LevelRoadmapPanel.vue')
assert.ok(fs.existsSync(componentPath), 'level roadmap panel exists')
const source = fs.existsSync(componentPath) ? fs.readFileSync(componentPath, 'utf8') : ''

assert.match(source, /defineProps\([\s\S]*roadmap/, 'panel receives backend roadmap data')
assert.match(source, /currentLevel/, 'current identity is rendered')
assert.match(source, /remainToNextLevel/, 'next-level distance is rendered')
assert.match(source, /最高等级/, 'level ten has a max-level message')
assert.match(source, /v-for="level in roadmap\.levels"/, 'all backend nodes are rendered')
assert.match(source, /COMPLETED|CURRENT|LOCKED/, 'three route states are represented')
assert.match(source, /:aria-current/, 'current node is exposed semantically')
assert.match(source, /overflow-x:\s*hidden/, 'route cannot create horizontal overflow')
assert.match(source, /@media\s*\(max-width:\s*700px\)/, 'mobile switches to a dedicated route layout')
assert.match(source, /@media\s*\(prefers-reduced-motion:\s*reduce\)/, 'route honors reduced motion')
