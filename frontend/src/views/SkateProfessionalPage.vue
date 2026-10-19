<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { api } from '../api'
import { getToken } from '../utils/auth'

defineOptions({ name: 'SkateProfessionalPage' })

const router = useRouter()
const activeTab = ref('tricks')
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const success = ref('')

const tricks = ref([])
const trickHistory = ref([])
const selectedTrickId = ref(null)
const plans = ref([])
const selectedPlanDetail = ref(null)
const currentTraining = ref(null)
const events = ref([])
const equipments = ref([])
const videos = ref([])

const checkinForm = reactive({
  practiceMinutes: 30,
  masterStatus: 'PRACTICING',
  content: '',
  mediaUrls: '',
  isPublic: '0'
})

const trainingForm = reactive({
  practiceMinutes: 30,
  content: '',
  mediaUrls: ''
})

const eventFilter = reactive({
  city: '',
  eventType: '',
  status: ''
})

const equipmentFilter = reactive({
  category: '',
  suitableLevel: ''
})

const tabs = [
  { key: 'tricks', label: '动作打卡', icon: 'target' },
  { key: 'training', label: '训练计划', icon: 'activity' },
  { key: 'events', label: '赛事活动', icon: 'trophy' },
  { key: 'equipment', label: '装备库', icon: 'box' }
]
const loadedModules = reactive({
  tricks: false,
  training: false,
  events: false,
  equipment: false
})

const trickChineseNames = {
  Ollie: '豚跳',
  Shuvit: '横转',
  Kickflip: '尖翻',
  'Hippy Jump': '人板分离跳',
  Manual: '后轮平衡',
  'Nose Manual': '前轮平衡',
  'Tic Tac': '摆板滑行',
  'No Comply 180': '单脚下地 180',
  Carving: '压弯',
  Powerslide: '甩尾刹停',
  'Fakie Shuvit': '倒滑横转',
  'Fakie Ollie': '倒滑豚跳',
  'Mini Ramp Pump': '小 U 池泵速',
  Nollie: '前脚豚跳',
  'Frontside 180': '正脚 180',
  'Backside 180': '背脚 180',
  'Pop Shuvit': '豚跳横转',
  Heelflip: '跟翻',
  'Varial Kickflip': '变向尖翻',
  '50-50 Grind': '双桥磨',
  Boardslide: '横板滑杆',
  '5-0 Grind': '后桥磨',
  'Drop In': '压板入池',
  'Rock to Fakie': '岩回倒滑',
  Pump: '泵速'
}

const selectedTrick = computed(() => tricks.value.find(item => item.trickId === selectedTrickId.value))
const currentTasks = computed(() => currentTraining.value?.tasks || [])
const currentRecords = computed(() => currentTraining.value?.records || [])
const completedTaskIds = computed(() => new Set(currentRecords.value.map(item => item.taskId)))
const todayTask = computed(() => currentTasks.value.find(item => !completedTaskIds.value.has(item.taskId)) || currentTasks.value[0])
const isLoggedIn = computed(() => !!getToken())
const learningStages = computed(() => buildLearningStages())

function splitTrickName(name) {
  const text = String(name || '').trim()
  const chineseStart = text.search(/[\u4e00-\u9fff]/)
  if (chineseStart === -1) {
    return { english: text, chinese: trickChineseNames[text] || '' }
  }
  return {
    english: text.slice(0, chineseStart).trim() || text,
    chinese: text.slice(chineseStart).trim()
  }
}

function trickEnglishName(name) {
  return splitTrickName(name).english
}

function trickChineseName(name) {
  return splitTrickName(name).chinese
}

function showSuccess(message) {
  success.value = message
  window.clearTimeout(showSuccess.timer)
  showSuccess.timer = window.setTimeout(() => {
    success.value = ''
  }, 2600)
}

function requireLogin() {
  if (isLoggedIn.value) return true
  router.push('/login')
  return false
}

