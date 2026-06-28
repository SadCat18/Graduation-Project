const fs = require('fs')
const path = require('path')

function read(file) {
  return fs.readFileSync(path.resolve(__dirname, '..', file), 'utf8')
}

function assertIncludes(source, needle, message) {
  if (!source.includes(needle)) throw new Error(message)
}

function assertExcludes(source, needle, message) {
  if (source.includes(needle)) throw new Error(message)
}

const router = read('src/router/index.js')
assertIncludes(router, 'MessagesPage', 'router must lazy-load MessagesPage')
assertIncludes(router, "path: '/messages'", 'router must expose /messages')
assertIncludes(router, "path: '/messages/:conversationId'", 'router must expose /messages/:conversationId')

const app = read('src/App.vue')
assertIncludes(app, "to: '/messages'", 'signed-in navigation must include messages')
assertIncludes(app, "label: '私信'", 'messages navigation label must be private messages')
assertIncludes(app, "icon: 'message'", 'messages navigation must use message icon')
assertIncludes(app, "base.push({ to: '/messages', label: '私信', icon: 'message' })", 'messages navigation must be appended next to personal center')
assertIncludes(app, "base.push({ to: '/profile', label: '个人中心', icon: 'user' })", 'personal center must remain after messages')
assertExcludes(app, 'base.splice(2', 'messages navigation must not be inserted in the middle of the nav')

const profile = read('src/views/ProfilePage.vue')
assertExcludes(profile, 'api.privateMessages', 'ProfilePage must not fetch private message thread content')
assertExcludes(profile, 'sendPrivateMessage', 'ProfilePage must not send private messages')
assertExcludes(profile, 'route.query.conversationId', 'ProfilePage must not read conversation query parameters')
assertExcludes(profile, '私信会话', 'ProfilePage must not render private conversation panel')
assertIncludes(profile, 'followersList', 'ProfilePage must still show followers')
assertIncludes(profile, 'followingList', 'ProfilePage must still show following')

console.log('messages page entrypoints contract passed')
