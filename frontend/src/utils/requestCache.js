const cacheStore = new Map()

function isFresh(entry) {
  return entry && entry.expiresAt > Date.now()
}

export function getCachedResource(key, loader, ttlMs = 60000) {
  const current = cacheStore.get(key)

  if (current?.promise) {
    return current.promise
  }

  if (isFresh(current)) {
    return Promise.resolve(current.value)
  }

  const promise = loader()
    .then((value) => {
      cacheStore.set(key, {
        value,
        expiresAt: Date.now() + ttlMs
      })
      return value
    })
    .catch((error) => {
      cacheStore.delete(key)
      throw error
    })

  cacheStore.set(key, {
    promise,
    expiresAt: Date.now() + ttlMs
  })

  return promise
}

export function clearCachedResource(key) {
  cacheStore.delete(key)
}

export function loadCachedPublic(key, loader) {
  return getCachedResource(key, loader, 60000)
}