function useEquipmentImageFallback(event) {
  const image = event.currentTarget
  image.onerror = null
  image.src = '/equipment/maple-deck-80.png'
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    await Promise.all([loadTricks(), loadVideos(), loadCurrentTraining()])
    loadedModules.tricks = true
  } catch (e) {
    error.value = e?.message || '滑板专业模块加载失败'
  } finally {
    loading.value = false
  }
}

async function activateTab(tabKey) {
  if (!tabs.some(tab => tab.key === tabKey)) return
  activeTab.value = tabKey
  if (loadedModules[tabKey]) return

  const loaders = {
    training: loadPlans,
    events: loadEvents,
    equipment: loadEquipments
  }
  const loader = loaders[tabKey]
  if (!loader) return

  loading.value = true
  error.value = ''
  try {
    await loader()
    loadedModules[tabKey] = true
  } catch (e) {
    error.value = e?.message || '模块加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function loadTricks() {
  tricks.value = await api.skateTricks()
  if (!selectedTrickId.value && tricks.value.length) {
    selectedTrickId.value = tricks.value[0].trickId
  }
  if (selectedTrickId.value && isLoggedIn.value) {
    await loadTrickHistory(selectedTrickId.value)
  }
}

async function loadTrickHistory(trickId) {
  if (!isLoggedIn.value) {
    trickHistory.value = []
    return
  }
  try {
    trickHistory.value = await api.skateTrickCheckins(trickId)
  } catch (_) {
    trickHistory.value = []
  }
}

async function chooseTrick(trickId) {
  selectedTrickId.value = trickId
  await loadTrickHistory(trickId)
}

async function submitCheckin() {
  if (!requireLogin() || !selectedTrickId.value) return
  actionLoading.value = true
  error.value = ''
  try {
    const result = await api.submitTrickCheckin(selectedTrickId.value, { ...checkinForm })
    checkinForm.content = ''
    checkinForm.mediaUrls = ''
    await loadTrickHistory(selectedTrickId.value)
    showSuccess(`打卡成功，经验 +${result?.expAwarded || 0}`)
  } catch (e) {
    error.value = e?.message || '动作打卡失败'
  } finally {
    actionLoading.value = false
  }
}

async function loadPlans() {
  plans.value = await api.skateTrainingPlans()
  if (!selectedPlanDetail.value && plans.value.length) {
    await showPlan(plans.value[0])
  }
}

async function showPlan(plan) {
  selectedPlanDetail.value = await api.skateTrainingPlanDetail(plan.planId)
}

async function loadCurrentTraining() {
  if (!isLoggedIn.value) {
    currentTraining.value = null
    return
  }
  try {
    const data = await api.currentTrainingPlan()
    currentTraining.value = data?.userPlan ? data : null
  } catch (_) {
    currentTraining.value = null
  }
}

async function joinPlan(planId) {
  if (!requireLogin()) return
  actionLoading.value = true
  error.value = ''
  try {
    await api.joinTrainingPlan(planId)
    trackProductEvent('START_TRAINING_PLAN', 'TRAINING_PLAN', planId)
    await loadCurrentTraining()
    showSuccess('已加入训练计划')
  } catch (e) {
    error.value = e?.message || '加入训练计划失败'
  } finally {
    actionLoading.value = false
  }
}

function trackProductEvent(eventType, targetType, targetId) {
  api.trackProductEvent({ eventType, targetType, targetId, sourcePage: 'skate-professional' }).catch(() => {})
}

async function completeTodayTask() {
  if (!requireLogin() || !todayTask.value) return
  actionLoading.value = true
  error.value = ''
  try {
    const result = await api.completeTrainingTask(todayTask.value.taskId, { ...trainingForm })
    trackProductEvent('COMPLETE_TRAINING_TASK', 'TRAINING_TASK', todayTask.value.taskId)
    trainingForm.content = ''
    trainingForm.mediaUrls = ''
    await loadCurrentTraining()
    const planText = result?.completedPlan ? `，完整计划经验 +${result?.planExpAwarded || 0}` : ''
    showSuccess(`训练完成，任务经验 +${result?.taskExpAwarded || 0}${planText}`)
  } catch (e) {
    error.value = e?.message || '训练任务提交失败'
  } finally {
    actionLoading.value = false
  }
}

async function loadEvents() {
  const params = { page: 1, size: 30 }
  if (eventFilter.city.trim()) params.city = eventFilter.city.trim()
  if (eventFilter.eventType) params.eventType = eventFilter.eventType
  if (eventFilter.status) params.status = eventFilter.status
  const data = await api.skateEvents(params)
  events.value = data?.list || []
}

async function signupEvent(item) {
  if (!requireLogin()) return
  actionLoading.value = true
  error.value = ''
  try {
    const result = await api.signupSkateEvent(item.event.eventId)
    await loadEvents()
    showSuccess(`赛事报名成功，经验 +${result?.expAwarded || 0}`)
  } catch (e) {
    error.value = e?.message || '赛事报名失败'
  } finally {
    actionLoading.value = false
  }
}

async function cancelEvent(item) {
  if (!requireLogin()) return
  actionLoading.value = true
  error.value = ''
  try {
    await api.cancelSkateEvent(item.event.eventId)
    await loadEvents()
    showSuccess('已取消赛事报名')
  } catch (e) {
    error.value = e?.message || '取消报名失败'
  } finally {
    actionLoading.value = false
  }
}

async function checkinEvent(item) {
  if (!requireLogin()) return
  actionLoading.value = true
  error.value = ''
  try {
    const result = await api.checkinSkateEvent(item.event.eventId)
    await loadEvents()
    showSuccess(`赛事签到成功，经验 +${result?.expAwarded || 0}`)
  } catch (e) {
    error.value = e?.message || '赛事签到失败'
  } finally {
    actionLoading.value = false
  }
}

async function loadEquipments() {
  const params = { page: 1, size: 30 }
  if (equipmentFilter.category) params.category = equipmentFilter.category
  if (equipmentFilter.suitableLevel) params.suitableLevel = equipmentFilter.suitableLevel
  const data = await api.skateEquipments(params)
  equipments.value = data?.list || []
}

async function loadVideos() {
  try {
    videos.value = await api.videos()
  } catch (_) {
    videos.value = []
  }
}

function buildLearningStages() {
  const configs = [
    { key: 'basics', title: 'Board basics', label: '上板基础', keywords: ['新手', '入门', '站姿', 'Regular', 'Goofy', 'push', '滑行'], task: '确认站姿，完成 20 分钟推行练习' },
    { key: 'balance', title: 'Balance and turning', label: '平衡与转弯', keywords: ['平衡', '转弯', 'carving', 'manual'], task: '练习重心转移和大弧线转向' },
    { key: 'braking', title: 'Braking', label: '刹车控制', keywords: ['刹车', '停止', 'brake', 'stop'], task: '在低速下完成 10 次稳定刹停' },
    { key: 'ollie', title: 'Ollie foundation', label: 'Ollie 基础', keywords: ['ollie', '豚跳', 'pop'], task: '拆分 Pop、刷脚和落地三个动作' },
    { key: 'street', title: 'First street tricks', label: '第一组街式动作', keywords: ['shove', 'kickflip', '街式', '台阶'], task: '选择一个低风险街式动作进入练习' }
  ]
  return configs.map((stage, index) => {
    const matchedTricks = tricks.value.filter(trick => matchesKeywords(`${trick.trickName || ''} ${trick.category || ''} ${trick.description || ''}`, stage.keywords)).slice(0, 3)
    const matchedVideos = videos.value.filter(video => matchesKeywords(`${video.title || ''} ${video.intro || ''}`, stage.keywords)).slice(0, 2)
    const matchedTasks = currentTasks.value.filter(task => matchesKeywords(`${task.taskTitle || ''} ${task.taskContent || ''}`, stage.keywords)).slice(0, 2)
    const completedCount = matchedTasks.filter(task => completedTaskIds.value.has(task.taskId)).length
    return {
      ...stage,
      order: index + 1,
      tricks: matchedTricks,
      videos: matchedVideos,
      tasks: matchedTasks,
      progress: matchedTasks.length ? Math.round((completedCount / matchedTasks.length) * 100) : 0
    }
  })
}

function matchesKeywords(text, keywords) {
  const normalized = String(text || '').toLowerCase()
  return keywords.some(keyword => normalized.includes(String(keyword).toLowerCase()))
}

function askCoachForStage(stage) {
  router.push({
    path: '/ai-coach',
    query: { q: `我正在学习${stage.label}，请给我一个适合当前阶段的滑板练习步骤和安全提醒。` }
  })
}

function openStageVideo(video) {
  if (!video?.videoId) return
  router.push({
    path: `/videos/${video.videoId}`,
    query: { from: 'skate' }
  })
}

async function toggleFavorite(item) {
  if (!requireLogin()) return
  actionLoading.value = true
  error.value = ''
  try {
    if (item.favorited) {
      await api.unfavoriteEquipment(item.equipment.equipmentId)
      showSuccess('已取消收藏')
    } else {
      const result = await api.favoriteEquipment(item.equipment.equipmentId)
      showSuccess(`收藏成功，经验 +${result?.expAwarded || 0}`)
    }
    await loadEquipments()
  } catch (e) {
    error.value = e?.message || '装备收藏失败'
  } finally {
    actionLoading.value = false
  }
}

function formatTime(value) {
  if (!value) return '待定'
  return String(value).replace('T', ' ').slice(0, 16)
}

function statusLabel(status) {
  return {
    NOT_STARTED: '未开始报名',
    SIGNUP_OPEN: '报名中',
    SIGNUP_CLOSED: '报名截止',
    ONGOING: '进行中',
    ENDED: '已结束'
  }[status] || status || '未知'
}

onMounted(loadAll)
</script>

<template>
  <section class="skate-page">
    <div class="skate-hero">
      <div>
        <p class="eyebrow">SkateHub Training Lab</p>
        <h1>滑板专业</h1>
        <p class="hero-copy">把动作练习、计划推进、赛事参与和装备选择放进同一个成长闭环。</p>
        <RouterLink to="/board-assistant">进入选板助手 · 预算、搭配检查与方案对比</RouterLink>
      </div>
      <div class="hero-stats">
        <span><strong>{{ tricks.length }}</strong> 动作</span>
        <span><strong>{{ loadedModules.training ? plans.length : '—' }}</strong> 计划</span>
        <span><strong>{{ loadedModules.events ? events.length : '—' }}</strong> 活动</span>
        <span><strong>{{ loadedModules.equipment ? equipments.length : '—' }}</strong> 装备</span>
      </div>
    </div>

    <section class="learning-path-panel">
      <div class="section-head">
        <div>
          <p class="eyebrow">Beginner Path</p>
          <h2>学习路径</h2>
          <p class="muted">按阶段连接动作、训练任务、教学视频和 AI Coach。</p>
        </div>
      </div>
      <div class="learning-stage-grid">
        <article v-for="stage in learningStages" :key="stage.key" class="learning-stage-card">
          <span class="stage-index">0{{ stage.order }}</span>
          <h3>{{ stage.label }}</h3>
          <p>{{ stage.task }}</p>
          <div class="stage-progress">
            <span><i :style="{ width: `${stage.progress}%` }"></i></span>
            <small>{{ stage.progress }}% 任务进度</small>
          </div>
          <div class="stage-links">
            <button v-for="trick in stage.tricks" :key="trick.trickId" class="btn-soft trick-name-chip" @click="chooseTrick(trick.trickId); activeTab = 'tricks'">
              <span>{{ trickEnglishName(trick.trickName) }}</span>
              <small v-if="trickChineseName(trick.trickName)">{{ trickChineseName(trick.trickName) }}</small>
            </button>
            <button v-for="video in stage.videos" :key="video.videoId" class="btn-soft" @click="openStageVideo(video)">{{ video.title }}</button>
            <span v-if="!stage.tricks.length && !stage.videos.length" class="muted">暂无匹配内容，可先按任务练习。</span>
          </div>
          <button class="btn-primary" @click="askCoachForStage(stage)">问 AI Coach</button>
        </article>
      </div>
    </section>

    <div class="tab-strip" role="tablist">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        :class="['tab-button', { active: activeTab === tab.key }]"
        @click="activateTab(tab.key)"
      >
        <AppIcon :name="tab.icon" :size="17" />
        {{ tab.label }}
      </button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="success" class="success">{{ success }}</p>
    <p v-if="loading" class="muted">正在加载滑板专业模块...</p>

    <div v-if="activeTab === 'tricks'" class="module-grid">
      <aside class="module-list">
        <button
          v-for="trick in tricks"
          :key="trick.trickId"
          type="button"
          :class="['list-row', { selected: trick.trickId === selectedTrickId }]"
          @click="chooseTrick(trick.trickId)"
        >
          <span class="trick-name-stack">
            <strong>{{ trickEnglishName(trick.trickName) }}</strong>
            <small v-if="trickChineseName(trick.trickName)">{{ trickChineseName(trick.trickName) }}</small>
          </span>
          <em>{{ trick.category }} · Lv.{{ trick.difficultyLevel }}</em>
        </button>
      </aside>

      <article class="module-panel">
        <img v-if="selectedTrick?.coverUrl" class="panel-image" :src="selectedTrick.coverUrl" alt="" loading="lazy" decoding="async">
        <h2>
          {{ trickEnglishName(selectedTrick?.trickName) || '选择一个动作' }}
          <small v-if="selectedTrick && trickChineseName(selectedTrick.trickName)" class="trick-heading-cn">{{ trickChineseName(selectedTrick.trickName) }}</small>
        </h2>
        <p class="muted">{{ selectedTrick?.description || '从左侧动作列表开始记录你的练习。' }}</p>

        <div class="form-grid">
          <label>练习时长
            <input v-model.number="checkinForm.practiceMinutes" type="number" min="1" max="600">
          </label>
          <label>掌握状态
            <select v-model="checkinForm.masterStatus">
              <option value="NOT_STARTED">未开始</option>
              <option value="PRACTICING">练习中</option>
              <option value="BASIC">基本掌握</option>
              <option value="SKILLED">熟练掌握</option>
            </select>
          </label>
          <label>是否公开
            <select v-model="checkinForm.isPublic">
              <option value="0">仅自己可见</option>
              <option value="1">公开到社区</option>
            </select>
          </label>
        </div>
        <label>练习心得
          <textarea v-model="checkinForm.content" placeholder="今天的落地、重心或失败点"></textarea>
        </label>
        <label>媒体地址
          <input v-model="checkinForm.mediaUrls" placeholder="/upload/xxx.mp4 或图片地址">
        </label>
        <button class="btn-primary" type="button" :disabled="actionLoading || !selectedTrickId" @click="submitCheckin">提交动作打卡</button>

        <div class="history-block">
          <h3>历史练习记录</h3>
          <p v-if="!isLoggedIn" class="muted">登录后可查看自己的动作历史。</p>
          <p v-else-if="!trickHistory.length" class="muted">还没有这个动作的打卡记录。</p>
          <div v-for="item in trickHistory.slice(0, 4)" :key="item.checkinId" class="timeline-item">
            <strong>{{ item.masterStatus }}</strong>
            <span>{{ item.practiceMinutes }} 分钟 · {{ formatTime(item.createTime) }}</span>
            <p>{{ item.content || '没有填写心得' }}</p>
          </div>
        </div>
      </article>
    </div>

    <div v-if="activeTab === 'training'" class="module-grid">
      <aside class="module-list">
        <button
          v-for="plan in plans"
          :key="plan.planId"
          type="button"
          :class="['list-row', { selected: selectedPlanDetail?.plan?.planId === plan.planId }]"
          @click="showPlan(plan)"
        >
          <span>{{ plan.planName }}</span>
          <em>{{ plan.durationDays }} 天 · Lv.{{ plan.difficultyLevel }}</em>
        </button>
      </aside>

      <article class="module-panel">
        <h2>{{ selectedPlanDetail?.plan?.planName || '推荐训练计划' }}</h2>
        <p class="muted">{{ selectedPlanDetail?.plan?.description || '选择一个计划查看每日任务。' }}</p>
        <button v-if="selectedPlanDetail?.plan" class="btn-primary" type="button" :disabled="actionLoading" @click="joinPlan(selectedPlanDetail.plan.planId)">加入训练计划</button>

        <div class="task-list">
          <div v-for="task in selectedPlanDetail?.tasks || []" :key="task.taskId" class="task-row">
            <b>Day {{ task.dayNo }}</b>
            <span>{{ task.taskTitle }}</span>
            <em>{{ task.targetMinutes }} 分钟</em>
          </div>
        </div>

        <div class="current-plan">
          <h3>今日训练</h3>
          <template v-if="currentTraining?.userPlan">
            <p class="muted">
              已完成 {{ currentTraining.userPlan.completedDays }} 天，当前状态 {{ currentTraining.userPlan.status }}
            </p>
            <div v-if="todayTask" class="today-card">
              <strong>{{ todayTask.taskTitle }}</strong>
              <p>{{ todayTask.taskContent }}</p>
              <div class="form-grid">
                <label>实际时长
                  <input v-model.number="trainingForm.practiceMinutes" type="number" min="1" max="600">
                </label>
                <label>媒体地址
                  <input v-model="trainingForm.mediaUrls" placeholder="训练图片或视频地址">
                </label>
              </div>
              <label>训练总结
                <textarea v-model="trainingForm.content" placeholder="今天完成了什么，哪里还需要练"></textarea>
              </label>
              <button class="btn-primary" type="button" :disabled="actionLoading" @click="completeTodayTask">完成今日任务</button>
            </div>
          </template>
          <p v-else class="muted">加入一个计划后，这里会显示今天要练什么。</p>
        </div>
      </article>
    </div>

    <div v-if="activeTab === 'events'" class="stack-panel">
      <div class="filter-bar">
        <input v-model="eventFilter.city" placeholder="城市">
        <select v-model="eventFilter.eventType">
          <option value="">全部类型</option>
          <option value="比赛">比赛</option>
          <option value="挑战赛">挑战赛</option>
          <option value="线下约板">线下约板</option>
        </select>
        <select v-model="eventFilter.status">
          <option value="">全部状态</option>
          <option value="SIGNUP_OPEN">报名中</option>
          <option value="SIGNUP_CLOSED">报名截止</option>
          <option value="ONGOING">进行中</option>
          <option value="ENDED">已结束</option>
        </select>
        <button type="button" @click="loadEvents">筛选赛事</button>
      </div>

      <div class="card-grid">
        <article v-for="item in events" :key="item.event.eventId" class="feature-card">
          <img v-if="item.event.coverUrl" :src="item.event.coverUrl" alt="" loading="lazy" decoding="async">
          <div>
            <span class="pill">{{ statusLabel(item.event.status) }}</span>
            <h3>{{ item.event.eventTitle }}</h3>
            <p>{{ item.event.city }} · {{ item.event.location }}</p>
            <p class="muted">{{ formatTime(item.event.startTime) }} · 已报名 {{ item.signedCount || 0 }}/{{ item.event.maxParticipants || '不限' }}</p>
            <div class="action-row">
              <button v-if="item.signupStatus === 'SIGNED'" type="button" @click="cancelEvent(item)">取消报名</button>
              <button v-else class="btn-primary" type="button" @click="signupEvent(item)">报名</button>
              <button type="button" @click="checkinEvent(item)">签到</button>
            </div>
          </div>
        </article>
      </div>
    </div>

    <div v-if="activeTab === 'equipment'" class="stack-panel">
      <div class="filter-bar">
        <select v-model="equipmentFilter.category">
          <option value="">全部分类</option>
          <option value="板面">板面</option>
          <option value="桥">桥</option>
          <option value="轮子">轮子</option>
          <option value="轴承">轴承</option>
          <option value="护具">护具</option>
          <option value="滑板鞋">滑板鞋</option>
        </select>
        <select v-model="equipmentFilter.suitableLevel">
          <option value="">全部水平</option>
          <option value="新手">新手</option>
          <option value="新手/进阶">新手/进阶</option>
          <option value="进阶">进阶</option>
        </select>
        <button type="button" @click="loadEquipments">筛选装备</button>
      </div>

      <div class="card-grid">
        <article v-for="item in equipments" :key="item.equipment.equipmentId" class="feature-card equipment-card">
          <img
            v-if="item.equipment.coverUrl"
            :src="item.equipment.coverUrl"
            alt=""
            loading="lazy"
            decoding="async"
            @error="useEquipmentImageFallback"
          >
          <div>
            <span class="pill">{{ item.equipment.category }} · {{ item.equipment.priceRange }}</span>
            <h3>{{ item.equipment.equipmentName }}</h3>
            <p>{{ item.equipment.description }}</p>
            <p class="muted">{{ item.equipment.specParams }}</p>
            <div class="action-row">
              <button type="button" :class="{ 'btn-primary': !item.favorited }" @click="toggleFavorite(item)">
                {{ item.favorited ? '取消收藏' : '收藏装备' }}
              </button>
              <a v-if="item.equipment.buyUrl" class="external-link" :href="item.equipment.buyUrl" target="_blank" rel="noopener noreferrer">外部链接</a>
            </div>
          </div>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.skate-page {
  display: grid;
  gap: 20px;
}

.skate-hero {
  min-height: 220px;
  padding: 30px;
  border: 1px solid rgba(18, 21, 19, 0.12);
  border-radius: 18px;
  background:
    linear-gradient(120deg, rgba(18, 21, 19, 0.88), rgba(38, 45, 40, 0.72)),
    url('https://images.pexels.com/photos/1984121/pexels-photo-1984121.jpeg?auto=compress&cs=tinysrgb&w=1800') center/cover;
  color: #fff;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 20px;
  overflow: hidden;
}

.eyebrow {
  margin: 0 0 8px;
  color: #b7d25a;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-size: 12px;
}

.skate-hero h1 {
  margin: 0;
  font-size: 42px;
  line-height: 1.05;
}

.hero-copy {
  max-width: 640px;
  margin: 12px 0 0;
  color: rgba(255, 255, 255, 0.82);
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(90px, 1fr));
  gap: 10px;
}

