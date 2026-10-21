// Pinned MIT snapshot; provenance and coverage: public/regions/README.md.
export async function regionOptions(level, province = '', city = '') {
  const controller = new AbortController()
  let timer
  try {
    const data = await Promise.race([
      fetch('/regions/china-area-5.0.1.json', { signal: controller.signal }).then(async response => {
        if (!response.ok) throw new Error('地区资料加载失败')
        return response.json()
      }),
      new Promise((_, reject) => { timer = setTimeout(() => { controller.abort(); reject(new Error('地区资料加载超时')) }, 10000) })
    ])
    if (!data?.['86'] || typeof data['86'] !== 'object') throw new Error('地区资料格式错误')
    const provinces = data['86']
    if (level === 'province') return Object.values(provinces)
    const provinceCode = Object.keys(provinces).find(key => provinces[key] === province)
    if (!provinceCode) throw new Error('省份资料不可用')
    const cities = data[provinceCode] || {}
    const municipality = ['110000', '120000', '310000', '500000'].includes(provinceCode)
    const directDistricts = ['810000', '820000'].includes(provinceCode)
    if (municipality || directDistricts) {
      if (level === 'city') return [province]
      if (city !== province) throw new Error('城市资料不可用')
      return directDistricts ? Object.values(cities) : Object.keys(cities).flatMap(code => Object.values(data[code] || {}))
    }
    const entries = Object.entries(cities).flatMap(([code, name]) =>
      /直辖县级行政区划/.test(name) ? Object.entries(data[code] || {}) : [[code, name]])
    if (level === 'city') return entries.map(([, name]) => name)
    const entry = entries.find(([, name]) => name === city)
    if (!entry) throw new Error('城市资料不可用')
    return Object.values(data[entry[0]] || {}).filter(name => name !== '市辖区')
  } finally { clearTimeout(timer) }
}
