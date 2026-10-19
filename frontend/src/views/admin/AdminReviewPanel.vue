<script setup>
import { computed } from 'vue'
import AdminEmptyState from './dashboard/AdminEmptyState.vue'

const props = defineProps({
  pendingReviewTotal: { type: Number, default: 0 },
  statusFilter: { type: String, default: 'all' },
  typeFilter: { type: String, default: 'all' },
  timeFilter: { type: String, default: 'all' },
  keyword: { type: String, default: '' },
  pendingCounts: { type: Object, default: () => ({}) },
  tabs: { type: Array, default: () => [] },
  centerTab: { type: String, default: 'all' },
  allFilteredSelected: { type: Boolean, default: false },
  selectedCards: { type: Array, default: () => [] },
  canBatchApproveOrReject: { type: Boolean, default: false },
  filteredCards: { type: Array, default: () => [] },
  selectedIds: { type: Array, default: () => [] },
  moderationLoadingKey: { type: String, default: '' },
  drawerVisible: { type: Boolean, default: false },
  drawerItem: { type: Object, default: null },
  drawerTemplates: { type: Array, default: () => [] },
  moderationResultKey: { type: String, default: '' },
  moderationResult: { type: Object, default: null }
})

const selectionSummary = computed(() => {
  if (!props.selectedCards.length) return '未选择内容'
  const types = [...new Set(props.selectedCards.map(item => item.typeLabel))]
  const pendingCount = props.selectedCards.filter(item => item.status === '0' || item.type === 'placeContribution' || item.type === 'placeReview').length
  return `已选 ${props.selectedCards.length} 条 · ${types.join('、')} · 待审核 ${pendingCount} 条`
})

defineEmits([
  'update:statusFilter',
  'update:typeFilter',
  'update:timeFilter',
  'update:keyword',
  'update:centerTab',
  'reset',
  'toggleSelectAll',
  'toggleSelect',
  'batchReview',
  'action',
  'moderation',
  'closeDrawer'
])
</script>

