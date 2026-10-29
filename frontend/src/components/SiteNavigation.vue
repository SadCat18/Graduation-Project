<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { warmRouteChunk } from '../utils/routePrefetch'
const props = defineProps({ mobile: Boolean, personal: { type: Array, default: () => [] } })
const route = useRoute(), root = ref(null), open = ref('')
const groups = [
  { title:'社区', links:[['帖子广场','/community'],['社区快讯','/bulletins'],['滑板资讯','/#skate-news'],['平台公告','/#site-notices']] },
  { title:'学滑板', links:[['学习路径及动作库','/skate?tab=tricks'],['训练计划','/skate?tab=training'],['我的训练指导','/training-guidance'],['AI滑板老师','/ai-coach']] },
  { title:'选装备', links:[['选板助手','/board-assistant'],['装备知识','/skate?tab=equipment'],['我的方案','/profile/board-plans']] },
  { title:'去滑板', links:[['同城约板','/activities'],['赛事活动','/skate?tab=events']] }
]
function close(restore = false) {
  const title = open.value; open.value = ''
  if (restore) root.value?.querySelector(`[data-group="${title}"]`)?.focus()
}
function outside(event) { if (!root.value?.contains(event.target)) close() }
function prefetch(to) { warmRouteChunk(to.split(/[?#]/)[0]) }
watch(() => route.fullPath, () => close())
onMounted(() => document.addEventListener('pointerdown', outside))
onBeforeUnmount(() => document.removeEventListener('pointerdown', outside))
</script>
<template>
  <nav ref="root" :class="['site-navigation', { 'site-navigation-mobile': mobile }]" :aria-label="mobile ? '移动端主导航' : '主导航'" @keydown.esc.prevent.stop="close(true)">
    <div v-for="group in groups" :key="group.title" class="site-nav-group">
      <button type="button" :data-group="group.title" :aria-expanded="open === group.title" :aria-controls="`${mobile ? 'mobile' : 'desktop'}-nav-${group.title}`" @click="open === group.title ? close(true) : open = group.title">{{ group.title }} <span aria-hidden="true">⌄</span></button>
      <div v-if="open === group.title" :id="`${mobile ? 'mobile' : 'desktop'}-nav-${group.title}`" class="site-subnav">
        <RouterLink v-for="[label, to] in group.links" :key="to" :to="to" @mouseenter="prefetch(to)" @focus="prefetch(to)" @touchstart.passive="prefetch(to)" @click="close()">{{ label }}</RouterLink>
        <button type="button" @click="close(true)">关闭{{ group.title }}导航</button>
      </div>
    </div>
    <RouterLink v-for="item in props.personal" :key="item.to" :to="item.to" class="site-personal">{{ item.label }}<small v-if="item.badge"> {{ item.badge }}</small></RouterLink>
  </nav>
</template>
<style scoped>
.site-navigation { display:flex; flex-wrap:wrap; gap:6px; align-items:center; justify-content:center; }
.site-nav-group { position:relative; }
.site-navigation button,.site-personal { padding:8px 10px; font-size:14px; white-space:nowrap; }
.site-subnav { position:absolute; top:calc(100% + 8px); left:0; min-width:210px; z-index:110; display:grid; gap:4px; padding:10px; border:1px solid var(--line); border-radius:12px; background:var(--surface,#fff); box-shadow:var(--shadow-md); }
.site-subnav a { padding:10px; border-radius:6px; }
.site-subnav a:hover,.site-subnav a:focus-visible { background:var(--surface-muted); }
.site-navigation :focus-visible { outline:3px solid var(--accent,#087a6d); outline-offset:2px; }
.site-navigation-mobile { display:none; }
@media(max-width:980px) {
  .site-navigation:not(.site-navigation-mobile) { display:none; }
  .site-navigation-mobile { display:flex; position:fixed; bottom:0; left:0; right:0; z-index:120; justify-content:space-around; padding:8px 5px calc(8px + env(safe-area-inset-bottom)); background:var(--surface,#fff); border-top:1px solid var(--line); }
  .site-navigation-mobile button,.site-navigation-mobile .site-personal { padding:7px 5px; font-size:12px; }
  .site-navigation-mobile .site-subnav { position:fixed; top:auto; bottom:75px; left:10px; right:10px; max-height:65dvh; overflow:auto; }
}
</style>
