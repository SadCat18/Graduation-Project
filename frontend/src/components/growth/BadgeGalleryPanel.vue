<script setup>
import { computed, ref } from 'vue'
import AchievementBadge from './AchievementBadge.vue'
import BadgeDetailDrawer from './BadgeDetailDrawer.vue'

const props = defineProps({
  badges: { type: Array, default: () => [] }
})

const statusFilter = ref('ALL')
const rarityFilter = ref('ALL')
const selectedBadge = ref(null)
const detailTrigger = ref(null)
const rarityOrder = { LEGENDARY: 0, EPIC: 1, RARE: 2, COMMON: 3 }
const statusOptions = [
  { key: 'ALL', label: '全部' },
  { key: 'UNLOCKED', label: '已解锁' },
  { key: 'LOCKED', label: '未解锁' }
]
const rarityOptions = [
  { key: 'ALL', label: '全部' },
  { key: 'COMMON', label: '普通' },
  { key: 'RARE', label: '稀有' },
  { key: 'EPIC', label: '史诗' },
  { key: 'LEGENDARY', label: '传说' }
]

const unlockedCount = computed(() => props.badges.filter(item => item.unlocked).length)
const completion = computed(() => props.badges.length
  ? Math.round(unlockedCount.value / props.badges.length * 100)
  : 0)
const rarityCounts = computed(() => Object.fromEntries(
  rarityOptions
    .filter(item => item.key !== 'ALL')
    .map(item => [item.key, props.badges.filter(badge => badge.rarity === item.key).length])
))
const filteredBadges = computed(() => [...props.badges]
  .filter(item => statusFilter.value === 'ALL' || (statusFilter.value === 'UNLOCKED') === Boolean(item.unlocked))
  .filter(item => rarityFilter.value === 'ALL' || item.rarity === rarityFilter.value)
  .sort((a, b) => Number(b.unlocked) - Number(a.unlocked)
    || (rarityOrder[a.rarity] ?? 99) - (rarityOrder[b.rarity] ?? 99)))

function rarityLabel(rarity) {
  return rarityOptions.find(item => item.key === rarity)?.label || '普通'
}

function badgeProgress(badge) {
  return Math.min(100, Math.round(Number(badge?.current || 0) / Math.max(1, Number(badge?.target || 1)) * 100))
}

function formatUnlockTime(value) {
  return String(value || '').replace('T', ' ') || '时间未记录'
}

function openBadge(badge, event) {
  selectedBadge.value = badge
  detailTrigger.value = event.currentTarget
}

function closeBadge() {
  selectedBadge.value = null
}

function clearFilters() {
  statusFilter.value = 'ALL'
  rarityFilter.value = 'ALL'
}
</script>

