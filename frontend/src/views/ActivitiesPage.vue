<script setup>
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { api } from '../api'
import { ACTIVITY_STATUS_LABEL, normalizeActivityStatus } from '../constants/activity'
import { getToken } from '../utils/auth'
import { amapKeyTail, loadAMap } from '../utils/amap'
import { getCachedResource } from '../utils/requestCache'
import {
  ACTIVITY_EXPIRY_FILTER,
  canCancelSignActivity,
  canSignActivity,
  filterActivitiesByExpiry,
  getActivityStatusLabel
} from '../utils/activityExpiry'
import { buildActivityPlaceRecommendations } from '../utils/activityPlaceRecommendations'

defineOptions({ name: 'ActivitiesPage' })

const router = useRouter()

const list = ref([])
const places = ref([])
const loading = ref(false)
const loadError = ref('')
const mapRequested = ref(false)
const mapLoading = ref(false)
const mapReady = ref(false)
const mapError = ref('')
const selectedActivityId = ref(null)
const mobilePanel = ref('list')
const isCompact = ref(false)

const mapRef = ref(null)
const provinceOptions = ref([])
const cityOptions = ref([])
const districtOptions = ref([])
const filter = reactive({
  province: '',
  city: '',
  district: '',
  keyword: '',
  expiry: ACTIVITY_EXPIRY_FILTER.ACTIVE
})

const isLoggedIn = computed(() => !!getToken())
const amapKeyMask = computed(() => amapKeyTail())
const recommendedPlaces = computed(() => buildActivityPlaceRecommendations(places.value, list.value, 8))
const PLACE_CACHE_TTL = 5 * 60 * 1000

let mapInstance = null
let citySearchInstance = null
let activityMarkers = []

function onResize() {
  isCompact.value = window.innerWidth <= 768
}



function buildQueryParams() {
  const params = { page: 1, size: 50 }
  if (filter.city.trim()) params.city = filter.city.trim()
  if (filter.district.trim()) params.district = filter.district.trim()
  if (filter.keyword.trim()) params.keyword = filter.keyword.trim()
  params.expired = filter.expiry === ACTIVITY_EXPIRY_FILTER.EXPIRED
  return params
}

function goPublish() {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  router.push('/activities/publish')
}

function goPlaceDetail(placeId) {
  if (!placeId) return
  router.push(`/places/${placeId}`)
}

function goActivityDetail(activityId) {
  if (!activityId) return
  router.push(`/activities/${activityId}`)
}

async function filterByPlace(place) {
  if (!place?.name) return
  filter.keyword = place.name
  await loadData()
}

