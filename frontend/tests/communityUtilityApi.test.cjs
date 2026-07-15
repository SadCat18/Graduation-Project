const assert = require('assert')
const fs = require('fs')
const path = require('path')

const source = fs.readFileSync(path.resolve(__dirname, '../src/api/index.js'), 'utf8')

const requiredSnippets = [
  "createConversation: (data) => http.post('/api/messages/conversations', data)",
  "privateConversations: (params) => http.get('/api/messages/conversations', { params })",
  "privateMessages: (conversationId, params) => http.get(`/api/messages/conversations/${conversationId}/messages`, { params })",
  "sendPrivateMessage: (conversationId, data) => http.post(`/api/messages/conversations/${conversationId}/send`, data)",
  "readPrivateConversation: (conversationId, data) => http.post(`/api/messages/conversations/${conversationId}/read`, data)",
  "privateUnreadCount: () => http.get('/api/messages/unread-count')",
  "followUser: (userId) => http.post(`/api/users/${userId}/follow`)",
  "unfollowUser: (userId) => http.delete(`/api/users/${userId}/follow`)",
  "followers: (userId) => http.get(`/api/users/${userId}/followers`)",
  "following: (userId) => http.get(`/api/users/${userId}/following`)",
  "followStatus: (userId) => http.get(`/api/users/${userId}/follow-status`)",
  "replyComment: (commentId, data) => http.post(`/api/comments/${commentId}/replies`, data)",
  "likeComment: (commentId) => http.post(`/api/comments/${commentId}/like`)",
  "unlikeComment: (commentId) => http.delete(`/api/comments/${commentId}/like`)"
]

for (const snippet of requiredSnippets) {
  assert.ok(source.includes(snippet), `api client contains: ${snippet}`)
}

console.log('community utility api tests passed')