.hero-stats span {
  padding: 12px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.16);
}

.hero-stats strong {
  display: block;
  font-size: 24px;
}

.learning-path-panel {
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
}

.learning-stage-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.learning-stage-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 300px;
  padding: 16px;
  border: 1px solid rgba(148, 163, 184, 0.3);
  border-radius: 12px;
  background: #f8fafc;
}

.learning-stage-card h3,
.learning-stage-card p {
  margin: 0;
}

.learning-stage-card h3 {
  min-height: 58px;
  font-size: 20px;
  line-height: 1.35;
}

.learning-stage-card > p {
  min-height: 62px;
  color: var(--text);
  line-height: 1.55;
}

.learning-stage-card > .btn-primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 42px;
  margin-top: auto;
}

.stage-index {
  width: fit-content;
  padding: 3px 8px;
  border-radius: 999px;
  background: #151917;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
}

.stage-progress {
  display: grid;
  gap: 5px;
}

.stage-progress span {
  height: 7px;
  overflow: hidden;
  border-radius: 999px;
  background: #e2e8f0;
}

.stage-progress i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #b7d25a;
}

.stage-links {
  display: flex;
  flex-wrap: wrap;
  align-content: flex-start;
  gap: 6px;
  min-height: 80px;
  margin-bottom: 2px;
}

