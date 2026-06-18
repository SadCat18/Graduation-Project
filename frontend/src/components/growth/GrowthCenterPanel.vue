<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { api } from '../../api'
import { calculateLevelProgress, createLatestRequestGate, findNewlyUnlocked } from '../../utils/growthCenter'
import { normalizeMediaUrl } from '../../utils/url'
import AchievementBadge from './AchievementBadge.vue'
import BadgeUnlockModal from './BadgeUnlockModal.vue'

const props = defineProps({
  profile: { type: Object, required: true }
})
const emit = defineEmits(['experience-changed'])

const summaryLoading = ref(false)
const summaryError = ref('')
const leaderboardLoading = ref(false)
const leaderboardError = ref('')
const checkinPending = ref(false)
const checkinError = ref('')
const checkinMessage = ref('')
const status = ref({ checkedInToday: false, streakDays: 0, totalDays: 0 })
const calendar = ref({ month: '', checkedDates: [] })
const badgeList = ref([])
const unlockQueue = ref([])
const checkinTrigger = ref(null)
const primaryCheckinButton = ref(null)
const leaderboard = ref({ total: 0, list: [], currentUser: null })
const boardType = ref('total')
const page = ref(1)
const size = 20
const summaryGate = createLatestRequestGate()
const leaderboardGate = createLatestRequestGate()
let disposed = false

function shanghaiParts() {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit'
  }).formatToParts(new Date())
  return Object.fromEntries(parts.map(part => [part.type, part.value]))
}

const shanghaiToday = shanghaiParts()
const currentMonth = ref(`${shanghaiToday.year}-${shanghaiToday.month}`)
const todayKey = `${currentMonth.value}-${shanghaiToday.day}`
const weekdayLabels = ['日', '一', '二', '三', '四', '五', '六']

const calendarTitle = computed(() => {
  const [year, month] = currentMonth.value.split('-').map(Number)
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long' }).format(new Date(year, month - 1, 1))
})

const calendarCells = computed(() => {
  const [year, month] = currentMonth.value.split('-').map(Number)
  const leading = new Date(year, month - 1, 1).getDay()
  const days = new Date(year, month, 0).getDate()
  const checkedDates = new Set(calendar.value?.checkedDates || [])
  const cells = Array.from({ length: leading }, (_, index) => ({ key: `blank-${index}`, blank: true }))
  for (let day = 1; day <= days; day += 1) {
    const date = `${currentMonth.value}-${String(day).padStart(2, '0')}`
    cells.push({ key: date, day, checked: checkedDates.has(date), today: date === todayKey })
  }
  return cells
})

const remain = computed(() => Number(props.profile.remain ?? props.profile.remainToNextLevel ?? 0))
const nextLevelNeedExp = computed(() => Number(props.profile.nextLevelNeedExp || 0))
const isMaxLevel = computed(() => Number(props.profile.level) >= 10 || nextLevelNeedExp.value <= 0)
const levelProgress = computed(() => calculateLevelProgress({
  level: props.profile.level,
  exp: props.profile.exp,
  nextLevelNeedExp: props.profile.nextLevelNeedExp
}))

const currentLeaderboardUser = computed(() => leaderboard.value?.currentUser || null)
const currentUnlock = computed(() => unlockQueue.value[0] || null)
const totalPages = computed(() => Math.max(1, Math.ceil(Number(leaderboard.value?.total || 0) / size)))
const scoreLabel = computed(() => boardType.value === 'weekly' ? '本周经验' : '总经验')

function errorMessage(error, fallback) {
  return error?.message || fallback
}

function initials(username) {
  return String(username || '板').trim().slice(0, 1).toUpperCase()
}

function isCurrentUser(entry) {
  const knownUserId = props.profile?.userId ?? currentLeaderboardUser.value?.userId
  return knownUserId != null && entry?.userId != null && Number(entry.userId) === Number(knownUserId)
}

function rarityLabel(rarity) {
  return ({ COMMON: '普通', RARE: '稀有', EPIC: '史诗', LEGENDARY: '传说' })[rarity] || rarity || '普通'
}

async function loadSummary() {
  if (disposed) return
  const requestToken = summaryGate.begin()
  const requestedMonth = currentMonth.value
  summaryLoading.value = true
  summaryError.value = ''
  try {
    const [statusData, calendarData, badgesData] = await Promise.all([
      api.checkinStatus(), api.checkinCalendar(requestedMonth), api.badges()
    ])
    if (!summaryGate.isCurrent(requestToken)) return
    status.value = statusData || status.value
    calendar.value = calendarData || { month: requestedMonth, checkedDates: [] }
    badgeList.value = Array.isArray(badgesData) ? badgesData : []
    return { status: statusData, calendar: calendarData, badges: badgeList.value }
  } catch (error) {
    if (summaryGate.isCurrent(requestToken)) {
      summaryError.value = errorMessage(error, '成长记录加载失败，请重试。')
    }
  } finally {
    if (summaryGate.isCurrent(requestToken)) summaryLoading.value = false
  }
}

