<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import AiLoadingOverlay from '../components/AiLoadingOverlay.vue'
import { api } from '../api'
import { getToken } from '../utils/auth'
import { amapKeyTail, loadAMap } from '../utils/amap'

const router = useRouter()
const amapWebKey = (process.env.VUE_APP_AMAP_KEY || '').trim()

const searchingPoi = ref(false)
const generatingCopy = ref(false)
const mapReady = ref(false)
const mapError = ref('')
const searchResults = ref([])
const places = ref([])
const provinceOptions = ref([])
const cityOptions = ref([])
const districtOptions = ref([])
const aiResult = ref(null)
const aiLoadingSteps = ['整理活动信息', '分析时间与地点', '生成召集文案', '提炼活动亮点', '补全注意事项与风险提示']

const mapRef = ref(null)
const locationKeyword = ref('')
const form = reactive({
  title: '',
  content: '',
  activityDesc: '',
  activityType: '',
  province: '',
  city: '',
  district: '',
  place: '',
  address: '',
  longitude: null,
  latitude: null,
  activityTime: '',
  maxNum: 2
})
const selectedPlaceId = ref(null)

const isLoggedIn = computed(() => !!getToken())
const amapKeyMask = computed(() => amapKeyTail())
const activityBaseContent = computed(() => (form.activityDesc || form.content || '').trim())

let mapInstance = null
let geocoderInstance = null
let placeSearchInstance = null
let citySearchInstance = null
let pickedMarker = null

function backToList() {
  router.push('/activities')
}

function normalizeRegionName(value) {
  if (Array.isArray(value)) {
    return value[0] || ''
  }
  return value || ''
}

function searchDistrict(keyword, level = 'country', subdistrict = 1) {
  return new Promise((resolve) => {
    if (!window.AMap?.DistrictSearch) {
      resolve([])
      return
    }
    const districtSearch = new window.AMap.DistrictSearch({
      level,
      subdistrict,
      showbiz: false,
      extensions: 'base'
    })
    districtSearch.search(keyword, (status, result) => {
      if (status !== 'complete') {
        resolve([])
        return
      }
      resolve(result?.districtList || [])
    })
  })
}

async function searchDistrictByRest(keyword, subdistrict = 1) {
  if (!amapWebKey) return []
  const query = new URLSearchParams({
    key: amapWebKey,
    keywords: keyword,
    subdistrict: String(subdistrict),
    extensions: 'base'
  }).toString()
  try {
    const response = await fetch(`/_AMapService/v3/config/district?${query}`)
    const data = await response.json()
    if (data?.status !== '1') return []
    return data?.districts || []
  } catch (_) {
    return []
  }
}

async function queryDistrictList(keyword, level = 'country', subdistrict = 1) {
  const pluginList = await searchDistrict(keyword, level, subdistrict)
  if (pluginList?.length) return pluginList
  return searchDistrictByRest(keyword, subdistrict)
}

async function loadProvinceOptions() {
  const districtList = await queryDistrictList('中国', 'country', 1)
  const provinces = districtList?.[0]?.districtList || []
  provinceOptions.value = Array.from(new Set(provinces.map(item => item.name).filter(Boolean)))
}

async function loadCityOptions(provinceName) {
  if (!provinceName) {
    cityOptions.value = []
    districtOptions.value = []
    return
  }
  const districtList = await queryDistrictList(provinceName, 'province', 1)
  const children = districtList?.[0]?.districtList || []

  let cities = children
    .map(item => item.name)
    .filter(Boolean)
    .filter(name => name !== '市辖区')

  if (!cities.length || children[0]?.level === 'district') {
    cities = [provinceName]
  }

  cityOptions.value = Array.from(new Set(cities))
  if (!cityOptions.value.includes(form.city)) {
    form.city = ''
  }
}

async function loadDistrictOptions(cityName) {
  if (!cityName) {
    districtOptions.value = []
    return
  }
  const districtList = await queryDistrictList(cityName, 'city', 1)
  const children = districtList?.[0]?.districtList || []
  const districts = children
    .map(item => item.name)
    .filter(Boolean)
    .filter(name => name !== '市辖区')

  districtOptions.value = Array.from(new Set(districts))
  if (!districtOptions.value.includes(form.district)) {
    form.district = ''
  }
}

async function onProvinceChange() {
  form.city = ''
  form.district = ''
  districtOptions.value = []
  await loadCityOptions(form.province)
}

