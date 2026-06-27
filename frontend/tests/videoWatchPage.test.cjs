const assert = require('assert')
const fs = require('fs')
const path = require('path')

const viewPath = path.resolve(__dirname, '../src/views/VideoWatchPage.vue')
const routerPath = path.resolve(__dirname, '../src/router/index.js')
const apiPath = path.resolve(__dirname, '../src/api/index.js')

assert.ok(fs.existsSync(viewPath), 'adds a dedicated video watch page')

const viewSource = fs.readFileSync(viewPath, 'utf8')
const routerSource = fs.readFileSync(routerPath, 'utf8')
const apiSource = fs.readFileSync(apiPath, 'utf8')

assert.match(routerSource, /\/videos\/:id/, 'registers the station video watch route')
assert.match(apiSource, /videoDetail:\s*\(id\)\s*=>\s*http\.get\(`\/api\/public\/videos\/\$\{id\}`\)/, 'adds a public video detail api')
assert.match(viewSource, /<video[\s\S]*controls/, 'renders a real video player with native controls')
assert.match(viewSource, /watch-action-bar/, 'adds a mobile action bar for watching actions')
assert.match(viewSource, /copyVideoLink/, 'supports copying the video link from the fallback action')
assert.match(viewSource, /goCoach/, 'keeps a path back to the AI coach context')