async function loadLeaderboard() {
  if (disposed) return
  const requestToken = leaderboardGate.begin()
  const requestedType = boardType.value
  const requestedPage = page.value
  leaderboardLoading.value = true
  leaderboardError.value = ''
  leaderboard.value = { total: 0, list: [], currentUser: null }
  try {
    const data = await api.growthLeaderboard({ type: requestedType, page: requestedPage, size: size })
    if (!leaderboardGate.isCurrent(requestToken)) return
    leaderboard.value = data || {
      total: 0, list: [], currentUser: null
    }
  } catch (error) {
    if (leaderboardGate.isCurrent(requestToken)) {
      leaderboardError.value = errorMessage(error, '排行榜暂时掉线，请重试。')
    }
  } finally {
    if (leaderboardGate.isCurrent(requestToken)) leaderboardLoading.value = false
  }
}

async function checkin(event) {
  if (checkinPending.value || status.value.checkedInToday) return
  checkinTrigger.value = event?.currentTarget instanceof HTMLElement ? event.currentTarget : null
  checkinPending.value = true
  checkinError.value = ''
  checkinMessage.value = ''
  summaryGate.invalidate()
  const badgesBeforeCheckin = badgeList.value
  try {
    const result = await api.dailyCheckin()
    if (disposed) return
    if (result?.alreadyCheckedIn) {
      checkinMessage.value = '今天已经签到，明天再来续上连签。'
    } else {
      checkinMessage.value = `签到成功：基础经验 +${Number(result?.baseExp || 0)}，连续奖励 +${Number(result?.bonusExp || 0)}`
      emit('experience-changed')
    }
    status.value.checkedInToday = true
    const [summaryData] = await Promise.all([loadSummary(), loadLeaderboard()])
    const newlyUnlocked = findNewlyUnlocked(badgesBeforeCheckin, summaryData?.badges)
    if (newlyUnlocked.length) unlockQueue.value.push(...newlyUnlocked)
  } catch (error) {
    checkinError.value = errorMessage(error, '签到失败，请稍后重试。')
    await loadSummary()
  } finally {
    checkinPending.value = false
    if (!unlockQueue.value.length) checkinTrigger.value = null
  }
}

function closeCurrentUnlock() {
  unlockQueue.value.shift()
  if (!unlockQueue.value.length) {
    nextTick(() => { checkinTrigger.value = null })
  }
}

function switchBoard(type) {
  if (boardType.value === type) return
  boardType.value = type
  page.value = 1
  loadLeaderboard()
}

function changePage(nextPage) {
  page.value = nextPage
  loadLeaderboard()
}

onMounted(() => {
  loadSummary()
  loadLeaderboard()
})

onBeforeUnmount(() => {
  disposed = true
  checkinTrigger.value = null
  summaryGate.invalidate()
  leaderboardGate.invalidate()
})
</script>

