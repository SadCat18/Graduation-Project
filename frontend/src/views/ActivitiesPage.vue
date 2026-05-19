<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { api } from '../api'
import { ACTIVITY_STATUS, ACTIVITY_STATUS_LABEL, SIGN_STATUS_LABEL, normalizeActivityStatus } from '../constants/activity'
import { getToken } from '../utils/auth'
import { amapKeyTail, loadAMap } from '../utils/amap'

const router = useRouter()

const list = ref([])
const places = ref([])
const loading = ref(false)
const loadError = ref('')
const mapReady = ref(false)
const mapError = ref('')
const selectedActivityId = ref(null)
const currentUserId = ref(null)
const managingActivityId = ref(null)
const signListLoading = ref(false)
const signListMap = ref({})

const mapRef = ref(null)
const provinceOptions = ref([])
const cityOptions = ref([])
const districtOptions = ref([])
const filter = reactive({
  province: '',
  city: '',
  district: '',
  keyword: ''
})

const isLoggedIn = computed(() => !!getToken())
const amapKeyMask = computed(() => amapKeyTail())

let mapInstance = null
let citySearchInstance = null
let activityMarkers = []

function buildQueryParams() {
  const params = { page: 1, size: 50 }
  if (filter.city.trim()) params.city = filter.city.trim()
  if (filter.district.trim()) params.district = filter.district.trim()
  if (filter.keyword.trim()) params.keyword = filter.keyword.trim()
  return params
}

