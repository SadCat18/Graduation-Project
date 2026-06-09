function normalizeRegionValue(value) {
  if (Array.isArray(value)) {
    return normalizeRegionValue(value[0])
  }
  return String(value || '').trim()
}

export function resolveCitySelection(cityOptions = [], cityName = '', provinceName = '', currentCity = '') {
  const options = Array.isArray(cityOptions) ? cityOptions : []
  const candidates = [
    normalizeRegionValue(cityName),
    normalizeRegionValue(currentCity),
    normalizeRegionValue(provinceName)
  ]

  return candidates.find(candidate => candidate && options.includes(candidate)) || ''
}
