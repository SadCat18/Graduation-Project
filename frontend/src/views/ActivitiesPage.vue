<script setup>
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import SkateboardSketch from '../components/SkateboardSketch.vue'
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
import { regionOptions } from '../utils/regions'
import { reviewScoreText, reviewCountText, localReviewSummary } from '../utils/localReviews'
import { buildActivityPlaceRecommendations } from '../utils/activityPlaceRecommendations'

defineOptions({ name: 'ActivitiesPage' })

const router = useRouter()

const list = ref([])
const places = ref([])
const placesLoading = ref(false), placesError = ref('')
const loading = ref(false)
const loadError = ref('')
const mapRequested = ref(false)
const mapLoading = ref(false)
const mapReady = ref(false)
const mapError = ref('')
const selectedActivityId = ref(null)
const mobilePanel = ref('list')
const isCompact = ref(false)
const activityActionMessage = ref('')
const activityActionId = ref(null)

const mapRef = ref(null)
const provinceOptions = ref([])
const cityOptions = ref([])
const districtOptions = ref([])
const regionLoading = reactive({ province: false, city: false, district: false })
const regionError = reactive({ province: '', city: '', district: '' })
const regionReady = reactive({ province: false, city: false, district: false })
const regionSequence = { province: 0, city: 0, district: 0 }
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
const placeRecommendationSummary = computed(() => {
  const activeCount = recommendedPlaces.value.filter(item => item.hasActivityLink).length
  if (activeCount) return `${activeCount} 个场地有关联活动`
  return recommendedPlaces.value.length ? '按评分与评价热度推荐' : '等待更多场地数据'
})
const PLACE_CACHE_TTL = 5 * 60 * 1000

let mapInstance = null
let citySearchInstance = null
let activityMarkers = []
let actionMessageTimer = null

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

const placeScore = reviewScoreText
function placeScorePercent(place) {
  return `${(localReviewSummary(place)?.score ?? 0) * 20}%`
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

    list.value = filterActivitiesByExpiry(activities.list || [], filter.expiry)
    renderActivityMarkers()
  } catch (e) {
    loadError.value = e?.message || '活动加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function loadPlaces() {
  if (placesLoading.value) return
  placesLoading.value = true; placesError.value = ''
  try {
    const data = await getCachedResource('public:places', () => api.places(), PLACE_CACHE_TTL)
    if (!Array.isArray(data)) throw new Error('场地资料格式不可用')
    places.value = data
  } catch (e) { placesError.value = e?.message || '场地加载失败' }
  finally { placesLoading.value = false }
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
  resetRegion('city'); resetRegion('district')
  await loadData()
}

async function sign(item) {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  await api.signActivity(item.activityId)
  activityActionId.value = item.activityId
  activityActionMessage.value = `已报名「${item.title}」`
  window.clearTimeout(actionMessageTimer)
  actionMessageTimer = window.setTimeout(() => {
    activityActionMessage.value = ''
    activityActionId.value = null
  }, 2200)
  await loadData()
}

async function cancelSign(item) {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  await api.cancelSignActivity(item.activityId)
  activityActionId.value = item.activityId
  activityActionMessage.value = `已退出「${item.title}」报名`
  window.clearTimeout(actionMessageTimer)
  actionMessageTimer = window.setTimeout(() => {
    activityActionMessage.value = ''
    activityActionId.value = null
  }, 2200)
  await loadData()
}

function resetRegion(level) {
  regionSequence[level]++
  regionLoading[level] = false
  regionReady[level] = false
  regionError[level] = ''
  ;({ city: cityOptions, district: districtOptions })[level].value = []
}
async function loadRegion(level) {
  const sequence = ++regionSequence[level], province = filter.province, city = filter.city
  regionLoading[level] = true
  regionError[level] = ''
  regionReady[level] = false
  const current = () => sequence === regionSequence[level] && (level === 'province' || province === filter.province) && (level !== 'district' || city === filter.city)
  try {
    const options = await regionOptions(level, province, city)
    if (!current()) return
    ;({ province: provinceOptions, city: cityOptions, district: districtOptions })[level].value = options
    regionReady[level] = true
  } catch (e) {
    if (current()) regionError[level] = e?.message || '地区资料加载失败'
  } finally { if (current()) regionLoading[level] = false }
}
async function onProvinceChange() {
  filter.city = ''; filter.district = ''
  resetRegion('city'); resetRegion('district')
  if (filter.province) await loadRegion('city')
}
async function onCityChange() {
  filter.district = ''; resetRegion('district')
  if (filter.city) await loadRegion('district')
}