function goPublish() {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  router.push('/activities/publish')
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

    const placeList = await api.places()
    list.value = activities.list || []
    places.value = placeList || []
    renderActivityMarkers()
  } catch (e) {
    loadError.value = e?.message || '活动加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function loadCurrentUser() {
  if (!isLoggedIn.value) {
    currentUserId.value = null
    return
  }
  try {
    const profile = await api.profile()
    currentUserId.value = profile?.userId || null
  } catch (_) {
    currentUserId.value = null
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

async function remove(item) {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  await api.deleteActivity(item.activityId)
  await loadData()
}

function isOwner(item) {
  return isLoggedIn.value && currentUserId.value && Number(item.userId) === Number(currentUserId.value)
}

async function toggleManageSigns(item) {
  if (!isOwner(item)) return
  if (managingActivityId.value === item.activityId) {
    managingActivityId.value = null
    return
  }
  managingActivityId.value = item.activityId
  await loadActivitySigns(item.activityId)
}

async function loadActivitySigns(activityId) {
  signListLoading.value = true
  try {
    const data = await api.activitySigns(activityId)
    signListMap.value = {
      ...signListMap.value,
      [activityId]: data || []
    }
  } finally {
    signListLoading.value = false
  }
}

function signStatusText(sign) {
  return SIGN_STATUS_LABEL[sign.signStatus] || '未知'
}

async function reviewSign(item, sign, signStatus) {
  await api.updateActivitySignStatus(item.activityId, sign.signId, { signStatus })
  await Promise.all([loadActivitySigns(item.activityId), loadData()])
}

async function closeSignup(item) {
  await api.closeSignupActivity(item.activityId)
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
  if (!mapRef.value) return

  try {
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
  }
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

function locateOnMap(item) {
  const lng = Number(item.longitude)
  const lat = Number(item.latitude)
  if (!mapInstance || !Number.isFinite(lng) || !Number.isFinite(lat)) return
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
  const status = normalizeActivityStatus(item.activityStatus ?? item.status)
  return ACTIVITY_STATUS_LABEL[status] || '未知状态'
}

function canSign(item) {
  const status = normalizeActivityStatus(item.activityStatus ?? item.status)
  return !item.signed && status === ACTIVITY_STATUS.SIGNUP_OPEN
}

function canCancelSign(item) {
  if (!item.signed) return false
  const status = normalizeActivityStatus(item.activityStatus ?? item.status)
  return status !== ACTIVITY_STATUS.CANCELED && status !== ACTIVITY_STATUS.ENDED
}

watch(() => filter.city, (city) => {
  if (city && mapInstance) {
    mapInstance.setCity(city)
  }
})

onMounted(async () => {
  await loadCurrentUser()
  await loadData()
  await initMap()
})

onBeforeUnmount(() => {
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
    <section class="left-pane">
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
          <div class="filter-group">
            <span class="group-title">地区</span>
            <select v-model="filter.province" @change="onProvinceChange">
              <option value="">选择省份</option>
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
          </div>
          <div class="filter-group keyword-group">
            <span class="group-title">关键词</span>
            <input v-model="filter.keyword" placeholder="关键词（地点/动作/活动名）" @keyup.enter="applyFilter" />
          </div>
          <div class="filter-group action-group">
            <span class="group-title">操作</span>
            <button class="btn-soft" @click="applyFilter">
              <AppIcon name="search" :size="15" />
              筛选
            </button>
            <button @click="clearFilter">重置</button>
          </div>
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
        >
          <div class="item-top">
            <h4>{{ item.title }}</h4>
            <span
              class="status-tag"
              :class="`status-${normalizeActivityStatus(item.activityStatus ?? item.status)}`"
            >
              {{ displayActivityStatus(item) }}
            </span>
          </div>
          <div class="meta-grid">
            <span class="meta-chip"><AppIcon name="notice" :size="14" /> 时间：{{ formatTime(item) }}</span>
            <span class="meta-chip"><AppIcon name="location" :size="14" /> 地点：{{ displayAddress(item) }}</span>
            <span class="meta-chip"><AppIcon name="user" :size="14" /> 人数：{{ item.signNum }}/{{ item.maxNum || '不限' }} · {{ item.publisherName }}</span>
          </div>
          <p class="content">{{ item.content || '暂无说明' }}</p>
          <div class="inline">
            <button v-if="canSign(item)" @click="sign(item)">
              报名
            </button>
            <button v-else class="btn-soft" :disabled="!canCancelSign(item)" @click="cancelSign(item)">
              退出报名
            </button>
            <button v-if="item.longitude && item.latitude" class="btn-soft" @click="locateOnMap(item)">
              <AppIcon name="location" :size="14" />
              地图定位
            </button>
            <button v-if="isOwner(item)" class="btn-soft" @click="toggleManageSigns(item)">
              {{ managingActivityId === item.activityId ? '收起报名管理' : '报名管理' }}
            </button>
            <button v-if="isOwner(item)" class="btn-soft" @click="closeSignup(item)">提前关闭报名</button>
            <button v-if="isOwner(item)" class="btn-danger" @click="remove(item)">取消活动</button>
          </div>

          <div v-if="managingActivityId === item.activityId" class="manage-box">
            <p class="muted">报名名单管理</p>
            <p v-if="signListLoading" class="muted">加载报名名单中...</p>
            <div v-for="sign in (signListMap[item.activityId] || [])" :key="sign.signId" class="sign-row">
              <span>#{{ sign.signId }} · 用户 {{ sign.userId }} · {{ signStatusText(sign) }}</span>
              <div class="inline">
                <button v-if="sign.signStatus === '0'" class="btn-soft" @click="reviewSign(item, sign, '1')">同意</button>
                <button v-if="sign.signStatus === '0'" class="btn-soft" @click="reviewSign(item, sign, '2')">拒绝</button>
                <button v-if="sign.signStatus === '1'" class="btn-soft" @click="reviewSign(item, sign, '3')">移除</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <aside class="right-pane">
      <div class="card map-card">
        <div class="section-head"><h3>活动地图</h3></div>
        <p class="muted">高德 Web API（{{ amapKeyMask }}）</p>
        <p v-if="!mapReady && !mapError" class="muted">地图加载中...</p>
        <p v-if="mapError" class="error">{{ mapError }}</p>
        <div ref="mapRef" class="amap-container"></div>
      </div>

      <div class="card place-card">
        <div class="section-head"><h3>推荐场地</h3></div>
        <p v-if="!places.length" class="muted place-empty">当前暂无推荐场地，稍后再来看看。</p>
        <div v-for="p in places.slice(0, 8)" :key="p.placeId" class="list-item">
          <strong>{{ p.name }}</strong>
          <p class="muted">{{ p.address }}</p>
          <p class="muted">评分：{{ p.score }}</p>
        </div>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.activity-page {
  align-items: start;
  grid-template-columns: minmax(0, 1.6fr) 360px;
  gap: var(--space-6);
}

.left-pane,
.right-pane {
  min-width: 0;
}

.list-card {
  display: grid;
  gap: var(--space-4);
  padding: var(--space-6);
}

.list-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
}

.list-toolbar h2 {
  margin: 0;
  font-size: 32px;
  line-height: 40px;
}

.list-toolbar .muted {
  margin: 6px 0 0;
}

.filter-row {
  display: grid;
  grid-template-columns: 2fr 1.2fr auto;
  gap: 10px;
}

.filter-group {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(110px, 1fr);
  gap: 8px;
  align-items: end;
  border: 2px solid var(--line-strong);
  background: linear-gradient(180deg, #ffffff 0%, #edf4ff 100%);
  border-radius: var(--radius-md);
  padding: 8px;
  position: relative;
}

.group-title {
  position: absolute;
  left: 8px;
  top: -10px;
  background: var(--bg-0);
  border: 2px solid var(--line-strong);
  border-radius: 4px;
  color: var(--text-3);
  font-size: 12px;
  line-height: 18px;
  padding: 0 6px;
}

.keyword-group {
  grid-auto-flow: row;
  grid-auto-columns: 1fr;
}

.action-group {
  grid-auto-columns: auto;
}

.filter-row :deep(select),
.filter-row :deep(input),
.filter-row :deep(button) {
  min-height: var(--h-md);
}

.activity-item {
  border: 2px solid var(--line-strong);
  border-radius: 2px;
  padding: 14px;
  background: linear-gradient(180deg, #ffffff 0%, #edf4ff 100%);
  box-shadow: var(--shadow-1);
  transition: transform var(--motion-base) var(--ease-ui), border-color var(--motion-base) var(--ease-ui);
}

.activity-item:hover {
  transform: translateY(-2px);
  border-color: var(--line-strong);
}

.activity-item.active {
  border-color: var(--brand);
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
  font-size: 20px;
  line-height: 1.35;
  color: var(--text-1);
}

.status-tag {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  border-radius: var(--radius-sm);
  padding: 0 10px;
  font-size: 12px;
  font-weight: 700;
  border: 1px solid var(--line-strong);
  box-shadow: var(--shadow-1);
}

.status-0,
.status-signup_open {
  color: #09151e;
  background: var(--brand);
  border-color: #68e4ff;
}

.status-1,
.status-full {
  color: #2a1d07;
  background: var(--warning);
  border-color: #ffcd68;
}

.status-2,
.status-3,
.status-ended,
.status-canceled {
  color: var(--text-2);
  background: #e4eefc;
  border-color: #8ba8ce;
}

.meta-grid {
  display: grid;
  gap: 6px;
  margin-top: 10px;
  color: var(--text-2);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.meta-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  border-radius: 0;
  background: transparent;
  min-height: 0;
  padding: 0;
  min-width: 0;
  color: var(--text-2);
}

.content {
  margin-top: 10px;
  color: var(--text-2);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.manage-box {
  margin-top: 10px;
  border-top: 1px dashed var(--line-strong);
  padding-top: 10px;
  display: grid;
  gap: 8px;
}

.sign-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  border: 2px solid var(--line-strong);
  border-radius: 2px;
  padding: 8px 10px;
  background: var(--bg-2);
}

.map-card {
  position: sticky;
  top: 74px;
}

.amap-container {
  width: 100%;
  height: 380px;
  border-radius: 2px;
  border: 2px solid var(--line-strong);
  overflow: hidden;
  background: #dfeaf8;
  box-shadow: var(--shadow-1);
}

.place-card {
  margin-top: 14px;
}

.place-empty {
  margin: 2px 0 8px;
  padding: 10px 12px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-sm);
  background: var(--bg-2);
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
    grid-template-columns: 1fr;
  }

  .list-toolbar {
    flex-direction: column;
  }

  .map-card {
    position: static;
  }

  .action-group {
    grid-auto-flow: row;
    grid-auto-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .filter-row {
    grid-template-columns: 1fr;
  }
}
</style>