<template>
  <section class="growth-center" aria-labelledby="growth-title">
    <header class="growth-header">
      <div>
        <p class="eyebrow">KEEP ROLLING / 成长档案</p>
        <h2 id="growth-title">成长中心</h2>
        <p>每一次签到与活跃，都是下一段滑行的助推。</p>
      </div>
      <span class="deck-stamp">LEVEL {{ profile.level || 1 }}</span>
    </header>

    <div class="top-grid">
      <article class="panel experience-card">
        <p class="panel-kicker">EXPERIENCE TRACK</p>
        <div class="level-line">
          <strong>LV.{{ profile.level || 1 }}</strong>
          <span>{{ Number(profile.exp || 0) }} EXP</span>
        </div>
        <div
          class="progress-track"
          role="progressbar"
          aria-label="当前等级经验进度"
          :aria-valuenow="levelProgress"
          aria-valuemin="0"
          aria-valuemax="100"
        >
          <span :style="{ width: `${levelProgress}%` }"></span>
        </div>
        <p class="progress-copy">
          <template v-if="isMaxLevel">已抵达当前最高等级</template>
          <template v-else>距离下一级还需 <strong>{{ remain }}</strong> 经验</template>
        </p>
      </article>

      <article class="panel checkin-card">
        <div class="panel-heading">
          <div><p class="panel-kicker">DAILY PUSH</p><h3>每日签到</h3></div>
          <span class="streak">{{ status.streakDays || 0 }} 天连签</span>
        </div>
        <p>累计签到 {{ status.totalDays || 0 }} 天</p>
        <button
          ref="primaryCheckinButton"
          class="checkin-button"
          :disabled="checkinPending"
          :aria-disabled="status.checkedInToday ? 'true' : undefined"
          @click="checkin"
        >
          {{ checkinPending ? '签到中…' : status.checkedInToday ? '今日已签到' : '踩下今日一板' }}
        </button>
        <p v-if="checkinMessage" class="inline-message success" role="status">{{ checkinMessage }}</p>
        <div v-if="checkinError" class="inline-message error" role="alert">
          <span>{{ checkinError }}</span><button @click="checkin">重试签到</button>
        </div>
      </article>
    </div>

    <div v-if="summaryLoading" class="panel state-box" aria-live="polite">正在铺开成长记录…</div>
    <div v-else-if="summaryError" class="panel state-box error" role="alert">
      <span>{{ summaryError }}</span><button @click="loadSummary">重试加载</button>
    </div>

    <div v-else class="middle-grid">
      <article class="panel calendar-card">
        <div class="panel-heading">
          <div><p class="panel-kicker">ROLL CALL</p><h3>签到轨迹</h3></div>
          <strong>{{ calendarTitle }}</strong>
        </div>
        <div class="calendar-grid calendar-weekdays" aria-hidden="true">
          <span v-for="label in weekdayLabels" :key="label">{{ label }}</span>
        </div>
        <div class="calendar-grid" aria-label="本月签到日历">
          <span
            v-for="cell in calendarCells"
            :key="cell.key"
            class="calendar-day"
            :class="{ blank: cell.blank, checked: cell.checked, today: cell.today }"
            :aria-label="cell.blank ? null : `${cell.day}日${cell.checked ? '，已签到' : ''}${cell.today ? '，今天' : ''}`"
          >{{ cell.blank ? '' : cell.day }}</span>
        </div>
      </article>

      <article class="panel badges-card">
        <div class="panel-heading">
          <div><p class="panel-kicker">BADGE WALL</p><h3>徽章墙</h3></div>
          <span>{{ badgeList.filter(item => item.unlocked).length }}/{{ badgeList.length }} 已解锁</span>
        </div>
        <div v-if="!badgeList.length" class="empty-state">暂无徽章，保持滑行，第一枚很快就来。</div>
        <ul v-else class="badge-grid">
          <li v-for="badge in badgeList" :key="badge.code" class="badge" :class="{ locked: !badge.unlocked }">
            <AchievementBadge :badge="badge" :size="58" compact />
            <div>
              <div class="badge-title"><strong>{{ badge.name }}</strong><span>{{ rarityLabel(badge.rarity) }}</span></div>
              <p>{{ badge.description }}</p>
              <small>{{ badge.current || 0 }} / {{ badge.target || 0 }} · {{ badge.unlocked ? '已解锁' : '未解锁' }}</small>
            </div>
          </li>
        </ul>
      </article>
    </div>

    <article class="panel leaderboard-card">
      <div class="leaderboard-head">
        <div><p class="panel-kicker">SPOT RANKING</p><h3>成长排行榜</h3></div>
        <div class="board-switch" aria-label="排行榜类型">
          <button :class="{ active: boardType === 'total' }" :aria-pressed="boardType === 'total'" @click="switchBoard('total')">总榜</button>
          <button :class="{ active: boardType === 'weekly' }" :aria-pressed="boardType === 'weekly'" @click="switchBoard('weekly')">周榜</button>
        </div>
      </div>

      <div v-if="currentLeaderboardUser" class="current-rank">
        <span>我的位置</span><strong>#{{ currentLeaderboardUser.rankNo }}</strong>
        <span>LV.{{ currentLeaderboardUser.level }} · {{ scoreLabel }} {{ currentLeaderboardUser.score }}</span>
      </div>
      <div v-if="leaderboardLoading" class="state-box" aria-live="polite">正在刷新排名…</div>
      <div v-else-if="leaderboardError" class="state-box error" role="alert">
        <span>{{ leaderboardError }}</span><button @click="loadLeaderboard">重试排行榜</button>
      </div>
      <div v-else-if="!leaderboard.list?.length" class="empty-state">暂无排行数据，完成签到抢下第一个板位。</div>
      <ol v-else class="rank-list">
        <li v-for="entry in leaderboard.list" :key="entry.userId || `${entry.rankNo}-${entry.username}`" :class="{ me: isCurrentUser(entry) }">
          <strong class="rank-number">{{ entry.rankNo }}</strong>
          <span class="rank-avatar">
            <img v-if="entry.avatar" :src="normalizeMediaUrl(entry.avatar)" :alt="`${entry.username}的头像`" />
            <span v-else aria-hidden="true">{{ initials(entry.username) }}</span>
          </span>
          <span class="rider"><strong>{{ entry.username || '匿名滑手' }}</strong><small>LV.{{ entry.level || 1 }}</small></span>
          <span class="score"><strong>{{ entry.score || 0 }}</strong><small>{{ scoreLabel }}</small></span>
        </li>
      </ol>
      <div v-if="!leaderboardLoading && !leaderboardError && leaderboard.total > size" class="pagination">
        <button :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
        <span>{{ page }} / {{ totalPages }}</span>
        <button :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
      </div>
    </article>
    <BadgeUnlockModal
      v-if="currentUnlock"
      :key="currentUnlock.code"
      :badge="currentUnlock"
      :return-focus-to="checkinTrigger"
      :fallback-focus-to="primaryCheckinButton"
      @close="closeCurrentUnlock"
    />
  </section>
