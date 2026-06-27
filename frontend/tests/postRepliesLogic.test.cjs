const assert = require('assert')
const fs = require('fs')
const path = require('path')

const sourcePath = path.resolve(__dirname, '../src/utils/communityComments.js')
assert.ok(fs.existsSync(sourcePath), 'community comment logic module exists')

const source = fs.readFileSync(sourcePath, 'utf8').replace(/export function /g, 'function ')
const { normalizeCommentThreads } = new Function(`${source}; return { normalizeCommentThreads };`)()

const result = normalizeCommentThreads([
  { commentId: 1, parentId: 0, content: 'root-a' },
  { commentId: 2, parentId: 1, content: 'reply-a' },
  { commentId: 3, parentId: null, content: 'root-b', replies: [{ commentId: 4, parentId: 3, content: 'existing' }] },
  { commentId: 5, parentId: 3, content: 'reply-b' }
])

assert.deepStrictEqual(result.map(item => item.commentId), [1, 3], 'only root comments are returned at top level')
assert.deepStrictEqual(result[0].replies.map(item => item.commentId), [2], 'reply is grouped under parent')
assert.deepStrictEqual(result[1].replies.map(item => item.commentId), [4, 5], 'existing nested replies are preserved before flat replies')
assert.deepStrictEqual(normalizeCommentThreads(null), [], 'null input returns an empty list')

console.log('post reply logic tests passed')
