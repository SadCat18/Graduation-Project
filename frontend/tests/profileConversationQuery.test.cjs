const fs = require('fs')
const path = require('path')

const source = fs.readFileSync(path.join(__dirname, '../src/views/ProfilePage.vue'), 'utf8')

function assertIncludes(needle, message) {
  if (!source.includes(needle)) throw new Error(message)
}

assertIncludes('useRoute', 'ProfilePage must import useRoute')
assertIncludes('const route = useRoute()', 'ProfilePage must create route instance')
assertIncludes("route.query.tab === 'community'", 'ProfilePage must read tab=community')
assertIncludes('route.query.conversationId', 'ProfilePage must read conversationId query')
assertIncludes('selectConversationById', 'ProfilePage must expose conversation selection by id')

console.log('profile conversation query contract passed')
