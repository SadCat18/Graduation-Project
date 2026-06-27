const fs = require('fs')
const path = require('path')

const source = fs.readFileSync(path.join(__dirname, '../src/views/PostDetailPage.vue'), 'utf8')

function assertIncludes(needle, message) {
  if (!source.includes(needle)) throw new Error(message)
}

function assertExcludes(needle, message) {
  if (source.includes(needle)) throw new Error(message)
}

assertIncludes('goUserProfile', 'post detail must define user profile navigation')
assertIncludes('router.push(`/users/${userId}`)', 'post detail must route to /users/:id')
assertIncludes('@click="goUserProfile(post.userId)"', 'post author identity must link to public profile')
assertIncludes('@click="goUserProfile(item.userId)"', 'top-level comment author must link to public profile')
assertIncludes('@click="goUserProfile(reply.userId)"', 'reply author must link to public profile')
assertExcludes('startAuthorConversation', 'post detail must not start private conversation directly')
assertExcludes('api.createConversation({ targetUserId: post.value.userId })', 'post detail must not call createConversation for author')

console.log('post detail user link contract passed')