async function onCityChange() {
  form.district = ''
  await loadDistrictOptions(form.city)
}

async function setProvinceCityDistrict(provinceName, cityName, districtName = '') {
  if (!provinceName) return
  form.province = provinceName
  await loadCityOptions(provinceName)
  const targetCity = cityName || provinceName
  form.city = cityOptions.value.includes(targetCity) ? targetCity : (cityOptions.value[0] || '')
  await loadDistrictOptions(form.city)
  if (districtName && districtOptions.value.includes(districtName)) {
    form.district = districtName
  }
}

async function initMap() {
  await nextTick()
  if (!mapRef.value) return

  try {
    const AMap = await loadAMap()
    mapInstance = new AMap.Map(mapRef.value, {
      zoom: 11,
      center: [116.397428, 39.90923],
      viewMode: '3D'
    })
    geocoderInstance = new AMap.Geocoder()
    placeSearchInstance = new AMap.PlaceSearch({
      city: '全国',
      pageSize: 8
    })
    citySearchInstance = new AMap.CitySearch()

    await loadProvinceOptions()
    if (!provinceOptions.value.length) {
      mapError.value = '省市区数据加载失败，请检查高德 Key 白名单或代理配置'
    }
    citySearchInstance.getLocalCity(async (status, result) => {
      if (status === 'complete' && result?.city) {
        mapInstance.setCity(result.city)
        await setProvinceCityDistrict(result.province || result.city, result.city)
      }
    })

    mapInstance.on('click', (e) => {
      selectCoordinateFromMap(e.lnglat.lng, e.lnglat.lat)
    })
    mapReady.value = true
  } catch (error) {
    mapError.value = error?.message || '地图加载失败，请检查网络或 Key 配置'
  }
}

async function searchPoi() {
  if (!placeSearchInstance) {
    alert('地图尚未加载完成，请稍后重试')
    return
  }
  const keyword = locationKeyword.value.trim()
  if (!keyword) {
    searchResults.value = []
    return
  }

  searchingPoi.value = true
  try {
    const city = (form.city || form.province || '全国').trim() || '全国'
    const queryKeyword = form.district ? `${form.district}${keyword}` : keyword
    placeSearchInstance.setCity(city)
    placeSearchInstance.search(queryKeyword, (status, result) => {
      if (status !== 'complete' || !result?.poiList?.pois) {
        searchResults.value = []
        return
      }
      searchResults.value = result.poiList.pois
    })
  } finally {
    searchingPoi.value = false
  }
}

async function selectPoi(poi) {
  if (!poi?.location) return
  selectedPlaceId.value = null
  form.place = poi.name || ''
  form.address = poi.address || poi.name || ''
  await setProvinceCityDistrict(
    normalizeRegionName(poi.pname) || form.province,
    normalizeRegionName(poi.cityname) || normalizeRegionName(poi.pname),
    normalizeRegionName(poi.adname)
  )
  form.longitude = Number(poi.location.lng)
  form.latitude = Number(poi.location.lat)
  showPickedMarker(form.longitude, form.latitude)
}

function selectRecommendPlace(item) {
  selectedPlaceId.value = item.placeId || null
  form.place = item.name || ''
  form.address = item.address || ''
}

function selectCoordinateFromMap(lng, lat) {
  selectedPlaceId.value = null
  form.longitude = Number(lng.toFixed(6))
  form.latitude = Number(lat.toFixed(6))
  showPickedMarker(form.longitude, form.latitude)
  if (!geocoderInstance) return
  geocoderInstance.getAddress([form.longitude, form.latitude], async (status, result) => {
    if (status !== 'complete') return
    const component = result?.regeocode?.addressComponent
    await setProvinceCityDistrict(
      normalizeRegionName(component?.province) || form.province,
      normalizeRegionName(component?.city) || normalizeRegionName(component?.province),
      normalizeRegionName(component?.district)
    )
    form.address = result?.regeocode?.formattedAddress || form.address || ''
    if (!form.place) {
      form.place = component?.township || component?.street || form.address || '地图选点'
    }
  })
}

function showPickedMarker(lng, lat) {
  if (!mapInstance || !window.AMap || !Number.isFinite(lng) || !Number.isFinite(lat)) return
  if (pickedMarker) pickedMarker.setMap(null)
  pickedMarker = new window.AMap.Marker({
    position: [lng, lat],
    map: mapInstance,
    icon: 'https://webapi.amap.com/theme/v1.3/markers/n/mark_r.png'
  })
  mapInstance.setZoomAndCenter(14, [lng, lat])
}

