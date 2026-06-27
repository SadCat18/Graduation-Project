const assert = require('assert')
const fs = require('fs')
const path = require('path')

const source = fs.readFileSync(path.resolve(__dirname, '../src/views/ProfilePage.vue'), 'utf8')

assert.match(
  source,
  /SUPPORTED_AVATAR_IMAGE_EXTENSIONS/,
  'profile page keeps an explicit avatar extension allowlist'
)

assert.match(
  source,
  /\.jpg'[\s\S]*\.jpeg'[\s\S]*\.png'[\s\S]*\.gif'[\s\S]*\.webp'[\s\S]*\.bmp'/,
  'avatar allowlist matches backend-supported image formats'
)

assert.match(
  source,
  /HEIC|HEIF|JPG|PNG|WebP/,
  'unsupported mobile photo formats explain that users should choose a supported web image'
)

assert.match(
  source,
  /isSupportedAvatarImage\(file\)/,
  'avatar selection validates file compatibility before uploading'
)
