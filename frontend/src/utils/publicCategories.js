import { ref } from 'vue'
import { api } from '../api'
function validOptions(items) {
  return Array.isArray(items) && items.every(item => typeof item?.key === 'string' && item.key.trim() && typeof item.label === 'string')
}
export function usePublicCategories(kind) {
  const options = ref([]), loading = ref(false), error = ref(''), metadata = ref(null), legacyOptions = ref([])
  async function load() {
    if (loading.value) return
    loading.value = true; error.value = ''
    try {
      const data = kind === 'equipment' ? await api.equipmentCategories() : await api.postCategories()
      const items = kind === 'equipment' ? data : data?.[kind === 'create' ? 'createOptions' : 'filterOptions']
      if (!validOptions(items)) throw new Error('分类资料格式不可用')
      options.value = items
      if (kind !== 'equipment') { metadata.value = data; legacyOptions.value = validOptions(data.legacyFilterOptions) ? data.legacyFilterOptions : [] }
    } catch (e) { error.value = e?.message || '分类加载失败' }
    finally { loading.value = false }
  }
  return { options, metadata, legacyOptions, loading, error, load }
}
export function categoryMatches(raw, selected, metadata) {
  if (!selected) return true
  const aliases=metadata?.aliasMap || {}, canonical=new Set((metadata?.filterOptions || []).map(x=>x.key))
  const target=aliases[selected] || selected
  if (!canonical.has(target)) return raw === selected
  return categoryGroup(raw, metadata) === target
}
export function categoryGroup(raw, metadata) {
  if (!metadata) return raw || '未分类'
  return (metadata.filterOptions || []).some(x=>x.key===raw) ? raw : metadata.aliasMap?.[raw] || metadata.groupMap?.[raw] || metadata.fallbackGroup || raw || '未分类'
}