async function generateActivityCopy() {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  if (!form.title.trim() || !activityBaseContent.value) {
    alert('请先填写活动标题和活动说明，再使用 AI 生成文案')
    return
  }

  generatingCopy.value = true
  try {
    const result = await api.activityDescription({
      title: form.title.trim(),
      activityTime: form.activityTime || '待定',
      location: [form.city, form.district, form.place || form.address].filter(Boolean).join(' '),
      place: form.place.trim(),
      address: form.address.trim(),
      maxNum: Number(form.maxNum) || 2,
      content: activityBaseContent.value
    })
    aiResult.value = {
      title: result?.title || '',
      description: result?.description || '',
      highlights: Array.isArray(result?.highlights) ? result.highlights : [],
      tips: Array.isArray(result?.tips) ? result.tips : [],
      suitableFor: result?.suitableFor || '',
      riskTips: Array.isArray(result?.riskTips) ? result.riskTips : []
    }
  } catch (e) {
    alert(e?.message || 'AI 生成文案失败，请稍后重试')
  } finally {
    generatingCopy.value = false
  }
}

function applyAiTitle() {
  if (aiResult.value?.title) {
    form.title = aiResult.value.title
  }
}

function applyAiDescription() {
  if (aiResult.value?.description) {
    form.content = aiResult.value.description
    form.activityDesc = aiResult.value.description
  }
}

function applyAiAll() {
  applyAiTitle()
  applyAiDescription()
}

async function publishActivity() {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  if (!form.title.trim()) {
    alert('请填写活动标题')
    return
  }
  if (!form.city) {
    alert('请选择城市')
    return
  }
  if (!form.activityTime) {
    alert('请选择活动时间')
    return
  }
  if (new Date(form.activityTime).getTime() < Date.now()) {
    alert('活动时间不能早于当前时间')
    return
  }
  if (!form.place.trim()) {
    alert('请先选择活动地点')
    return
  }
  if (!form.activityType.trim()) {
    alert('活动类型不能为空')
    return
  }

  const activityDesc = (form.activityDesc || form.content || '').trim()
  if (!activityDesc) {
    alert('活动说明不能为空')
    return
  }
  if (activityDesc.length < 10) {
    alert('活动说明不能过短（至少10个字）')
    return
  }

  const maxNum = Number(form.maxNum)
  if (!Number.isFinite(maxNum) || maxNum < 2 || maxNum > 6) {
    alert('人数上限需在 2 到 6 人之间')
    return
  }

  const payload = {
    title: form.title.trim(),
    content: activityDesc,
    activityDesc,
    activityType: form.activityType.trim(),
    placeId: selectedPlaceId.value,
    place: form.place.trim(),
    address: form.address?.trim() || null,
    city: form.city?.trim() || null,
    district: form.district?.trim() || null,
    longitude: form.longitude,
    latitude: form.latitude,
    activityTime: form.activityTime,
    maxNum
  }
  await api.createActivity(payload)
  alert('发布成功，活动已进入待审核')
  router.push('/activities')
}

watch(() => form.city, (city) => {
  if (city && mapInstance) {
    mapInstance.setCity(city)
  }
})

onMounted(async () => {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  places.value = await api.places()
  await initMap()
})

onBeforeUnmount(() => {
  if (pickedMarker) pickedMarker.setMap(null)
  if (mapInstance) {
    mapInstance.destroy()
    mapInstance = null
  }
})
</script>

