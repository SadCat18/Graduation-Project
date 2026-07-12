const assert = require('assert')
const fs = require('fs')
const path = require('path')

const sourcePath = path.resolve(__dirname, '../src/views/AiCoachPage.vue')
const source = fs.readFileSync(sourcePath, 'utf8')

assert.match(source, /references:\s*Array\.isArray\(extra\.references\)/, 'assistant messages should store references from chat response')
assert.match(source, /\{\s*suggestions:\s*result\?\.suggestions\s*\|\|\s*\[\],\s*references:\s*result\?\.references\s*\|\|\s*\[\]\s*\}/s, 'sendMessage should pass response references into assistant message')
assert.match(source, /class="coach-references"/, 'AI coach should render a references block under assistant answers')
assert.match(source, /v-for="reference in item\.references"/, 'reference cards should render from item.references')
assert.match(source, /function openReference\(reference\)/, 'AI coach should expose a reference opener')
assert.match(source, /router\.push\(reference\.routePath\)/, 'reference opener should route internal routePath references')
