export function normalizeMediaUrl(value) {
  if (!value) return ''
  const raw = String(value).trim()
  if (!raw) return ''

  const url = raw.replace(/\\/g, '/')
  if (
    url.startsWith('http://') ||
    url.startsWith('https://') ||
    url.startsWith('data:') ||
    url.startsWith('blob:')
  ) {
    return url
  }
  if (url.startsWith('//')) {
    return `${window.location.protocol}${url}`
  }
  if (url.startsWith('/')) {
    return url
  }
  return `/${url}`
}