async function loadData() {
  loading.value = true
  loadError.value = ''
  try {
    const params = buildQueryParams()
    const placePromise = getCachedResource(
      'public:places',
      () => api.places(),
      PLACE_CACHE_TTL
    )
    let activities
    if (isLoggedIn.value) {
      try {
        activities = await api.myActivities(params)
      } catch (e) {
        if (e?.response?.status === 403 || e?.response?.status === 401) {
          activities = await api.publicActivities(params)
        } else {
          throw e
        }
      }
    } else {
      activities = await api.publicActivities(params)
    }

    const placeList = await placePromise
    list.value = filterActivitiesByExpiry(activities.list || [], filter.expiry)
    places.value = placeList || []
    renderActivityMarkers()
  } catch (e) {
    loadError.value = e?.message || '活动加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function applyFilter() {
  await loadData()
}

async function clearFilter() {
  filter.province = ''
  filter.city = ''
  filter.district = ''
  filter.keyword = ''
  filter.expiry = ACTIVITY_EXPIRY_FILTER.ACTIVE
  cityOptions.value = []
  districtOptions.value = []
  await loadData()
}

async function sign(item) {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  await api.signActivity(item.activityId)
  await loadData()
}

async function cancelSign(item) {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  await api.cancelSignActivity(item.activityId)
  await loadData()
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

async function loadProvinceOptions() {
  const districtList = await searchDistrict('中国', 'country', 1)
  const provinces = districtList?.[0]?.districtList || []
  provinceOptions.value = provinces.map(item => item.name).filter(Boolean)
}

async function loadCityOptions(provinceName) {
  if (!provinceName) {
    cityOptions.value = []
    districtOptions.value = []
    return
  }
  const districtList = await searchDistrict(provinceName, 'province', 1)
  const children = districtList?.[0]?.districtList || []

  let cities = children
    .map(item => item.name)
    .filter(Boolean)
    .filter(name => name !== '市辖区')

  if (!cities.length || children[0]?.level === 'district') {
    cities = [provinceName]
  }

  cityOptions.value = Array.from(new Set(cities))
  if (!cityOptions.value.includes(filter.city)) {
    filter.city = ''
  }
}

async function loadDistrictOptions(cityName) {
  if (!cityName) {
    districtOptions.value = []
    return
  }
  const districtList = await searchDistrict(cityName, 'city', 1)
  const children = districtList?.[0]?.districtList || []
  const districts = children
    .map(item => item.name)
    .filter(Boolean)
    .filter(name => name !== '市辖区')

  districtOptions.value = Array.from(new Set(districts))
  if (!districtOptions.value.includes(filter.district)) {
    filter.district = ''
  }
}

async function onProvinceChange() {
  filter.city = ''
  filter.district = ''
  districtOptions.value = []
  await loadCityOptions(filter.province)
}

async function onCityChange() {
  filter.district = ''
  await loadDistrictOptions(filter.city)
}

async function initMap() {
  await nextTick()
  if (!mapRef.value || mapInstance) return

  try {
    mapLoading.value = true
    mapError.value = ''
    const AMap = await loadAMap()
    mapInstance = new AMap.Map(mapRef.value, {
      zoom: 11,
      center: [116.397428, 39.90923],
      viewMode: '3D'
    })
    citySearchInstance = new AMap.CitySearch()
    citySearchInstance.getLocalCity((status, result) => {
      if (status === 'complete' && result?.city) {
        mapInstance.setCity(result.city)
      }
    })
    await loadProvinceOptions()
    mapReady.value = true
    renderActivityMarkers()
  } catch (error) {
    mapError.value = error?.message || '地图加载失败，请检查网络或高德 Key 配置'
  } finally {
    mapLoading.value = false
  }
}

async function requestMap() {
  mapRequested.value = true
  await initMap()
}

function renderActivityMarkers() {
  if (!mapInstance || !window.AMap) return

  activityMarkers.forEach(marker => marker.setMap(null))
  activityMarkers = []

  list.value.forEach((item) => {
    const lng = Number(item.longitude)
    const lat = Number(item.latitude)
    if (!Number.isFinite(lng) || !Number.isFinite(lat)) return

    const marker = new window.AMap.Marker({
      position: [lng, lat],
      title: item.title,
      map: mapInstance
    })
    marker.on('click', () => locateOnMap(item))
    activityMarkers.push(marker)
  })
}

async function locateOnMap(item) {
  const lng = Number(item.longitude)
  const lat = Number(item.latitude)
  if (!Number.isFinite(lng) || !Number.isFinite(lat)) return
  if (!mapInstance) {
    await requestMap()
  }
  if (!mapInstance) return
  selectedActivityId.value = item.activityId
  mapInstance.setZoomAndCenter(14, [lng, lat])
}

function displayAddress(item) {
  const values = [item.city, item.district, item.place, item.address].filter(Boolean)
  return values.join(' · ') || '未设置地点'
}

function formatTime(item) {
  if (!item.activityTime) return '未设置'
  return String(item.activityTime).replace('T', ' ')
}

function displayActivityStatus(item) {
  const expiryLabel = getActivityStatusLabel(item)
  if (expiryLabel) return expiryLabel
  const status = normalizeActivityStatus(item.activityStatus ?? item.status)
  return ACTIVITY_STATUS_LABEL[status] || '未知状态'
}

function activityStatusClass(item) {
  if (getActivityStatusLabel(item)) return 'expired'
  return normalizeActivityStatus(item.activityStatus ?? item.status)
}

function canSign(item) {
  return canSignActivity(item)
}

function canCancelSign(item) {
  return canCancelSignActivity(item)
}

watch(() => filter.city, (city) => {
  if (city && mapInstance) {
    mapInstance.setCity(city)
  }
})

watch(mobilePanel, async (next) => {
  if (next === 'map' && !mapRequested.value) {
    await requestMap()
  }
})

onMounted(async () => {
  onResize()
  window.addEventListener('resize', onResize)
  await loadData()
})
onActivated(() => {
  onResize()
  window.addEventListener('resize', onResize)
})
onDeactivated(() => {
  window.removeEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  activityMarkers.forEach(marker => marker.setMap(null))
  activityMarkers = []
  if (mapInstance) {
    mapInstance.destroy()
    mapInstance = null
  }
})
</script>

<template>
  <div class="page-grid activity-page">
    <div v-if="isCompact" class="mobile-switch">
      <button class="btn-soft" :class="{ active: mobilePanel === 'list' }" @click="mobilePanel = 'list'">活动列表</button>
      <button class="btn-soft" :class="{ active: mobilePanel === 'map' }" @click="mobilePanel = 'map'">地图与场地</button>
    </div>
    <section v-show="!isCompact || mobilePanel === 'list'" class="left-pane">
      <div class="card list-card">
        <div class="list-toolbar">
          <div>
            <h2>同城约板活动</h2>
            <p class="muted">进入页面即可浏览活动，筛选后可快速定位本地活动。</p>
          </div>
          <button class="btn-primary" @click="goPublish">
            <AppIcon name="plus" :size="15" />
            发布约板活动
          </button>
        </div>

        <div class="filter-row">
          <select v-model="filter.province" :disabled="!mapReady" @focus="requestMap" @change="onProvinceChange">
            <option value="">{{ mapReady ? '选择省份' : '加载地图后可选省份' }}</option>
            <option v-for="item in provinceOptions" :key="item" :value="item">{{ item }}</option>
          </select>
          <select v-model="filter.city" :disabled="!filter.province" @change="onCityChange">
            <option value="">{{ filter.province ? '选择城市' : '请先选择省份' }}</option>
            <option v-for="item in cityOptions" :key="item" :value="item">{{ item }}</option>
          </select>
          <select v-model="filter.district" :disabled="!filter.city">
            <option value="">{{ filter.city ? '选择区县（可选）' : '请先选择城市' }}</option>
            <option v-for="item in districtOptions" :key="item" :value="item">{{ item }}</option>
          </select>
          <input v-model="filter.keyword" placeholder="关键词（地点/动作/活动名）" @keyup.enter="applyFilter" />
          <select v-model="filter.expiry" @change="applyFilter">
            <option :value="ACTIVITY_EXPIRY_FILTER.ACTIVE">未过期活动</option>
            <option :value="ACTIVITY_EXPIRY_FILTER.EXPIRED">已过期活动</option>
          </select>
          <button class="btn-soft" @click="applyFilter">
            <AppIcon name="search" :size="15" />
            筛选
          </button>
          <button @click="clearFilter">重置</button>
        </div>

        <p class="muted" v-if="loading">正在加载活动...</p>
        <p v-if="loadError" class="error">{{ loadError }}</p>

        <div v-if="!loading && !loadError && !list.length" class="empty-state">
          <p>当前筛选条件下暂无活动，试试更换地区或关键词。</p>
          <button class="btn-primary" @click="goPublish">发布第一个活动</button>
        </div>

        <div
          v-for="item in list"
          :key="item.activityId"
          class="activity-item"
          :class="{ active: selectedActivityId === item.activityId }"
          @click="goActivityDetail(item.activityId)"
        >
          <div class="item-top">
            <h4>{{ item.title }}</h4>
            <span
              class="status-tag"
              :class="`status-${activityStatusClass(item)}`"
            >
              {{ displayActivityStatus(item) }}
            </span>
          </div>
          <div class="meta-grid">
            <span><AppIcon name="notice" :size="13" /> {{ formatTime(item) }}</span>
            <span><AppIcon name="location" :size="13" /> {{ displayAddress(item) }}</span>
            <span><AppIcon name="user" :size="13" /> {{ item.signNum }}/{{ item.maxNum || '不限' }} 人 · {{ item.publisherName }}</span>
          </div>
          <p class="content">{{ item.content || '暂无说明' }}</p>
          <div class="inline">
            <button v-if="canSign(item)" @click.stop="sign(item)">
              报名
            </button>
            <button v-else class="btn-soft" :disabled="!canCancelSign(item)" @click.stop="cancelSign(item)">
              退出报名
            </button>
            <button v-if="item.longitude && item.latitude" class="btn-soft" @click.stop="locateOnMap(item)">
              <AppIcon name="location" :size="14" />
              {{ mapReady ? '地图定位' : '加载地图定位' }}
            </button>
          </div>
        </div>
      </div>
    </section>

    <aside v-show="!isCompact || mobilePanel === 'map'" class="right-pane">
      <div class="card map-card">
        <div class="section-head"><h3>活动地图</h3></div>
        <p class="muted">高德 Web API（{{ amapKeyMask }}）</p>
        <div v-if="!mapRequested" class="map-lazy-panel">
          <AppIcon name="map" :size="28" />
          <strong>地图按需加载</strong>
          <p>先浏览活动列表；需要定位场地或选择省市区时再加载地图资源。</p>
          <button class="btn-primary" @click="requestMap">加载地图与地区筛选</button>
        </div>
        <p v-if="mapRequested && mapLoading && !mapReady && !mapError" class="muted">地图加载中...</p>
        <p v-if="mapError" class="error">{{ mapError }}</p>
        <div v-if="mapRequested" ref="mapRef" class="amap-container"></div>
      </div>

      <div class="card place-card">
        <div class="section-head"><h3>推荐场地</h3></div>
        <p v-if="!recommendedPlaces.length" class="muted place-empty">当前暂无推荐场地，稍后再来看看。</p>
        <div v-for="p in recommendedPlaces" :key="p.placeId" class="list-item place-recommend-item">
          <strong>{{ p.name }}</strong>
          <p class="muted">{{ p.address }}</p>
          <p class="muted">评分：{{ p.score }} · {{ p.reviewCount || 0 }} 条评价</p>
          <p v-if="p.hasActivityLink" class="muted place-link">
            关联 {{ p.relatedCount }} 个当前活动
            <template v-if="p.latestActivity"> · 最近：{{ p.latestActivity.title }}</template>
          </p>
          <div class="inline place-actions">
            <button v-if="p.hasActivityLink" class="btn-soft" @click="filterByPlace(p)">筛选活动</button>
            <button v-if="p.placeId" class="btn-soft" @click="goPlaceDetail(p.placeId)">查看评价</button>
          </div>
        </div>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.activity-page {
  align-items: start;
  grid-template-columns: minmax(0, 1.6fr) minmax(300px, 0.9fr);
  gap: 18px;
}

.left-pane,
.right-pane {
  min-width: 0;
}

.right-pane {
  position: relative;
}

.list-card {
  display: grid;
  gap: 16px;
  border-radius: 18px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
  padding: 18px;
}

.list-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
}

.list-toolbar h2 {
  margin: 0;
  font-size: 28px;
}

.list-toolbar .muted {
  margin: 6px 0 0;
}

.mobile-switch {
  display: none;
  gap: 8px;
}

.mobile-switch button {
  flex: 1;
  min-height: 42px;
}

.mobile-switch .active {
  background: var(--surface-muted);
  border-color: var(--text-soft);
}

.filter-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 1.35fr 1fr auto auto;
  gap: 10px;
}