async function initMap() {
  await nextTick()
  if (!mapRef.value || mapInstance || mapLoading.value) return

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
      if (status === 'complete' && result?.city && mapInstance) {
        mapInstance.setCity(result.city)
      }
    })
    mapReady.value = true
    renderActivityMarkers()
  } catch (error) {
    mapInstance?.destroy()
    mapInstance = null
    citySearchInstance = null
    mapReady.value = false
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
  loadRegion('province')
  loadPlaces()
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
  Object.keys(regionSequence).forEach(level => regionSequence[level]++)
  window.removeEventListener('resize', onResize)
  window.clearTimeout(actionMessageTimer)
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
          <select v-model="filter.province" :disabled="!regionReady.province || regionLoading.province" aria-label="省份（用于选择城市）" @change="onProvinceChange">
            <option value="">省份（用于选择城市）</option>
            <option v-for="item in provinceOptions" :key="item" :value="item">{{ item }}</option>
          </select>
          <select v-model="filter.city" :disabled="!filter.province || !regionReady.city || regionLoading.city" aria-label="城市" @change="onCityChange">
            <option value="">{{ filter.province ? '选择城市' : '请先选择省份' }}</option>
            <option v-for="item in cityOptions" :key="item" :value="item">{{ item }}</option>
          </select>
          <select v-model="filter.district" :disabled="!filter.city || !regionReady.district || regionLoading.district" aria-label="区县">
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

        <p class="muted">省份仅用于选择城市；地区资料为固定版本，未覆盖的地区可使用关键词查找活动。</p>
        <div v-for="(label, level) in { province: '省份', city: '城市', district: '区县' }" :key="level">
          <p v-if="regionLoading[level]" role="status">{{ label }}资料加载中…</p>
          <p v-if="regionError[level]" role="alert">{{ label }}：{{ regionError[level] }} <button type="button" @click="loadRegion(level)">重试{{ label }}资料</button></p>
          <p v-if="regionReady[level] && !({ province: provinceOptions, city: cityOptions, district: districtOptions })[level].length">当前资料无下级{{ label }}选项。</p>
        </div>
        <div v-if="loading" class="skate-loading-state">
          <div class="skate-loading-board"><SkateboardSketch class="skate-loading-sketch" /></div>
          <p>正在加载同城约板活动...</p>
        </div>
        <p v-if="loadError" class="error">{{ loadError }}</p>
        <p v-if="activityActionMessage" class="success action-feedback">{{ activityActionMessage }}</p>

        <div v-if="!loading && !loadError && !list.length" class="empty-state">
          <p>当前筛选条件下暂无活动，试试更换地区或关键词。</p>
          <button class="btn-primary" @click="goPublish">发布第一个活动</button>
        </div>

        <div v-if="!loading && !loadError && list.length" class="motion-list activity-motion-list">
          <div
            v-for="(item, index) in list"
            :key="item.activityId"
            class="activity-item motion-card"
            :class="{ active: selectedActivityId === item.activityId }"
            :style="{ '--motion-index': index }"
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
              <button
                v-if="canSign(item)"
                :class="{ 'success-action': activityActionId === item.activityId }"
                @click.stop="sign(item)"
              >
                报名
              </button>
              <button
                v-else
                class="btn-soft"
                :class="{ 'success-action': activityActionId === item.activityId }"
                :disabled="!canCancelSign(item)"
                @click.stop="cancelSign(item)"
              >
                退出报名
              </button>
              <button v-if="item.longitude && item.latitude" class="btn-soft" @click.stop="locateOnMap(item)">
                <AppIcon name="location" :size="14" />
                {{ mapReady ? '地图定位' : '加载地图定位' }}
              </button>
            </div>
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
          <p>先浏览活动列表；需要定位场地时再加载地图资源，地区筛选独立可用。</p>
          <button class="btn-primary" @click="requestMap">加载地图</button>
        </div>
        <div v-if="mapRequested && mapLoading && !mapReady && !mapError" class="skate-loading-state map-loading-state">
          <div class="skate-loading-board"><SkateboardSketch class="skate-loading-sketch" /></div>
          <p>地图加载中...</p>
        </div>
        <p v-if="mapError" class="error">{{ mapError }} <button type="button" @click="requestMap">重试地图</button></p>
        <div v-if="mapRequested" ref="mapRef" class="amap-container"></div>
      </div>

      <div class="card place-card">
        <div class="section-head place-head">
          <div>
            <h3>推荐场地</h3>
            <p class="muted">按活动、评分和评价热度自动排序</p>
          </div>
          <span class="place-summary">{{ placeRecommendationSummary }}</span>
        </div>
        <p v-if="placesLoading" role="status">场地加载中…</p>
        <p v-if="placesError" role="alert">场地加载失败：{{ placesError }} <button type="button" @click="loadPlaces">重试场地</button></p>
        <p v-if="!placesLoading && !placesError && !recommendedPlaces.length" class="muted place-empty">当前暂无推荐场地，稍后再来看看。</p>
        <div class="motion-list place-motion-list">
          <div
            v-for="(p, index) in recommendedPlaces"
            :key="p.placeId"
            class="place-recommend-item motion-card"
            :style="{ '--motion-index': index }"
          >
            <span class="place-reason">{{ p.recommendReason }}</span>
            <div class="place-title-row">
              <strong>{{ p.name }}</strong>
              <span class="place-score">{{ placeScore(p) }}</span>
            </div>
            <p class="muted place-address">
              <AppIcon name="location" :size="13" />
              {{ p.address || '暂无地址' }}
            </p>
            <div class="place-score-row" aria-hidden="true">
              <span class="score-track"><i :style="{ width: placeScorePercent(p) }"></i></span>
              <span>{{ reviewCountText(p) }}</span>
            </div>
            <div class="place-tags">
              <span v-for="tag in p.placeTags" :key="`${p.placeId}-${tag}`">{{ tag }}</span>
            </div>
            <p v-if="p.hasActivityLink" class="place-link">
              关联 {{ p.relatedCount }} 个当前活动
              <template v-if="p.latestActivity"> · 最近：{{ p.latestActivity.title }}</template>
            </p>
            <div class="inline place-actions">
              <button v-if="p.hasActivityLink" class="btn-soft" @click="filterByPlace(p)">筛选活动</button>
              <button v-if="p.placeId" class="btn-soft" @click="goPlaceDetail(p.placeId)">查看场地</button>
            </div>
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
  position: sticky;
  top: 74px;
  max-height: calc(100vh - 90px);
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 2px;
  scrollbar-width: thin;
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
  overflow: hidden;
  border-radius: 18px;
  background:
    radial-gradient(circle at 0% 0%, rgba(20, 184, 166, 0.1), transparent 38%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.96));
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.08);
}