</template>

<style scoped>
.growth-center {
  --paper: #f3eddd;
  --ink: #161a1d;
  --yellow: #f4cf35;
  --cyan: #35c9cf;
  --muted: #68665f;
  display: grid;
  gap: 14px;
  min-width: 0;
  color: var(--ink);
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
}

.growth-header,
.panel {
  border: 1px solid rgba(22, 26, 29, 0.22);
  border-radius: 14px;
  background-color: var(--paper);
  background-image: repeating-linear-gradient(115deg, transparent 0 24px, rgba(22, 26, 29, 0.025) 25px 26px);
  box-shadow: 4px 5px 0 rgba(22, 26, 29, 0.1);
}

.growth-header {
  position: relative;
  overflow: hidden;
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: center;
  padding: 22px 24px;
  color: #fff;
  background-color: var(--ink);
  background-image: repeating-linear-gradient(105deg, transparent 0 34px, rgba(255,255,255,.045) 35px 36px);
}

.growth-header::after { content: ""; position: absolute; width: 180px; height: 14px; right: -28px; bottom: 10px; background: var(--yellow); transform: rotate(-7deg); }
.growth-header h2 { margin: 2px 0 4px; font: 900 clamp(28px, 5vw, 45px)/1 "Arial Black", "Microsoft YaHei", sans-serif; letter-spacing: -1px; }
.growth-header p { margin: 0; color: rgba(255,255,255,.72); }
.eyebrow, .panel-kicker { font: 800 11px/1.2 Consolas, monospace; letter-spacing: .16em; text-transform: uppercase; }
.deck-stamp { z-index: 1; flex: none; padding: 10px 13px; border: 2px solid var(--yellow); color: var(--yellow); font: 900 15px Consolas, monospace; transform: rotate(2deg); }
.panel { padding: 18px; min-width: 0; }
.top-grid, .middle-grid { display: grid; grid-template-columns: minmax(0, .82fr) minmax(0, 1.18fr); gap: 14px; }
.middle-grid { grid-template-columns: minmax(280px, .8fr) minmax(0, 1.2fr); }
.panel-kicker { margin: 0 0 7px; color: var(--muted); }
.panel h3 { margin: 0; font: 900 21px/1.15 "Arial Black", "Microsoft YaHei", sans-serif; }
.panel-heading, .leaderboard-head, .level-line { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.level-line strong { font: 900 38px/1 "Arial Black", sans-serif; }
.level-line span { font: 800 14px Consolas, monospace; }
.progress-track { height: 15px; margin: 18px 0 8px; padding: 2px; border: 2px solid var(--ink); background: rgba(255,255,255,.55); transform: skewX(-8deg); }
.progress-track span { display: block; height: 100%; background: var(--yellow); transition: width .45s ease; }
.progress-copy { margin: 0; color: var(--muted); font-size: 13px; }
.streak { padding: 5px 9px; border-radius: 999px; background: var(--cyan); font-size: 12px; font-weight: 800; }
.checkin-card > p { color: var(--muted); }
.checkin-button { width: 100%; min-height: 46px; border: 2px solid var(--ink); border-radius: 6px; background: var(--yellow); color: var(--ink); font-weight: 900; box-shadow: 3px 3px 0 var(--ink); }
.checkin-button:not(:disabled):not([aria-disabled="true"]):hover { transform: translate(2px, 2px); box-shadow: 1px 1px 0 var(--ink); }
.checkin-button:disabled, .checkin-button[aria-disabled="true"] { opacity: .55; cursor: not-allowed; }
.inline-message, .state-box { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin: 10px 0 0; font-size: 13px; }
.state-box { margin: 0; }
.success { color: #176c4d; }
.error { color: #a13228; }
.inline-message button, .state-box button { border: 0; padding: 3px; color: inherit; background: transparent; text-decoration: underline; font-weight: 800; }
.calendar-weekdays { margin-top: 18px; color: var(--muted); font: 700 11px Consolas, monospace; }
.calendar-grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 5px; text-align: center; }
.calendar-day { aspect-ratio: 1; display: grid; place-items: center; min-width: 0; border-radius: 5px; font: 700 12px Consolas, monospace; background: rgba(255,255,255,.55); }
.calendar-day.checked { color: #fff; background: var(--ink); box-shadow: inset 0 -3px var(--cyan); }
.calendar-day.today { outline: 2px solid var(--yellow); outline-offset: 1px; }
.calendar-day.blank { background: transparent; }
.badge-grid { list-style: none; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin: 14px 0 0; padding: 0; }
.badge { display: grid; grid-template-columns: 58px minmax(0, 1fr); align-items: center; gap: 10px; padding: 10px; border: 1px solid rgba(22,26,29,.2); border-radius: 8px; background: rgba(255,255,255,.52); }
.badge.locked { opacity: .72; }
.badge-title { display: flex; justify-content: space-between; gap: 5px; }
.badge-title span { color: var(--muted); font-size: 10px; }
.badge p { margin: 4px 0; color: var(--muted); font-size: 12px; }
.badge small { font: 700 10px Consolas, monospace; }
.board-switch { display: flex; padding: 3px; border: 1px solid var(--ink); border-radius: 7px; background: rgba(255,255,255,.5); }
.board-switch button { min-height: 32px; border: 0; border-radius: 4px; background: transparent; color: var(--ink); }
.board-switch button.active { background: var(--ink); color: #fff; }
.current-rank { display: flex; gap: 12px; align-items: baseline; margin: 14px 0 8px; padding: 10px 12px; border-left: 5px solid var(--cyan); background: rgba(53,201,207,.13); }
.current-rank strong { font: 900 21px Consolas, monospace; }
.rank-list { list-style: none; margin: 10px 0 0; padding: 0; }
.rank-list li { display: grid; grid-template-columns: 42px 42px minmax(0, 1fr) auto; gap: 10px; align-items: center; padding: 10px 8px; border-top: 1px dashed rgba(22,26,29,.22); }
.rank-list li.me { margin-inline: -4px; padding-inline: 12px; background: rgba(244,207,53,.18); }
.rank-number { text-align: center; font: 900 20px Consolas, monospace; }
.rank-avatar { width: 38px; height: 38px; display: grid; place-items: center; overflow: hidden; border: 2px solid var(--ink); border-radius: 50%; background: var(--cyan); font-weight: 900; }
.rank-avatar img { width: 100%; height: 100%; object-fit: cover; }
.rider, .score { display: grid; min-width: 0; }
.rider strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rider small, .score small { color: var(--muted); font-size: 10px; }
.score { text-align: right; }
.score strong { font: 900 18px Consolas, monospace; }
.empty-state { margin-top: 12px; padding: 18px; border: 1px dashed rgba(22,26,29,.35); text-align: center; color: var(--muted); }
.pagination { display: flex; justify-content: center; align-items: center; gap: 12px; margin-top: 12px; }
.pagination button { min-height: 34px; border: 1px solid var(--ink); background: transparent; color: var(--ink); }
button:focus-visible { outline: 3px solid var(--cyan); outline-offset: 3px; }

@media (max-width: 780px) {
  .top-grid, .middle-grid { grid-template-columns: 1fr; }
  .growth-header { align-items: flex-start; }
  .badge-grid { grid-template-columns: 1fr; }
}

@media (max-width: 520px) {
  .growth-header { padding: 18px; }
  .deck-stamp { font-size: 12px; }
  .panel { padding: 14px; }
  .leaderboard-head { align-items: flex-start; flex-direction: column; }
  .rank-list li { grid-template-columns: 30px 38px minmax(0, 1fr) auto; gap: 7px; padding-inline: 0; }
  .current-rank { flex-wrap: wrap; }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { scroll-behavior: auto !important; transition: none !important; animation: none !important; }
}
</style>
