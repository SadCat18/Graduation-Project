const AMAP_JS_URL = 'https://webapi.amap.com/maps'
const DEFAULT_KEY = '6003345b78ed25b126a76cb9caf08a7b'
const AMAP_KEY = (process.env.VUE_APP_AMAP_KEY || DEFAULT_KEY).trim()
const AMAP_SECURITY_JS_CODE = (process.env.VUE_APP_AMAP_SECURITY_JS_CODE || '').trim()

let mapLoaderPromise = null

function resolveServiceHost() {
  const envHost = (process.env.VUE_APP_AMAP_SERVICE_HOST || '').trim()
  if (envHost) {
    return envHost.replace(/\/$/, '')
  }
  return `${window.location.origin}/_AMapService`
}

function ensureSecurityConfig() {
  const serviceHost = resolveServiceHost()
  const base = { ...(window._AMapSecurityConfig || {}) }
  if (AMAP_SECURITY_JS_CODE) {
    window._AMapSecurityConfig = {
      ...base,
      securityJsCode: AMAP_SECURITY_JS_CODE
    }
    return
  }
  window._AMapSecurityConfig = {
    ...base,
    serviceHost
  }
}

export function loadAMap() {
  if (window.AMap) {
    return Promise.resolve(window.AMap)
  }
  if (mapLoaderPromise) {
    return mapLoaderPromise
  }

  mapLoaderPromise = new Promise((resolve, reject) => {
    let settled = false

    const cleanupFns = []
    const cleanup = () => {
      cleanupFns.forEach(fn => {
        try {
          fn()
        } catch (_) {
          // ignore cleanup failures
        }
      })
      cleanupFns.length = 0
    }

    const fail = (message) => {
      if (settled) return
      settled = true
      cleanup()
      mapLoaderPromise = null
      reject(new Error(message))
    }

    const succeed = () => {
      if (settled) return
      settled = true
      cleanup()
      resolve(window.AMap)
    }

    const onGlobalScriptError = (event) => {
      const target = event?.target
      const src = target?.src || event?.filename || ''
      if (String(src).includes('webapi.amap.com/maps')) {
        fail('高德地图脚本加载失败，请检查 Key、域名白名单或网络连接')
      }
    }

    window.addEventListener('error', onGlobalScriptError, true)
    cleanupFns.push(() => window.removeEventListener('error', onGlobalScriptError, true))

    ensureSecurityConfig()

    const existing = document.querySelector('script[data-amap-loader="1"]')
    if (existing) {
      const onLoad = () => succeed()
      const onError = () => fail('高德地图脚本加载失败，请检查网络连接')
      existing.addEventListener('load', onLoad)
      existing.addEventListener('error', onError)
      cleanupFns.push(() => existing.removeEventListener('load', onLoad))
      cleanupFns.push(() => existing.removeEventListener('error', onError))
      return
    }

    const script = document.createElement('script')
    script.src = `${AMAP_JS_URL}?v=2.0&key=${AMAP_KEY}&plugin=AMap.PlaceSearch,AMap.Geocoder,AMap.CitySearch,AMap.DistrictSearch`
    script.async = true
    script.defer = true
    script.crossOrigin = 'anonymous'
    script.referrerPolicy = 'no-referrer-when-downgrade'
    script.setAttribute('data-amap-loader', '1')

    script.onload = () => {
      if (!window.AMap) {
        fail('高德地图脚本已返回，但 SDK 未就绪')
        return
      }
      succeed()
    }
    script.onerror = () => fail('高德地图脚本加载失败，请检查网络连接')

    document.head.appendChild(script)
  })

  return mapLoaderPromise
}

export function amapKeyTail() {
  if (!AMAP_KEY || AMAP_KEY.length < 6) {
    return AMAP_KEY || ''
  }
  return `***${AMAP_KEY.slice(-6)}`
}