<template>
  <section class="badge-gallery" aria-labelledby="badge-gallery-title">
    <header class="archive-header">
      <div>
        <p class="archive-kicker">STICKER ARCHIVE / 荣誉图鉴</p>
        <h2 id="badge-gallery-title">徽章图鉴</h2>
        <p>每一枚贴纸，都记录一段真实的社区成长。</p>
      </div>
      <div class="completion-block">
        <strong>{{ unlockedCount }} / {{ badges.length }}</strong>
        <span>已收入图鉴</span>
        <div class="completion-track" role="progressbar" aria-label="徽章总完成度" :aria-valuenow="completion" aria-valuemin="0" aria-valuemax="100">
          <span :style="{ width: `${completion}%` }"></span>
        </div>
        <small>{{ completion }}%</small>
      </div>
    </header>

    <div class="rarity-stats" aria-label="各稀有度徽章数量">
      <div v-for="item in rarityOptions.slice(1)" :key="item.key" :class="`rarity-${item.key.toLowerCase()}`">
        <span>{{ item.label }}</span><strong>{{ rarityCounts[item.key] || 0 }}</strong>
      </div>
    </div>

    <div class="filter-board">
      <fieldset>
        <legend>解锁状态</legend>
        <button
          v-for="item in statusOptions"
          :key="item.key"
          type="button"
          :aria-pressed="statusFilter === item.key"
          @click="statusFilter = item.key"
        >{{ item.label }}</button>
      </fieldset>
      <fieldset>
        <legend>稀有度</legend>
        <button
          v-for="item in rarityOptions"
          :key="item.key"
          type="button"
          :aria-pressed="rarityFilter === item.key"
          @click="rarityFilter = item.key"
        >{{ item.label }}</button>
      </fieldset>
      <p class="result-count" aria-live="polite">当前展示 {{ filteredBadges.length }} 枚徽章</p>
    </div>

    <div v-if="!badges.length" class="gallery-empty">暂无徽章数据，完成成长任务后再回来看看。</div>
    <div v-else-if="!filteredBadges.length" class="gallery-empty">
      <p>当前筛选条件下没有徽章。</p>
      <button type="button" @click="clearFilters">清除筛选</button>
    </div>
    <div v-else class="badge-card-grid">
      <button
        v-for="badge in filteredBadges"
        :key="badge.code"
        type="button"
        class="badge-card"
        :class="[`rarity-${String(badge.rarity || 'COMMON').toLowerCase()}`, { locked: !badge.unlocked }]"
        @click="openBadge(badge, $event)"
      >
        <span class="card-state">{{ badge.unlocked ? '已解锁' : '未解锁' }}</span>
        <AchievementBadge :badge="badge" :size="88" />
        <span class="badge-copy">
          <span class="badge-heading"><strong>{{ badge.name }}</strong><small>{{ rarityLabel(badge.rarity) }}</small></span>
          <span class="badge-description">{{ badge.description }}</span>
        </span>
        <span v-if="badge.unlocked" class="unlocked-stamp">
          <strong>已收入图鉴</strong><small>{{ formatUnlockTime(badge.unlockTime) }}</small>
        </span>
        <span v-else class="card-progress">
          <span><small>当前进度</small><strong>{{ badge.current || 0 }} / {{ badge.target || 0 }}</strong></span>
          <span class="progress-track" role="progressbar" :aria-label="`${badge.name}解锁进度`" :aria-valuenow="badgeProgress(badge)" aria-valuemin="0" aria-valuemax="100">
            <span :style="{ width: `${badgeProgress(badge)}%` }"></span>
          </span>
        </span>
      </button>
    </div>

    <BadgeDetailDrawer
      v-if="selectedBadge"
      :badge="selectedBadge"
      :return-focus-to="detailTrigger"
      @close="closeBadge"
    />
  </section>
</template>

<style scoped>
.badge-gallery {
  --archive-ink: #161a1d;
  --archive-paper: #f3eddd;
  --archive-yellow: #f4cf35;
  display: grid;
  gap: 14px;
  width: 100%;
  min-width: 0;
  color: var(--archive-ink);
}

.archive-header {
  position: relative;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  overflow: hidden;
  padding: 24px;
  border: 2px solid var(--archive-ink);
  border-radius: 14px;
  color: #fff;
  background: var(--archive-ink) repeating-linear-gradient(105deg, transparent 0 34px, rgba(255,255,255,.045) 35px 36px);
  box-shadow: 5px 6px 0 rgba(22, 26, 29, 0.14);
}

.archive-header::after {
  content: '';
  position: absolute;
  right: -30px;
  bottom: 12px;
  width: 220px;
  height: 13px;
  background: var(--archive-yellow);
  transform: rotate(-6deg);
}

.archive-kicker { margin: 0 0 7px; color: rgba(255,255,255,.7); font: 800 11px/1.2 Consolas, monospace; letter-spacing: .16em; }
.archive-header h2 { margin: 0; font: 900 clamp(30px, 4vw, 46px)/1 "Microsoft YaHei", sans-serif; }
.archive-header p:last-child { margin: 8px 0 0; color: rgba(255,255,255,.72); }

