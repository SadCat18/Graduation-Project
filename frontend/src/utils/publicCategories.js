import { ref } from 'vue'
import { api } from '../api'
function validOptions(items) {
  return Array.isArray(items) && items.every(item => typeof item?.key === 'string' && item.key.trim() && typeof item.label === 'string')
}
export function usePublicCategories(kind) {
  const options = ref([]), loading = ref(false), error = ref('')
  async function load() {
    if (loading.value) return
    loading.value = true; error.value = ''
    try {
      const data = kind === 'equipment' ? await api.equipmentCategories() : await api.postCategories()
      const items = kind === 'equipment' ? data : data?.[kind === 'create' ? 'createOptions' : 'filterOptions']
      if (!validOptions(items)) throw new Error('分类资料格式不可用')
      options.value = items
    } catch (e) { error.value = e?.message || '分类加载失败' }
    finally { loading.value = false }
  }
  return { options, loading, error, load }
}