.stage-links .btn-soft {
  min-height: 30px;
  padding: 4px 8px;
  font-size: 12px;
}

.stage-links .trick-name-chip {
  display: inline-grid;
  gap: 2px;
  justify-items: start;
  min-height: 40px;
  line-height: 1.2;
  text-align: left;
}

.trick-name-chip small,
.trick-name-stack small,
.trick-heading-cn {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.2;
}

.tab-strip {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.tab-button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.tab-button.active {
  background: #151917;
  color: #fff;
}

.module-grid {
  display: grid;
  grid-template-columns: 290px minmax(0, 1fr);
  gap: 18px;
}

.module-list,
.module-panel,
.stack-panel {
  border: 1px solid var(--line);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.78);
  box-shadow: var(--shadow-sm);
}

.module-list {
  padding: 10px;
  display: grid;
  align-content: start;
  gap: 8px;
  contain: layout style;
}

.list-row {
  width: 100%;
  display: grid;
  gap: 3px;
  text-align: left;
  border-radius: 12px;
  background: #fff;
}

.trick-name-stack {
  display: grid;
  gap: 2px;
}

.trick-name-stack strong {
  font-size: 15px;
  line-height: 1.25;
}

.trick-heading-cn {
  display: block;
  margin-top: 4px;
  font-size: 14px;
}

