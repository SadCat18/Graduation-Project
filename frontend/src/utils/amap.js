const AMAP_JS_URL = 'https://webapi.amap.com/maps'
const AMAP_KEY = (process.env.VUE_APP_AMAP_KEY || '').trim()

let mapLoaderPromise = null
let amapErrorGuard = null

// Suppress AMap cross-origin errors from bubbling to webpack overlay.
// AMap SDK initialization can throw on some browsers/keys, but the
// error is opaque ("Script error.") due to cross-origin restrictions.
function setupAmapErrorGuard() {
  if (amapErrorGuard) return
  amapErrorGuard = new AbortController()
  window.addEventListener("error", function amapGuard(e) {
    var fromAmap = e.filename && e.filename.indexOf("webapi.amap.com") !== -1
    var anonymousScriptError = !e.filename && e.message === "Script error."
    if (fromAmap || anonymousScriptError) {
      e.preventDefault()
    }
  }, { capture: true, signal: amapErrorGuard.signal })
}

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
  window._AMapSecurityConfig = {
    ...base,
    serviceHost
  }
}

export function loadAMap() {
  if (window.AMap) {
    return Promise.resolve(window.AMap)
  }
  if (!AMAP_KEY) {
    return Promise.reject(new Error('高德地图 Key 未配置'))
  }
  if (mapLoaderPromise) {
    return mapLoaderPromise
  }

  let timeout
  mapLoaderPromise = new Promise((resolve, reject) => {
    timeout = setTimeout(() => reject(new Error('高德地图加载超时')), 15000)
    ensureSecurityConfig()

    setupAmapErrorGuard()
    const existing = document.querySelector('script[data-amap-loader="1"]')
    if (existing) {
      existing.addEventListener('load', () => { if (amapErrorGuard) amapErrorGuard.abort(); resolve(window.AMap) })
      existing.addEventListener('error', () => { if (amapErrorGuard) amapErrorGuard.abort(); reject(new Error('高德地图脚本加载失败')) })
      return
    }

    const script = document.createElement('script')
    script.src = `${AMAP_JS_URL}?v=2.0&key=${AMAP_KEY}&plugin=AMap.PlaceSearch,AMap.Geocoder,AMap.CitySearch,AMap.DistrictSearch`
    script.async = true
    script.defer = true
    script.setAttribute('data-amap-loader', '1')
    script.onload = () => { if (amapErrorGuard) amapErrorGuard.abort(); resolve(window.AMap) }
    script.onerror = () => { if (amapErrorGuard) amapErrorGuard.abort(); reject(new Error('高德地图脚本加载失败')) }
    document.head.appendChild(script)
  }).then(amap => {
    if (!amap?.Map) throw new Error('高德地图接口不可用')
    return amap
  }).catch(error => {
    document.querySelector('script[data-amap-loader="1"]')?.remove()
    if (amapErrorGuard) amapErrorGuard.abort()
    amapErrorGuard = null
    mapLoaderPromise = null
    throw error
  }).finally(() => clearTimeout(timeout))

  return mapLoaderPromise
}

export function amapKeyTail() {
  if (!AMAP_KEY || AMAP_KEY.length < 6) {
    return AMAP_KEY || ''
  }
  return `***${AMAP_KEY.slice(-6)}`
}
