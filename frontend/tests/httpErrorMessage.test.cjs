const assert = require('assert')
const fs = require('fs')
const path = require('path')

const source = fs.readFileSync(path.resolve(__dirname, '../src/api/http.js'), 'utf8')

assert.match(
  source,
  /error\?\.response\?\.data/,
  'HTTP error interceptor reads structured error response data'
)

assert.match(
  source,
  /responseBody\?\.message/,
  'HTTP error interceptor prefers backend business error messages'
)

assert.match(
  source,
  /Promise\.reject\(new Error\(message\)\)/,
  'HTTP error interceptor rejects with a user-facing message instead of raw Axios status text'
)