.filter-row :deep(select),
.filter-row :deep(input),
.filter-row :deep(button) {
  min-height: 42px;
}

.activity-item {
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 14px 15px;
  background: #fff;
  cursor: pointer;
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.05);
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.activity-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.09);
}

.activity-item.active {
  border-color: rgba(59, 130, 246, 0.45);
  box-shadow: 0 0 0 2px rgba(148, 163, 184, 0.18);
}

.item-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.item-top h4,
.content {
  margin: 0;
}

.item-top h4 {
  font-size: 19px;
  line-height: 1.35;
  color: #0f172a;
  word-break: break-word;
}

.status-tag {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 3px 10px;
  font-size: 12px;
  font-weight: 700;
  border: 1px solid transparent;
}

.status-0,
.status-signup_open {
  color: #0369a1;
  background: #e0f2fe;
  border-color: #bae6fd;
}

.status-1,
.status-full {
  color: #92400e;
  background: #fef3c7;
  border-color: #fde68a;
}

.status-2,
.status-3,
.status-ended,
.status-canceled,
.status-expired {
  color: #64748b;
  background: #f1f5f9;
  border-color: #cbd5e1;
}

.meta-grid {
  display: grid;
  grid-template-columns: 1.15fr 1.5fr 1fr;
  gap: 8px 12px;
  margin-top: 8px;
  color: #475569;
  font-size: 13px;
}