<template>
  <div class="publish-shell">
    <AiLoadingOverlay
      :visible="generatingCopy"
      title="AI 正在生成活动文案"
      subtitle="正在根据活动时间、地点和说明，组织更自然的同城约板召集内容。"
      :steps="aiLoadingSteps"
    />

    <div class="card publish-card">
      <div class="section-head top-head">
        <div>
          <h2>发布约板活动</h2>
          <p class="muted">按省份→城市→区县选择，并支持地图点选自动回填。</p>
        </div>
        <div class="head-actions">
          <button
            type="button"
            class="btn-soft"
            :disabled="generatingCopy"
            @click="generateActivityCopy"
          >
            <AppIcon name="refresh" :size="15" />
            {{ generatingCopy ? 'AI 生成中...' : 'AI 生成文案' }}
          </button>
          <button class="btn-soft" @click="backToList">返回活动列表</button>
        </div>
      </div>

      <div class="publish-grid">
        <section class="left-form">
          <div class="form-grid two-col">
            <input v-model="form.title" placeholder="活动标题" />
            <input v-model="form.activityTime" type="datetime-local" />
          </div>

          <div class="form-grid two-col">
            <input v-model="form.activityType" placeholder="活动类型（如：街式 / 碗池 / 刷街）" />
            <input v-model.number="form.maxNum" type="number" min="2" max="6" placeholder="人数上限（2-6）" />
          </div>

          <div class="form-grid three-col">
            <select v-model="form.province" @change="onProvinceChange">
              <option value="">选择省份</option>
              <option v-for="item in provinceOptions" :key="item" :value="item">{{ item }}</option>
            </select>
            <select v-model="form.city" :disabled="!form.province" @change="onCityChange">
              <option value="">{{ form.province ? '选择城市' : '请先选择省份' }}</option>
              <option v-for="item in cityOptions" :key="item" :value="item">{{ item }}</option>
            </select>
            <select v-model="form.district" :disabled="!form.city">
              <option value="">{{ form.city ? '选择区县（可选）' : '请先选择城市' }}</option>
              <option v-for="item in districtOptions" :key="item" :value="item">{{ item }}</option>
            </select>
          </div>

          <div class="form-grid search-grid">
            <input v-model="locationKeyword" placeholder="搜索场地关键词（如：滑板公园）" />
            <button :disabled="searchingPoi" class="btn-soft" @click="searchPoi">
              <AppIcon name="search" :size="15" />
              {{ searchingPoi ? '搜索中...' : '搜索场地' }}
            </button>
          </div>

          <textarea v-model="form.content" placeholder="活动说明（难度、集合规则、费用等）" />
          <textarea v-model="form.activityDesc" placeholder="活动说明（必填，至少10字）" />

          <div v-if="aiResult" class="ai-panel">
            <div class="ai-panel-head">
              <div>
                <span class="ai-badge">AI 生成文案结果</span>
                <p class="muted">可先预览，再按需回填到当前活动表单。</p>
              </div>
              <button type="button" class="btn-primary ai-apply-all" @click="applyAiAll">应用全部内容</button>
            </div>

            <div class="ai-result-grid">
              <div class="ai-result-block">
                <div class="ai-result-title-row">
                  <h3>优化标题</h3>
                  <button type="button" class="btn-soft" @click="applyAiTitle">应用标题</button>
                </div>
                <p class="ai-result-text">{{ aiResult.title || '暂无结果' }}</p>
              </div>

              <div class="ai-result-block">
                <h3>适合人群</h3>
                <p class="ai-result-text">{{ aiResult.suitableFor || '暂无建议' }}</p>
              </div>

              <div class="ai-result-block ai-result-block-wide">
                <div class="ai-result-title-row">
                  <h3>活动介绍</h3>
                  <button type="button" class="btn-soft" @click="applyAiDescription">仅应用活动介绍</button>
                </div>
                <p class="ai-result-text ai-result-content">{{ aiResult.description || '暂无结果' }}</p>
              </div>

              <div class="ai-result-block">
                <h3>活动亮点</h3>
                <ul v-if="aiResult.highlights.length" class="result-list">
                  <li v-for="item in aiResult.highlights" :key="item">{{ item }}</li>
                </ul>
                <p v-else class="ai-result-text">暂无亮点建议</p>
              </div>

              <div class="ai-result-block">
                <h3>注意事项</h3>
                <ul v-if="aiResult.tips.length" class="result-list">
                  <li v-for="item in aiResult.tips" :key="item">{{ item }}</li>
                </ul>
                <p v-else class="ai-result-text">暂无注意事项</p>
              </div>

              <div class="ai-result-block ai-result-block-wide">
                <h3>风险提示</h3>
                <ul v-if="aiResult.riskTips.length" class="result-list">
                  <li v-for="item in aiResult.riskTips" :key="item">{{ item }}</li>
                </ul>
                <p v-else class="ai-result-text">未发现明显风险提示</p>
              </div>
            </div>
          </div>

          <div v-if="searchResults.length" class="poi-list">
            <div v-for="poi in searchResults" :key="poi.id" class="poi-item">
              <div>
                <strong>{{ poi.name }}</strong>
                <p class="muted">{{ [poi.pname, poi.cityname, poi.adname, poi.address].filter(Boolean).join(' ') }}</p>
              </div>
              <button class="btn-soft" @click="selectPoi(poi)">选用</button>
            </div>
          </div>

          <div class="form-grid two-col">
            <input v-model="form.place" placeholder="地点名（必填）" />
            <input v-model="form.address" placeholder="详细地址" />
          </div>

          <div class="form-grid three-col">
            <input v-model.number="form.longitude" type="number" step="0.000001" placeholder="经度" />
            <input v-model.number="form.latitude" type="number" step="0.000001" placeholder="纬度" />
            <input v-model="form.activityDesc" placeholder="活动说明摘要（可选）" />
          </div>

          <button class="btn-primary" @click="publishActivity">确认发布活动</button>
        </section>

        <aside>
          <div class="side-card">
            <h3>地图选点</h3>
            <p class="muted">高德 Web API（{{ amapKeyMask }}）</p>
            <p v-if="!mapReady && !mapError" class="muted">地图加载中...</p>
            <p v-if="mapError" class="error">{{ mapError }}</p>
            <div ref="mapRef" class="amap-container"></div>
            <p class="muted">可直接点击地图自动回填省市区、地址和经纬度。</p>
          </div>

          <div class="side-card">
            <h3>推荐场地快捷填入</h3>
            <div v-for="item in places.slice(0, 6)" :key="item.placeId" class="recommend-item">
              <div>
                <strong>{{ item.name }}</strong>
                <p class="muted">{{ item.address }}</p>
              </div>
              <button class="btn-soft" @click="selectRecommendPlace(item)">使用</button>
            </div>
          </div>
        </aside>
      </div>
    </div>
  </div>
