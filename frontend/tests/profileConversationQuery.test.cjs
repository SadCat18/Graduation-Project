const fs = require('fs')
const path = require('path')

const profileSource = fs.readFileSync(path.join(__dirname, '../src/views/ProfilePage.vue'), 'utf8')
const messagesSource = fs.readFileSync(path.join(__dirname, '../src/views/MessagesPage.vue'), 'utf8')

function assertIncludes(source, needle, message) {
  if (!source.includes(needle)) throw new Error(message)
}

function assertExcludes(source, needle, message) {
  if (source.includes(needle)) throw new Error(message)
}

assertExcludes(profileSource, "route.query.tab === 'community'", 'ProfilePage must not auto-open community tab for conversations')
assertExcludes(profileSource, 'route.query.conversationId', 'ProfilePage must not own conversation routing')
assertExcludes(profileSource, 'selectConversationById', 'ProfilePage must not expose conversation selection by id')
assertIncludes(messagesSource, 'route.params.conversationId', 'MessagesPage must own direct conversation routing')

console.log('standalone message conversation routing contract passed')