.meta-grid span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  word-break: break-word;
}

.content {
  margin-top: 10px;
  color: #334155;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.manage-box {
  margin-top: 10px;
  border-top: 1px dashed var(--line);
  padding-top: 10px;
  display: grid;
  gap: 8px;
}

.sign-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 8px 10px;
  background: #fafafa;
}

.inline :deep(button) {
  min-height: 40px;
}

.map-card {
  position: sticky;
  top: 74px;
  z-index: 3;
  border-radius: 18px;
  box-shadow: 0 12px 26px rgba(15, 23, 42, 0.08);
}

.amap-container {
  width: 100%;
  height: 380px;
  border-radius: var(--radius-md);
  border: 1px solid var(--line);
  overflow: hidden;
  background: #f1f5f9;
}

.map-lazy-panel {
  min-height: 260px;
  border: 1px dashed #cbd5e1;
  border-radius: var(--radius-md);
  background: #f8fafc;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 10px;
  padding: 24px;
  text-align: center;
  color: #334155;
}

.map-lazy-panel strong {
  color: #111827;
  font-size: 18px;
}

.map-lazy-panel p {
  max-width: 320px;
  margin: 0;
  color: var(--text-muted);
  line-height: 1.5;
}

.place-card {
  margin-top: 14px;
  position: relative;
  z-index: 1;
  border-radius: 16px;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.06);
}

.place-empty {
  margin: 2px 0 8px;
  padding: 10px 12px;
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  background: #f8fafc;
}

.place-recommend-item {
  display: grid;
  gap: 6px;
}

.place-link {
  color: #334155;
}

.place-actions {
  margin-top: 2px;
}

.inline :deep(button) {
  transition: filter 0.18s ease, transform 0.18s ease;
}

.inline :deep(button:hover:not(:disabled)) {
  filter: brightness(1.04);
  transform: translateY(-1px);
}

@media (max-width: 980px) {
  .activity-page {
    grid-template-columns: 1fr;
  }

  .filter-row {
    grid-template-columns: 1fr 1fr;
  }

  .list-toolbar {
    flex-direction: column;
  }

  .map-card {
    position: static;
  }

  .meta-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .filter-row {
    grid-template-columns: 1fr;
  }

  .inline {
    flex-wrap: wrap;
  }

  .inline :deep(button) {
    flex: 1 1 calc(50% - 6px);
  }

  .sign-row {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 768px) {
  .mobile-switch {
    display: flex;
  }
}
</style>



