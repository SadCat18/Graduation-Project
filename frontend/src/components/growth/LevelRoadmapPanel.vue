<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  roadmap: { type: Object, default: () => ({ levels: [] }) },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})
defineEmits(['retry'])

const selectedLevel = ref(null)
const currentNode = computed(() => (props.roadmap?.levels || []).find(level => level.state === 'CURRENT') || null)

watch(
  () => props.roadmap?.levels,
  levels => {
    const list = Array.isArray(levels) ? levels : []
    const retained = list.find(level => level.level === selectedLevel.value?.level)
    selectedLevel.value = retained || list.find(level => level.state === 'CURRENT') || list[0] || null
  },
  { immediate: true }
)

function stateLabel(state) {
  return ({ COMPLETED: '已完成', CURRENT: '当前位置', LOCKED: '尚未到达' })[state] || '尚未到达'
}
</script>

<template>
  <section class="level-roadmap" aria-labelledby="level-roadmap-title">
    <header class="roadmap-identity">
      <div>
        <p class="roadmap-kicker">LEVEL EVOLUTION / 等级进化</p>
        <h2 id="level-roadmap-title">Lv{{ roadmap.currentLevel || 1 }} {{ currentNode?.title || '新板入场' }}</h2>
        <p>沿着滑板公园一路前进，看看你已经滑到了哪里。</p>
      </div>
      <div class="identity-score">
        <strong>{{ roadmap.currentExp || 0 }} EXP</strong>
        <span v-if="roadmap.nextLevel">距离 Lv{{ roadmap.nextLevel }} 还需 {{ roadmap.remainToNextLevel }} EXP</span>
        <span v-else>已抵达当前最高等级</span>
      </div>
    </header>

    <div v-if="loading" class="roadmap-state" aria-live="polite">正在铺设等级路线...</div>
    <div v-else-if="error" class="roadmap-state error" role="alert">
      <span>{{ error }}</span><button type="button" @click="$emit('retry')">重试</button>
    </div>
    <div v-else-if="!roadmap.levels?.length" class="roadmap-state">暂无等级路线数据。</div>
    <template v-else>
      <div class="skatepark-frame">
        <div class="park-label" aria-hidden="true">KEEP ROLLING</div>
        <div class="skatepark-route" aria-label="Lv1 至 Lv10 等级路线">
          <button
            v-for="level in roadmap.levels"
            :key="level.level"
            type="button"
            class="level-node"
            :class="level.state.toLowerCase()"
            :aria-current="level.state === 'CURRENT' ? 'step' : undefined"
            :aria-pressed="selectedLevel?.level === level.level"
            @click="selectedLevel = level"
          >
            <span class="node-status">{{ stateLabel(level.state) }}</span>
            <span class="node-level">LV.{{ level.level }}</span>
            <strong>{{ level.title }}</strong>
            <small>{{ level.requiredExp }} EXP</small>
          </button>
        </div>
      </div>

      <article v-if="selectedLevel" class="level-detail" :class="selectedLevel.state.toLowerCase()">
        <div>
          <p>{{ stateLabel(selectedLevel.state) }}</p>
          <h3>Lv{{ selectedLevel.level }} {{ selectedLevel.title }}</h3>
          <span>{{ selectedLevel.description }}</span>
        </div>
        <strong>累计 {{ selectedLevel.requiredExp }} EXP</strong>
      </article>
    </template>
  </section>
</template>

<style scoped>
.level-roadmap {
  --route-ink: #161a1d;
  --route-paper: #f3eddd;
  --route-yellow: #f4cf35;
  --route-cyan: #35c9cf;
  display: grid;
  gap: 14px;
  width: 100%;
  min-width: 0;
  overflow-x: hidden;
  color: var(--route-ink);
}

.roadmap-identity {
  position: relative;
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 24px;
  overflow: hidden;
  padding: 24px;
  border: 2px solid var(--route-ink);
  border-radius: 14px;
  color: #fff;
  background: var(--route-ink) repeating-linear-gradient(105deg, transparent 0 34px, rgba(255,255,255,.045) 35px 36px);
  box-shadow: 5px 6px 0 rgba(22, 26, 29, 0.14);
}

.roadmap-identity::after {
  content: '';
  position: absolute;
  right: -35px;
  bottom: 12px;
  width: 240px;
  height: 13px;
  background: var(--route-yellow);
  transform: rotate(-6deg);
}

.roadmap-kicker { margin: 0 0 7px; color: rgba(255,255,255,.7); font: 800 11px/1.2 Consolas, monospace; letter-spacing: .16em; }
.roadmap-identity h2 { margin: 0; font: 900 clamp(30px, 4vw, 46px)/1 "Microsoft YaHei", sans-serif; }
.roadmap-identity p:last-child { margin: 8px 0 0; color: rgba(255,255,255,.72); }
.identity-score { z-index: 1; display: grid; gap: 5px; min-width: 220px; text-align: right; }
.identity-score strong { color: var(--route-yellow); font: 900 26px/1 Consolas, monospace; }
.identity-score span { color: rgba(255,255,255,.75); font-size: 13px; }