<template>
  <div class="review-center">
    <div class="card module-section-head compact">
      <div>
        <span class="section-kicker">集中审核</span>
        <h3>统一处理活动、快讯、举报和场地评价</h3>
      </div>
      <span class="muted">待办 {{ pendingReviewTotal }} 条</span>
    </div>

    <div class="card review-filter-bar">
      <select :value="statusFilter" @change="$emit('update:statusFilter', $event.target.value)">
        <option value="all">状态：全部</option>
        <option value="pending">状态：待处理</option>
        <option value="approved">状态：已通过/已处理</option>
        <option value="rejected">状态：已驳回</option>
      </select>
      <select :value="typeFilter" @change="$emit('update:typeFilter', $event.target.value)">
        <option value="all">类型：全部</option>
        <option value="activity">活动</option>
        <option value="bulletin">快讯</option>
        <option value="report">举报</option>
        <option value="placeContribution">场地贡献</option>
        <option value="placeReview">场地评价</option>
      </select>
      <select :value="timeFilter" @change="$emit('update:timeFilter', $event.target.value)">
        <option value="all">时间：全部</option>
        <option value="today">今天</option>
        <option value="7d">近 7 天</option>
        <option value="30d">近 30 天</option>
      </select>
      <input
        :value="keyword"
        type="text"
        placeholder="关键词：标题/发布人/举报原因"
        @input="$emit('update:keyword', $event.target.value)"
      />
      <button class="btn-soft" @click="$emit('reset')">重置筛选</button>
    </div>

    <div class="card review-kpi-grid">
      <div class="review-kpi-card">
        <p>活动（当前结果）</p>
        <strong>{{ pendingCounts.activity || 0 }}</strong>
      </div>
      <div class="review-kpi-card">
        <p>快讯（当前结果）</p>
        <strong>{{ pendingCounts.bulletin || 0 }}</strong>
      </div>
      <div class="review-kpi-card">
        <p>举报（当前结果）</p>
        <strong>{{ pendingCounts.report || 0 }}</strong>
      </div>
      <div class="review-kpi-card">
        <p>场地贡献（当前结果）</p>
        <strong>{{ pendingCounts.placeContribution || 0 }}</strong>
      </div>
      <div class="review-kpi-card">
        <p>场地评价（当前结果）</p>
        <strong>{{ pendingCounts.placeReview || 0 }}</strong>
      </div>
    </div>

    <div class="card review-tabs">
      <button
        v-for="item in tabs"
        :key="item.key"
        class="btn-soft"
        :class="{ active: centerTab === item.key }"
        @click="$emit('update:centerTab', item.key)"
      >
        {{ item.label }}
      </button>
    </div>

    <div class="card content-list">
      <div class="review-batch-bar">
        <label class="review-check-all">
          <input
            type="checkbox"
            :checked="allFilteredSelected"
            @change="$emit('toggleSelectAll', $event.target.checked)"
          />
          <span>全选当前列表</span>
        </label>
        <span class="batch-selection-summary">{{ selectionSummary }}</span>
        <button class="btn-primary" :disabled="!canBatchApproveOrReject" @click="$emit('batchReview', 'approve')">批量通过</button>
        <button class="btn-soft" :disabled="!canBatchApproveOrReject" @click="$emit('batchReview', 'reject')">批量驳回</button>
      </div>

      <AdminEmptyState
        v-if="!filteredCards.length"
        icon="search"
        title="未找到匹配项"
        description="请调整状态、类型、时间或关键词筛选条件。"
        action-label="重置筛选"
        @action="$emit('reset')"
      />
      <div v-for="item in filteredCards" :key="item.id" :class="['review-card', `risk-${String(item.riskLevel || 'LOW').toLowerCase()}`]">
        <div class="review-card-main">
          <div class="review-card-head">
            <label class="review-select">
              <input
                type="checkbox"
                :checked="selectedIds.includes(item.id)"
                @change="$emit('toggleSelect', item.id, $event.target.checked)"
              />
            </label>
            <span class="status-chip">{{ item.typeLabel }}</span>
            <span class="status-chip">{{ item.statusLabel }}</span>
            <span :class="['risk-chip', `risk-${String(item.riskLevel || 'LOW').toLowerCase()}`]">{{ item.riskLabel || '普通' }}</span>
            <span class="wait-chip">{{ item.waitLabel || '等待时间未知' }}</span>
          </div>
          <h3 class="review-title clamp-2">{{ item.title }}</h3>
          <p class="review-meta">发布人：{{ item.actor }}</p>
        </div>

        <div class="review-card-judge">
          <p class="review-meta">发布时间：{{ item.time }}</p>
          <p class="review-summary clamp-3">{{ item.summary }}</p>
          <div class="review-facts">
            <p v-for="fact in item.facts" :key="`${item.id}-${fact.label}`">
              <span>{{ fact.label }}：</span>{{ fact.value }}
            </p>
          </div>
          <p v-if="moderationResultKey === item.id && moderationResult" class="card-ai-summary">
            <strong>AI 建议：</strong>{{ moderationResult.suggestion || moderationResult.normalizedSummary || '未发现明显风险' }}
          </p>
        </div>

        <div class="review-card-actions">
          <template v-if="item.type === 'activity' || item.type === 'bulletin'">
            <button v-if="item.status === '0'" class="btn-primary" @click="$emit('action', item, 'approve')">通过</button>
            <button class="btn-soft" @click="$emit('action', item, 'view')">查看</button>
            <button class="btn-soft" :disabled="moderationLoadingKey === item.id" @click="$emit('moderation', item)">
              {{ moderationLoadingKey === item.id ? '分析中...' : 'AI 审核建议' }}
            </button>
            <button v-if="item.status === '0'" class="btn-soft" @click="$emit('action', item, 'reject')">驳回</button>
          </template>
          <template v-else-if="item.type === 'report'">
            <button v-if="item.status === '0'" class="btn-primary" @click="$emit('action', item, 'done')">标记已处理</button>
            <button class="btn-soft" @click="$emit('action', item, 'view')">查看</button>
            <button v-if="item.status === '0'" class="btn-soft" @click="$emit('action', item, 'reject')">驳回举报</button>
            <button class="btn-danger" @click="$emit('action', item, 'delete')">删除违规内容</button>
          </template>
          <template v-else-if="item.type === 'placeContribution'">
            <button class="btn-primary" @click="$emit('action', item, 'approve')">通过</button>
            <button class="btn-soft" @click="$emit('action', item, 'view')">查看</button>
            <button class="btn-soft" :disabled="moderationLoadingKey === item.id" @click="$emit('moderation', item)">
              {{ moderationLoadingKey === item.id ? '分析中...' : 'AI 审核建议' }}
            </button>
            <button class="btn-soft" @click="$emit('action', item, 'reject')">驳回</button>
          </template>
          <template v-else-if="item.type === 'placeReview'">
            <button class="btn-soft" @click="$emit('action', item, 'view')">查看</button>
            <button class="btn-danger" @click="$emit('action', item, 'delete')">删除</button>
          </template>
        </div>
      </div>
    </div>

    <div v-if="drawerVisible && drawerItem" class="review-drawer-mask" @click.self="$emit('closeDrawer')">
      <aside class="review-drawer">
        <div class="review-drawer-head">
          <h3>审核详情</h3>
          <button class="btn-soft" @click="$emit('closeDrawer')">关闭</button>
        </div>
        <div class="review-drawer-body">
          <h4 class="review-drawer-title">{{ drawerItem.title }}</h4>
          <p>类型：{{ drawerItem.typeLabel }} · 状态：{{ drawerItem.statusLabel }}</p>
          <p>发布人/举报人：{{ drawerItem.actor }}</p>
          <p>发布时间：{{ drawerItem.time }}</p>
          <img v-if="drawerItem.imageUrl" :src="drawerItem.imageUrl" alt="详情图片" class="review-drawer-image" />
          <div class="review-drawer-block">
            <strong>完整正文</strong>
            <p>{{ drawerItem.fullContent }}</p>
          </div>
          <div class="review-drawer-block">
            <strong>业务信息</strong>
            <p v-for="fact in drawerItem.detailFacts" :key="`drawer-${drawerItem.id}-${fact.label}`">
              <span>{{ fact.label }}：</span>{{ fact.value }}
            </p>
          </div>
          <div v-if="drawerItem.historyNote" class="review-drawer-block">
            <strong>历史处理备注</strong>
            <p>{{ drawerItem.historyNote }}</p>
          </div>
          <div v-if="drawerItem.status === '0' && drawerTemplates.length" class="review-drawer-block">
            <strong>处理原因模板</strong>
            <div class="template-chip-row">
              <button
                v-for="template in drawerTemplates"
                :key="template.key"
                type="button"
                class="template-chip"
                @click="$emit('action', drawerItem, template.actions.includes('done') ? 'done' : 'reject', template.key)"
              >
                {{ template.label }}
              </button>
            </div>
          </div>
          <div v-if="moderationResultKey === drawerItem.id && moderationResult" class="review-drawer-block moderation-block">
            <strong>AI 审核建议</strong>
            <p><span>风险等级：</span>{{ moderationResult.riskLevel }}</p>
            <p><span>处理建议：</span>{{ moderationResult.suggestion || '暂无建议' }}</p>
            <p><span>规范摘要：</span>{{ moderationResult.normalizedSummary || '暂无摘要' }}</p>
            <div v-if="moderationResult.riskPoints.length" class="moderation-points">
              <span>风险点：</span>
              <ul>
                <li v-for="point in moderationResult.riskPoints" :key="point">{{ point }}</li>
              </ul>
            </div>
            <p v-else><span>风险点：</span>未发现明显风险点</p>
          </div>
        </div>
        <div class="review-drawer-actions">
          <template v-if="drawerItem.type === 'activity' || drawerItem.type === 'bulletin'">
            <button class="btn-soft" :disabled="moderationLoadingKey === drawerItem.id" @click="$emit('moderation', drawerItem)">
              {{ moderationLoadingKey === drawerItem.id ? '分析中...' : 'AI 审核建议' }}
            </button>
            <button v-if="drawerItem.status === '0'" class="btn-primary" @click="$emit('action', drawerItem, 'approve')">通过</button>
            <button v-if="drawerItem.status === '0'" class="btn-soft" @click="$emit('action', drawerItem, 'reject')">驳回</button>
          </template>
          <template v-else-if="drawerItem.type === 'report'">
            <button v-if="drawerItem.status === '0'" class="btn-primary" @click="$emit('action', drawerItem, 'done')">标记已处理</button>
            <button v-if="drawerItem.status === '0'" class="btn-soft" @click="$emit('action', drawerItem, 'reject')">驳回举报</button>
            <button class="btn-danger" @click="$emit('action', drawerItem, 'delete')">删除违规内容</button>
          </template>
          <template v-else-if="drawerItem.type === 'placeReview'">
            <button class="btn-danger" @click="$emit('action', drawerItem, 'delete')">删除</button>
          </template>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.review-center,