.list-row.selected {
  border-color: rgba(8, 122, 109, 0.42);
  background: rgba(226, 248, 244, 0.9);
}

.list-row em,
.task-row em {
  color: var(--text-muted);
  font-size: 12px;
  font-style: normal;
}

.module-panel,
.stack-panel {
  padding: 18px;
}

.panel-image {
  width: 100%;
  height: 220px;
  object-fit: cover;
  border-radius: 14px;
  margin-bottom: 16px;
}

.form-grid,
.filter-bar {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 12px;
  align-items: end;
}

label {
  display: grid;
  gap: 6px;
  margin: 12px 0;
  color: var(--text-soft);
  font-size: 13px;
}

.history-block,
.current-plan {
  margin-top: 22px;
  padding-top: 16px;
  border-top: 1px solid var(--line);
}

.timeline-item,
.today-card,
.task-row {
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 12px;
  background: #fff;
  margin-top: 10px;
}

.timeline-item span {
  display: block;
  color: var(--text-muted);
  font-size: 12px;
}

.task-list {
  margin-top: 14px;
  display: grid;
  gap: 8px;
}

.task-row {
  display: grid;
  grid-template-columns: 70px 1fr auto;
  gap: 10px;
  align-items: center;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.feature-card {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
  box-shadow: var(--shadow-sm);
}

.feature-card img {
  width: 100%;
  height: 150px;
  object-fit: cover;
}

.equipment-card > img {
  background: #f5f2ea;
  object-fit: contain;
}

.feature-card > div {
  padding: 14px;
}

.pill {
  display: inline-flex;
  width: fit-content;
  padding: 4px 8px;
  border-radius: 999px;
  color: #065f46;
  background: rgba(209, 250, 229, 0.85);
  font-size: 12px;
}

.action-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
  margin-top: 12px;
}

.external-link {
  padding: 7px 12px;
  border-radius: 10px;
  border: 1px solid var(--line);
  background: #fff;
}

@media (max-width: 780px) {
  .skate-hero {
    grid-template-columns: 1fr;
    padding: 22px;
  }

  .skate-hero h1 {
    font-size: 34px;
  }

  .module-grid {
    grid-template-columns: 1fr;
  }

  .learning-stage-grid {
    grid-template-columns: 1fr;
  }

  .task-row {
    grid-template-columns: 1fr;
  }
}
</style>