.place-head {
  align-items: flex-start;
}

.place-head .muted {
  margin: 4px 0 0;
  font-size: 13px;
}

.place-summary {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 4px 10px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: #334155;
  font-size: 12px;
  font-weight: 700;
}

.place-empty {
  margin: 2px 0 8px;
  padding: 10px 12px;
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  background: #f8fafc;
}

.place-recommend-item {
  position: relative;
  display: grid;
  gap: 8px;
  padding: 14px;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
}

.place-recommend-item::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: linear-gradient(180deg, #0f766e, #38bdf8);
  opacity: 0.72;
}

.place-reason {
  width: fit-content;
  padding: 4px 9px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.1);
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
}

.place-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.place-title-row strong {
  min-width: 0;
  line-height: 1.35;
}

.place-score {
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
  min-width: 42px;
  padding: 5px 8px;
  font-size: 13px;
  text-align: center;
  min-height: 34px;
  border-radius: 12px;
  background: #111827;
  color: #fff;
  font-weight: 900;
}

.place-address {
  display: inline-flex;
  align-items: flex-start;
  gap: 5px;
  margin: 0;
  line-height: 1.5;
}

.place-score-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  color: var(--text-muted);
  font-size: 12px;
}

.score-track {
  position: relative;
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.22);
}

.score-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #0f766e, #38bdf8);
}

.place-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.place-tags span {
  padding: 3px 8px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 999px;
  background: rgba(248, 250, 252, 0.82);
  color: #475569;
  font-size: 12px;
}

.place-link {
  margin: 0;
  color: #334155;
  line-height: 1.5;
}

.place-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 2px;
}

.place-actions :deep(button) {
  justify-content: center;
  min-height: 36px;
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
    gap: 10px;
  }

  .list-toolbar .btn-primary {
    width: 100%;
    min-height: 42px;
  }

  .right-pane {
    position: static;
    max-height: none;
    overflow: visible;
    padding-right: 0;
  }

  .map-card {
    position: static;
  }

  .meta-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .list-card {
    padding: 14px;
    gap: 12px;
  }

  .place-head,
  .place-title-row {
    align-items: stretch;
    flex-direction: column;
  }

  .place-summary,
  .place-score {
    width: fit-content;
  }

  .place-actions {
    grid-template-columns: 1fr;
  }

  .list-toolbar h2 {
    font-size: 24px;
  }

  .filter-row {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .activity-item {
    padding: 12px;
    border-radius: 14px;
  }

  .item-top {
    align-items: flex-start;
  }

  .item-top h4 {
    font-size: 17px;
  }

  .status-tag {
    flex: 0 0 auto;
  }

  .meta-grid {
    gap: 6px;
    font-size: 12px;
  }

  .content {
    -webkit-line-clamp: 3;
  }

  .inline {
    flex-wrap: wrap;
  }

  .inline :deep(button) {
    flex: 1 1 calc(50% - 6px);
    min-height: 42px;
  }

  .sign-row {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 768px) {
  .mobile-switch {
    display: flex;
    position: sticky;
    top: 8px;
    z-index: 6;
    padding: 6px;
    border: 1px solid rgba(226, 232, 240, 0.9);
    border-radius: 14px;
    background: rgba(255, 255, 255, 0.86);
    backdrop-filter: blur(12px);
  }
}

@media (max-width: 420px) {
  .inline :deep(button) {
    flex: 1 1 100%;
  }

  .item-top {
    display: grid;
  }
}
</style>