.content-list,
.review-card-judge,
.moderation-points {
  display: grid;
  gap: 12px;
}

.module-section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.module-section-head h3,
.review-drawer-head h3,
.review-drawer-title {
  margin: 0;
}

.section-kicker {
  display: inline-flex;
  width: fit-content;
  margin-bottom: 6px;
  padding: 3px 9px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.review-filter-bar {
  display: grid;
  grid-template-columns: repeat(4, minmax(160px, 1fr)) 220px auto;
  gap: 8px;
  align-items: center;
}

.review-filter-bar input,
.review-filter-bar select {
  width: 100%;
}

.review-kpi-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  gap: 10px;
}

.review-kpi-card,
.review-card,
.review-drawer-block {
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: #fff;
  padding: 12px;
}

.review-kpi-card p,
.review-meta,
.review-summary,
.review-drawer-block p {
  margin: 0;
  color: var(--text-muted);
}

.review-kpi-card strong {
  display: block;
  margin-top: 6px;
  font-size: 26px;
}

.review-tabs,
.review-batch-bar,
.review-card-head,
.review-card-actions,
.review-check-all,
.review-select,
.template-chip-row,
.review-drawer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.review-batch-bar {
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: #f8fafc;
}

.batch-selection-summary {
  margin-right: auto;
  color: #45514a;
  font-size: 13px;
  font-weight: 700;
}