</template>

<style scoped>
.publish-shell {
  padding-bottom: 6px;
}

.publish-card {
  display: grid;
  gap: 14px;
}

.top-head {
  margin-bottom: 0;
}

.head-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.publish-grid {
  display: grid;
  grid-template-columns: 1.3fr 1fr;
  gap: 16px;
}

.left-form {
  display: grid;
  gap: 10px;
}

.form-grid {
  display: grid;
  gap: 10px;
}

.two-col {
  grid-template-columns: 1fr 1fr;
}

.three-col {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.search-grid {
  grid-template-columns: 1fr auto;
}

.ai-panel {
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  padding: 14px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(247, 249, 255, 0.98));
  display: grid;
  gap: 14px;
}

.ai-panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.ai-badge {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.06);
  color: var(--text-soft);
  font-size: 13px;
  font-weight: 700;
}

.ai-apply-all {
  white-space: nowrap;
}

.ai-result-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.ai-result-block {
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  padding: 12px;
  display: grid;
  gap: 10px;
}

.ai-result-block-wide {
  grid-column: 1 / -1;
}

.ai-result-title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}

.ai-result-title-row h3,
.ai-result-block h3 {
  margin: 0;
  font-size: 15px;
}

.ai-result-text {
  margin: 0;
  color: var(--text);
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-result-content {
  min-height: 120px;
}

.result-list {
  margin: 0;
  padding-left: 18px;
  color: var(--text);
  display: grid;
  gap: 6px;
}

.poi-list {
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.poi-item {
  padding: 10px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border-bottom: 1px solid var(--line);
}

.poi-item strong,
.poi-item p {
  word-break: break-word;
}

.poi-item:last-child {
  border-bottom: 0;
}

.side-card {
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--surface-muted);
  padding: 12px;
  margin-bottom: 10px;
}

.side-card h3 {
  margin: 0 0 8px;
}

.amap-container {
  width: 100%;
  height: 320px;
  border: 1px solid var(--line);
  border-radius: 10px;
  overflow: hidden;
  background: #f1f5f9;
}

.recommend-item {
  border-bottom: 1px solid var(--line);
  padding: 9px 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.recommend-item:last-child {
  border-bottom: 0;
}

@media (max-width: 980px) {
  .publish-grid {
    grid-template-columns: 1fr;
  }

  .three-col,
  .ai-result-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 768px) {
  .form-grid :deep(input),
  .form-grid :deep(select),
  .form-grid :deep(button),
  textarea,
  .left-form > button {
    min-height: 42px;
  }

  textarea {
    min-height: 120px;
  }

  .two-col,
  .three-col,
  .search-grid,
  .ai-result-grid {
    grid-template-columns: 1fr;
  }

  .top-head,
  .ai-panel-head,
  .ai-result-title-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .amap-container {
    height: 260px;
  }

  .poi-item,
  .recommend-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .poi-item button,
  .recommend-item button,
  .head-actions button,
  .ai-apply-all {
    width: 100%;
  }
}
</style>
