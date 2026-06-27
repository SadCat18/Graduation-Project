const fs = require('fs')
const path = require('path')

function read(file) {
  return fs.readFileSync(path.join(__dirname, '..', file), 'utf8')
}

function assertIncludes(source, needle, message) {
  if (!source.includes(needle)) throw new Error(message)
}

const router = read('src/router/index.js')
assertIncludes(router, 'UserProfilePage', 'router must lazy-load UserProfilePage')
assertIncludes(router, "path: '/users/:id'", 'router must expose /users/:id')

const page = read('src/views/UserProfilePage.vue')
assertIncludes(page, 'api.publicUserProfile', 'page must load public profile')
assertIncludes(page, 'api.publicUserPosts', 'page must load public posts')
assertIncludes(page, 'api.profile()', 'page must load signed-in user before deciding self state')
assertIncludes(page, 'api.createConversation', 'page must start private conversation')
assertIncludes(page, "router.push(`/community/post/${post.postId}`)", 'page must navigate to post detail')
assertIncludes(page, "router.push({ path: '/profile', query: { tab: 'community', conversationId: String(conversationId) } })", 'page must jump to profile conversation')
assertIncludes(page, "router.push('/profile')", 'self profile action must go to personal center')

console.log('public user profile page contract passed')