.completion-block { z-index: 1; display: grid; grid-template-columns: auto auto; gap: 3px 10px; min-width: 210px; }
.completion-block > strong { font: 900 27px/1 Consolas, monospace; }
.completion-block > span { align-self: end; color: rgba(255,255,255,.72); font-size: 12px; }
.completion-block > small { text-align: right; color: var(--archive-yellow); font-weight: 900; }
.completion-track { grid-column: 1 / -1; height: 13px; padding: 2px; border: 2px solid #fff; background: rgba(255,255,255,.14); }
.completion-track span { display: block; height: 100%; background: var(--archive-yellow); }

.rarity-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }
.rarity-stats > div { display: flex; justify-content: space-between; padding: 12px 14px; border: 2px solid var(--archive-ink); background: var(--archive-paper); box-shadow: 3px 3px 0 var(--rarity-color); }
.rarity-stats strong { font: 900 20px Consolas, monospace; }
.rarity-common { --rarity-color: #cbd5e1; }
.rarity-rare { --rarity-color: #60a5fa; }
.rarity-epic { --rarity-color: #c084fc; }
.rarity-legendary { --rarity-color: #fbbf24; }

.filter-board { display: grid; gap: 10px; padding: 14px; border: 1px solid rgba(22,26,29,.22); border-radius: 12px; background: rgba(255,255,255,.72); }
.filter-board fieldset { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; margin: 0; padding: 0; border: 0; }
.filter-board legend { float: left; min-width: 74px; margin-right: 8px; font-weight: 900; }
.filter-board button { min-height: 34px; border: 1px solid rgba(22,26,29,.25); background: #fffdf7; }
.filter-board button[aria-pressed="true"] { color: #fff; background: var(--archive-ink); border-color: var(--archive-ink); }
.result-count { margin: 0; color: #68665f; font-size: 13px; }

.badge-card-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.badge-card { --rarity-color: #cbd5e1; display: grid; justify-items: start; gap: 12px; min-width: 0; padding: 18px; border: 2px solid var(--archive-ink); border-radius: 10px 15px 9px 13px; color: var(--archive-ink); text-align: left; background: var(--archive-paper) repeating-linear-gradient(112deg, transparent 0 22px, rgba(23,32,42,.035) 23px 24px); box-shadow: 4px 5px 0 var(--rarity-color); }
.badge-card:hover:not(:disabled) { transform: translateY(-3px) rotate(-.25deg); border-color: var(--archive-ink); }
.badge-card:focus-visible { outline: 4px solid var(--rarity-color); outline-offset: 3px; }
.card-state { padding: 3px 8px; border: 1px solid var(--archive-ink); background: var(--rarity-color); font-size: 11px; font-weight: 900; }
.badge-copy { display: grid; gap: 6px; min-width: 0; width: 100%; }
.badge-heading { display: flex; align-items: start; justify-content: space-between; gap: 8px; }
.badge-heading strong { font-size: 18px; }
.badge-heading small { color: #68665f; }
.badge-description { min-height: 3em; color: #68665f; font-size: 13px; }
.unlocked-stamp { display: grid; gap: 2px; padding: 8px 10px; border: 2px solid #176c4d; color: #176c4d; background: rgba(255,255,255,.58); transform: rotate(-1deg); }
.card-progress { display: grid; gap: 6px; width: 100%; }
.card-progress > span:first-child { display: flex; justify-content: space-between; }
.progress-track { display: block; height: 12px; padding: 2px; border: 2px solid var(--archive-ink); background: #fffdf7; }
.progress-track > span { display: block; height: 100%; background: var(--rarity-color); }
.badge-card.locked .card-state { background: #d8d8d2; }
.gallery-empty { padding: 28px; border: 2px dashed rgba(22,26,29,.35); text-align: center; background: rgba(255,255,255,.55); }
.gallery-empty p { margin-top: 0; }

@media (max-width: 1100px) { .badge-card-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 820px) {
  .archive-header { align-items: flex-start; flex-direction: column; }
  .completion-block { width: 100%; }
  .rarity-stats, .badge-card-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 560px) {
  .archive-header { padding: 20px; }
  .rarity-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .badge-card-grid { grid-template-columns: 1fr; }
  .filter-board fieldset { align-items: stretch; }
  .filter-board legend { width: 100%; margin: 0 0 4px; }
}
</style>
