const fs = require('fs')
const path = require('path')

const apiSource = fs.readFileSync(path.join(__dirname, '../src/api/index.js'), 'utf8')

function assertIncludes(source, needle, message) {
  if (!source.includes(needle)) throw new Error(message)
}

assertIncludes(apiSource, 'publicUserProfile: (userId) => http.get(`/api/public/users/${userId}`)', 'missing publicUserProfile api')
assertIncludes(apiSource, 'publicUserPosts: (userId, params) => http.get(`/api/public/users/${userId}/posts`, { params })', 'missing publicUserPosts api')

console.log('public user profile api contract passed')
