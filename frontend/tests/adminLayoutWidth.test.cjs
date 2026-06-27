const assert = require('assert')
const fs = require('fs')
const path = require('path')

const appSource = fs.readFileSync(path.resolve(__dirname, '../src/App.vue'), 'utf8')
const routerSource = fs.readFileSync(path.resolve(__dirname, '../src/router/index.js'), 'utf8')

assert.match(
  routerSource,
  /path:\s*'\/admin'[\s\S]*meta:\s*\{[^}]*wideLayout:\s*true/,
  'admin route opts into the wide application layout'
)

assert.match(
  appSource,
  /route\.meta\.wideLayout/,
  'App shell reads the route wideLayout metadata'
)

assert.match(
  appSource,
  /:class="\[[^\]]*'main-container-wide'/,
  'main container applies a wide layout class from route metadata'
)

assert.match(
  appSource,
  /\.main-container-wide\s*\{[\s\S]*max-width:\s*1680px/,
  'wide admin layout raises the main container max width'
)
