<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { boardApi } from '../api/board'
import { getRole, getToken } from '../utils/auth'
import { errorText, labelState, requestGate } from '../board/state'
import BoardEvaluation from '../components/board/BoardEvaluation.vue'
import BoardPricing from '../components/board/BoardPricing.vue'
import '../board/board.css'
const router = useRouter(), gate = requestGate()
const list = ref([]), total = ref(0), page = ref(1), detail = ref(null), busy = ref(false), error = ref(''), notice = ref(''), name = ref(''), note = ref(''), deleting = ref(false)
function allowed() {
  if (!getToken()) { router.replace({ path: '/login', query: { returnTo: '/profile/board-plans' } }); return false }
  if (getRole() !== 'USER') { error.value = '我的方案仅限用户身份；管理员不能访问个人方案'; return false }
  return true
}
function fail(e) { error.value = errorText(e); if (e.status === 401) router.replace({ path: '/login', query: { returnTo: '/profile/board-plans' } }) }
async function load(nextPage = page.value) {
  if (!allowed()) return
  const id = gate.next(); busy.value = true; error.value = ''; detail.value = null; deleting.value = false; page.value = nextPage
  try { const data = await boardApi.plans({ page: nextPage, size: 10 }); if (gate.current(id)) { list.value = data.list; total.value = data.total } }
  catch(e) { if(gate.current(id)) fail(e) }
  finally { if(gate.current(id)) busy.value = false }
}
async function open(key) {
  if (!allowed()) return
  const id=gate.next(); busy.value=true; error.value=''; notice.value=''; detail.value=null; deleting.value=false
  try { const data=await boardApi.plan(key); if(gate.current(id)) { detail.value=data; name.value=data.snapshot.name; note.value=data.snapshot.note || '' } }
  catch(e) { if(gate.current(id)) fail(e) }
  finally { if(gate.current(id)) busy.value=false }
}
async function mutate(action) {
  if(busy.value || !detail.value || !allowed()) return
  const key=detail.value.snapshot.key, id=gate.next(); busy.value=true; error.value=''; notice.value=''
  try {
    const data=action === 'rename' ? await boardApi.rename(key,{name:name.value.trim(),note:note.value}) : action === 'copy' ? await boardApi.copy(key,{name:`${name.value.slice(0,74)}（副本）`,note:note.value}) : await boardApi.remove(key)
    if(!gate.current(id)) return
    if(action !== 'delete' && !data?.key) throw new Error('未返回方案凭据，无法确认操作完成')
    if(action === 'rename') { detail.value.snapshot = { ...detail.value.snapshot, name:data.name, note:data.note }; notice.value='名称与备注已更新' }
    else if(action === 'copy') { detail.value={snapshot:data,latestEvaluation:null}; name.value=data.name; note.value=data.note || ''; notice.value='已复制历史方案，价格保留原快照；查看最新状态需重新打开详情。' }
    else { detail.value=null; notice.value='方案已删除'; busy.value=false; await load(1) }
    deleting.value=false
  } catch(e) { if(gate.current(id)) fail(e) }
  finally { if(gate.current(id)) busy.value=false }
}
onMounted(() => load())
onBeforeUnmount(() => gate.invalidate())
</script>
<template>
  <div class="board-page"><header class="board-head"><h1>我的方案</h1><RouterLink to="/board-assistant">继续选板</RouterLink></header>
    <p class="muted">保存时价格与检查结果作为历史快照保留。最新状态单独显示，不覆盖历史。</p>
    <p v-if="busy" role="status">读取中…</p><p v-if="error" role="alert" class="board-error">{{ error }}</p><p v-if="notice" role="status" class="board-notice">{{ notice }}</p>
    <section v-if="!detail" class="card"><p v-if="!busy && !list.length && !error">尚未保存方案。先在选板助手查找并确认保存。</p><button v-if="error" :disabled="busy" @click="load()">重新读取方案</button><article v-for="plan in list" :key="plan.key" class="board-item"><h2>{{ plan.name }}</h2><BoardPricing :evaluation="plan" /><p>{{ labelState(plan.snapshotState) }} · {{ plan.createdAt }}</p><p v-if="plan.historicalCopy">历史价格副本</p><p>{{ plan.note }}</p><button :disabled="busy" @click="open(plan.key)">查看{{ plan.name }}</button></article><div class="board-actions"><button :disabled="busy || page === 1" @click="load(page-1)">上一页</button><span>第{{ page }}页 / {{ total }}套</span><button :disabled="busy || page*10 >= total" @click="load(page+1)">下一页</button></div></section>
    <template v-else><button :disabled="busy" @click="load()">返回方案列表</button><section class="card" data-testid="saved-snapshot"><h2>保存时快照 · {{ detail.snapshot.name }}</h2><p>保存时间 {{ detail.snapshot.createdAt }}</p><p v-if="detail.snapshot.historicalCopy">历史方案副本保留原快照价格；不表示当前可购买价格。</p><BoardEvaluation :evaluation="detail.snapshot" /></section><section class="card" data-testid="latest-evaluation"><h2>最新重新计算</h2><BoardEvaluation v-if="detail.latestEvaluation" :evaluation="detail.latestEvaluation" /><p v-else>尚未读取最新状态。重新打开详情可获取。</p></section><form class="card board-form" @submit.prevent="mutate('rename')"><label>方案名称<input v-model="name" required maxlength="80" /></label><label>备注<textarea v-model="note" maxlength="1000" /></label><button :disabled="busy">保存名称与备注</button><button type="button" :disabled="busy" @click="mutate('copy')">复制历史方案</button><button type="button" :disabled="busy" @click="deleting = true">删除方案</button><div v-if="deleting" class="board-notice"><p>确认删除本人方案“{{ detail.snapshot.name }}”？</p><button type="button" :disabled="busy" @click="mutate('delete')">确认删除</button><button type="button" @click="deleting = false">取消删除</button></div></form></template>
  </div>
</template>
