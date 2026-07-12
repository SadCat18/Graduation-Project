const assert = require('assert')
const fs = require('fs')
const path = require('path')

const source = fs.readFileSync(path.resolve(__dirname, '../src/views/AiCoachPage.vue'), 'utf8')

assert.match(source, /function\s+formatCoachContent\(content\)/, 'AI coach answers should pass through a formatter')
assert.match(source, /function\s+normalizeCoachDisplayContent\(content\)/, 'AI coach should clean persisted JSON-like raw answers before display')
assert.match(source, /formatCoachContent\(normalizeCoachDisplayContent\(item\.content\)\)/, 'assistant answers should render the cleaned display content')
assert.match(source, /\.message-card\s*\{[\s\S]*?min-width:\s*0;/, 'message cards must be allowed to shrink inside chat rows')
assert.match(source, /\.message-content\s*\{[\s\S]*?overflow-wrap:\s*anywhere;/, 'message content must wrap long JSON/code-like tokens')
assert.match(source, /\.coach-answer-line\s*\{[\s\S]*?overflow-wrap:\s*anywhere;/, 'formatted answer lines must wrap long assistant output')

console.log('AI coach message overflow contract passed')
