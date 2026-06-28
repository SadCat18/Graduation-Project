const fs = require('fs')
const path = require('path')

const source = fs.readFileSync(path.resolve(__dirname, '../src/views/MessagesPage.vue'), 'utf8')

function assertIncludes(needle, message) {
  if (!source.includes(needle)) throw new Error(message)
}

assertIncludes('useRoute', 'MessagesPage must read route params')
assertIncludes('useRouter', 'MessagesPage must update the route when selecting conversations')
assertIncludes('route.params.conversationId', 'MessagesPage must open direct conversation URLs')
assertIncludes('api.privateConversations', 'MessagesPage must load conversation list')
assertIncludes('api.privateMessages', 'MessagesPage must load selected message thread')
assertIncludes('api.sendPrivateMessage', 'MessagesPage must send messages')
assertIncludes('api.readPrivateConversation', 'MessagesPage must mark selected conversation as read')
assertIncludes("import { gsap } from 'gsap'", 'MessagesPage must use GSAP for message motion')
assertIncludes('prefersReducedMotion', 'MessagesPage must respect reduced-motion preferences')
assertIncludes('animatePageEnter', 'MessagesPage must animate the initial layout entrance')
assertIncludes('animateThreadSwitch', 'MessagesPage must animate conversation switches')
assertIncludes('animateRecentMessages', 'MessagesPage must animate recent message bubbles')
assertIncludes('animateUnreadBadges', 'MessagesPage must animate unread badges')
assertIncludes('animateSendPress', 'MessagesPage must animate send feedback')
assertIncludes('selectConversation', 'MessagesPage must expose conversation selection')
assertIncludes('sendMessage', 'MessagesPage must expose sending')
assertIncludes('class="conversation-list"', 'MessagesPage must render a conversation list')
assertIncludes('class="message-thread"', 'MessagesPage must render a chat thread')

console.log('messages page contract passed')
