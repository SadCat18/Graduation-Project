const base = require('./playwright.config.cjs')
// Explicit Java/H2 browser harness; bundled Chromium by default or PLAYWRIGHT_CHANNEL.
module.exports = {
  ...base,
  testIgnore: [],
  testMatch: 'board-t07-real-http.spec.cjs',
  fullyParallel:false,
  workers:1,
  projects:[{name:'http-browser',use:{...base.projects[0].use,...(process.env.PLAYWRIGHT_CHANNEL ? {channel:process.env.PLAYWRIGHT_CHANNEL} : {})}}],
  webServer:{...base.webServer,command:'node node_modules/@vue/cli-service/bin/vue-cli-service.js serve --host 127.0.0.1 --port 4173'}
}