.roadmap-state { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 24px; border: 2px dashed rgba(22,26,29,.35); background: rgba(255,255,255,.58); }
.roadmap-state.error { color: #a13228; }

.skatepark-frame {
  position: relative;
  overflow: hidden;
  padding: 58px 28px 30px;
  border: 2px solid var(--route-ink);
  border-radius: 14px;
  background: var(--route-paper) repeating-linear-gradient(112deg, transparent 0 24px, rgba(22,26,29,.03) 25px 26px);
  box-shadow: 5px 6px 0 rgba(22,26,29,.12);
}

.park-label { position: absolute; top: 16px; left: 20px; padding: 5px 9px; color: #fff; background: var(--route-ink); font: 900 11px Consolas, monospace; letter-spacing: .14em; transform: rotate(-1deg); }
.skatepark-route { position: relative; display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 54px 16px; }
.skatepark-route::before { content: ''; position: absolute; z-index: 0; inset: 50% 4% auto; height: 8px; border: 2px solid var(--route-ink); border-radius: 999px; background: linear-gradient(90deg, var(--route-yellow) 0 38%, #d7d4ca 38% 100%); transform: translateY(-50%) rotate(-1.2deg); }

.level-node {
  position: relative;
  z-index: 1;
  display: grid;
  justify-items: start;
  gap: 4px;
  min-width: 0;
  min-height: 112px;
  padding: 14px;
  border: 2px solid var(--route-ink);
  border-radius: 8px 13px 7px 11px;
  color: var(--route-ink);
  text-align: left;
  background: #fffdf7;
  box-shadow: 4px 5px 0 #c9c7bd;
}

.level-node:nth-child(even) { transform: translateY(20px) rotate(.35deg); }
.level-node:nth-child(odd) { transform: translateY(-8px) rotate(-.35deg); }
.level-node:hover:not(:disabled) { transform: translateY(-3px); border-color: var(--route-ink); }
.level-node.completed { background: var(--route-ink); color: #fff; box-shadow: 4px 5px 0 var(--route-yellow); }
.level-node.current { border-color: var(--route-ink); background: var(--route-cyan); box-shadow: 0 0 0 4px #fffdf7, 0 0 0 7px var(--route-cyan), 5px 6px 0 var(--route-ink); animation: current-node-pulse 2.2s ease-in-out infinite; }
.level-node.locked { color: #696b68; background: #e7e4da; filter: grayscale(.55); }
.level-node[aria-pressed="true"] { outline: 4px solid var(--route-yellow); outline-offset: 3px; }
.node-status { padding: 2px 6px; border: 1px solid currentColor; font-size: 10px; font-weight: 900; }
.node-level { font: 900 12px Consolas, monospace; letter-spacing: .08em; }
.level-node strong { overflow: hidden; max-width: 100%; font-size: 17px; text-overflow: ellipsis; white-space: nowrap; }
.level-node small { opacity: .78; }

.level-detail { --detail-accent: #d7d4ca; display: flex; align-items: center; justify-content: space-between; gap: 22px; padding: 20px 22px; border: 2px solid var(--route-ink); border-left: 10px solid var(--detail-accent); background: rgba(255,255,255,.78); box-shadow: 4px 5px 0 rgba(22,26,29,.1); }
.level-detail.completed { --detail-accent: var(--route-yellow); }
.level-detail.current { --detail-accent: var(--route-cyan); }
.level-detail p { margin: 0 0 4px; color: #68665f; font: 800 11px Consolas, monospace; letter-spacing: .12em; }
.level-detail h3 { margin: 0 0 5px; font-size: 23px; }
.level-detail span { color: #68665f; }
.level-detail > strong { flex: none; font: 900 17px Consolas, monospace; }

@keyframes current-node-pulse {
  50% { box-shadow: 0 0 0 4px #fffdf7, 0 0 0 10px rgba(53,201,207,.34), 5px 6px 0 var(--route-ink); }
}

@media (max-width: 900px) {
  .skatepark-route { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .skatepark-route::before { inset: 48% 3% auto; }
}

@media (max-width: 700px) {
  .roadmap-identity { align-items: start; flex-direction: column; }
  .identity-score { min-width: 0; text-align: left; }
  .skatepark-frame { padding: 54px 18px 24px; }
  .skatepark-route { grid-template-columns: 1fr; gap: 12px; padding-left: 24px; }
  .skatepark-route::before { inset: 0 auto 0 6px; width: 8px; height: auto; transform: none; background: linear-gradient(180deg, var(--route-yellow) 0 38%, #d7d4ca 38% 100%); }
  .level-node:nth-child(n) { min-height: 96px; transform: none; }
  .level-detail { align-items: flex-start; flex-direction: column; }
}

@media (prefers-reduced-motion: reduce) {
  .level-node.current { animation: none; }
  .level-node { transition: none; }
}
</style>