.review-card {
  display: grid;
  gap: 10px;
  border-left: 4px solid #687067;
}

.review-card.risk-high { border-left-color: #dc2626; background: #fffafa; }
.review-card.risk-medium { border-left-color: #f97316; }
.review-card.risk-low { border-left-color: #059669; }

.review-card-main {
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--line);
}

.review-title {
  margin: 6px 0 0;
  font-size: 16px;
  line-height: 1.4;
}

.review-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(220px, 1fr));
  gap: 6px 12px;
}

.review-facts p {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
}

.review-facts span,
.review-drawer-block span {
  color: var(--text-soft);
}

.status-chip {
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: var(--surface-muted);
  color: var(--text-soft);
  font-size: 12px;
}

.risk-chip,
.wait-chip {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  border-radius: 999px;
  padding: 3px 9px;
  font-size: 11px;
  font-weight: 800;
}

.risk-chip.risk-high { background: #fee2e2; color: #b91c1c; }
.risk-chip.risk-medium { background: #ffedd5; color: #c2410c; }
.risk-chip.risk-low { background: #d1fae5; color: #047857; }
.wait-chip { background: #eef2ef; color: #5c675f; }

.card-ai-summary {
  margin: 0;
  border-radius: 10px;
  padding: 8px 10px;
  background: #f0fdfa;
  color: #35584f;
  font-size: 12px;
}

.card-ai-summary strong { color: #087a6d; }

.clamp-2,
.clamp-3 {
  overflow: hidden;
  display: -webkit-box;
  -webkit-box-orient: vertical;
}

.clamp-2 { -webkit-line-clamp: 2; }
.clamp-3 { -webkit-line-clamp: 3; word-break: break-word; }

.review-drawer-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  z-index: 40;
  display: flex;
  justify-content: flex-end;
}

.review-drawer {
  width: min(640px, 92vw);
  height: 100vh;
  background: #fff;
  border-left: 1px solid var(--line);
  display: grid;
  grid-template-rows: auto 1fr auto;
}

.review-drawer-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid var(--line);
}

.review-drawer-body {
  padding: 14px 16px;
  overflow: auto;
  display: grid;
  gap: 10px;
}

.review-drawer-image {
  width: 100%;
  max-height: 220px;
  object-fit: cover;
  border: 1px solid var(--line);
  border-radius: 10px;
}

.review-drawer-block {
  background: #f8fafc;
}

.review-drawer-block strong {
  display: block;
  margin-bottom: 6px;
}

.review-drawer-block p + p {
  margin-top: 6px;
}

.template-chip {
  padding: 7px 10px;
  border: 1px solid rgba(15, 118, 110, 0.24);
  border-radius: 999px;
  background: rgba(240, 253, 250, 0.86);
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.template-chip:hover { background: #ccfbf1; }

.moderation-block {
  background: #fffdf7;
  border-color: #eadfb7;
}

.moderation-points ul {
  margin: 0;
  padding-left: 18px;
}

.review-drawer-actions {
  border-top: 1px solid var(--line);
  padding: 12px 16px;
}

@media (max-width: 900px) {
  .review-kpi-grid,
  .review-filter-bar {
    grid-template-columns: 1fr 1fr;
  }

  .review-facts {
    grid-template-columns: 1fr;
  }

  .review-drawer-mask {
    align-items: flex-end;
  }

  .review-drawer {
    width: 100vw;
    height: min(88vh, 100vh);
    border-left: 0;
    border-top: 1px solid var(--line);
    border-top-left-radius: 12px;
    border-top-right-radius: 12px;
  }
}

@media (max-width: 560px) {
  .review-kpi-grid,
  .review-filter-bar {
    grid-template-columns: 1fr;
  }

  .review-card-actions button,
  .review-batch-bar button,
  .review-tabs button {
    min-height: 40px;
  }
}
</style>
