<script setup>
import { computed } from 'vue'
const props=defineProps({category:String,name:String})
const kind=computed(()=> /鞋/.test(props.category) ? 'shoe' : /工具/.test(props.category) ? 'tool' : /头盔/.test(`${props.category} ${props.name}`) ? 'helmet' : /护具/.test(props.category) ? 'pad' : /轴承/.test(props.category) ? 'bearing' : /轮/.test(props.category) ? 'wheel' : /桥/.test(props.category) ? 'truck' : /螺/.test(props.category) ? 'hardware' : /垫|砂纸/.test(props.category) ? 'sheet' : /板面|整板/.test(props.category) ? 'board' : 'generic')
</script>
<template>
  <figure class="equipment-type-art">
    <svg viewBox="0 0 240 120" role="img" :aria-label="`${category || '装备'}类别示意`" fill="none" stroke="currentColor" stroke-width="3">
      <g v-if="kind === 'wheel' || kind === 'bearing'"><circle cx="120" cy="60" r="42"/><circle cx="120" cy="60" :r="kind === 'bearing' ? 27 : 13"/><circle v-if="kind === 'bearing'" cx="120" cy="60" r="11"/><path v-if="kind === 'wheel'" d="M116 18c52-6 71 63 20 82"/></g>
      <g v-else-if="kind === 'truck'"><path d="M48 40h144M65 31v18m110-18v18M80 40l25 32h30l25-32M120 63v28M85 91h70l12 16H73z"/></g>
      <g v-else-if="kind === 'hardware'"><path d="M74 25h35v12H74zM91 37v61m-10-45h20m-20 12h20m-20 12h20m-20 12h20M155 57l20 11v23l-20 11-20-11V68z"/><circle cx="155" cy="79" r="9"/></g>
      <g v-else-if="kind === 'sheet'"><path d="M55 34h115l20 45H75zM55 34v12l20 45h115V79M75 79v12"/><circle cx="86" cy="45" r="3"/><circle cx="151" cy="45" r="3"/></g>
      <g v-else-if="kind === 'helmet'"><path d="M70 75V57a50 40 0 01100 0v18H70zm5 0l22 28h46l22-28M95 38v23m25-31v30m25-22v23"/></g>
      <g v-else-if="kind === 'pad'"><rect x="76" y="20" width="88" height="80" rx="28"/><rect x="91" y="30" width="58" height="60" rx="24"/><path d="M76 40H50v16h26m88-16h26v16h-26M76 72H50v16h26m88-16h26v16h-26"/></g>
      <g v-else-if="kind === 'shoe'"><path d="M43 83V48l39-12 35 33 55 6q25 3 25 25H43zm0 8h151M83 52l16-10m-3 23l16-10m0 20l15-10"/></g>
      <g v-else-if="kind === 'tool'"><path d="M65 28h110v25h-42v55h-26V53H65zm17 0v25m76-25v25M107 93h26"/></g>
      <g v-else-if="kind === 'board'"><rect x="30" y="38" width="180" height="44" rx="22"/><path d="M65 45v30m110-30v30M72 49h8m-8 22h8m80-22h8m-8 22h8"/></g>
      <g v-else><rect x="75" y="16" width="90" height="88" rx="8"/><path d="M94 40h52m-52 20h52m-52 20h34"/></g>
    </svg>
    <figcaption>{{ category || '装备' }}结构示意 · 非商品照片</figcaption>
  </figure>
</template>
<style scoped>
.equipment-type-art { margin:0; padding:14px; background:var(--surface-muted); color:var(--text-soft); text-align:center; }
svg { width:100%; height:140px; }figcaption { font-size:12px; }
</style>
