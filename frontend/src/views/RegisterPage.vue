<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { api } from '../api'

const router = useRouter()
const form = ref({ username: '', password: '', confirmPassword: '' })
const loading = ref(false)
const message = ref('')
const error = ref('')

async function submit() {
  loading.value = true
  message.value = ''
  error.value = ''
  try {
    if (form.value.password !== form.value.confirmPassword) {
      error.value = '涓ゆ杈撳叆鐨勫瘑鐮佷笉涓€鑷?
      return
    }
    await api.register({ username: form.value.username, password: form.value.password })
    message.value = '娉ㄥ唽鎴愬姛锛屾鍦ㄨ烦杞櫥褰曢〉...'
    setTimeout(() => router.push('/login'), 700)
  } catch (e) {
    error.value = e.message || '娉ㄥ唽澶辫触锛岃绋嶅悗閲嶈瘯'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-shell">
    <div class="auth-card card">
      <section class="brand-panel">
        <p class="brand-sub">鍔犲叆婊戞澘浜ゆ祦缃戠珯</p>
        <h1>鍒涘缓璐﹀彿</h1>
        <p class="brand-desc">娉ㄥ唽鍚庡彲鍙戝竷甯栧瓙銆佸弬鍔犳椿鍔ㄣ€佸畬鍠勪綘鐨勬粦鏉挎。妗堛€?/p>
        <div class="brand-points">
          <span>鍙戝笘鍒嗕韩鍔ㄤ綔涓庤矾绾?/span>
          <span>鍚屽煄绾︽澘娲诲姩鎶ュ悕绛惧埌</span>
          <span>涓汉璧勬枡涓庡ご鍍忔湰鍦颁笂浼?/span>
        </div>
      </section>

      <section class="form-panel">
        <div class="section-head">
          <h2>娉ㄥ唽</h2>
        </div>

        <label class="field-label">鐢ㄦ埛鍚?/label>
        <input v-model.trim="form.username" placeholder="璇疯緭鍏ョ敤鎴峰悕" />

        <label class="field-label">瀵嗙爜</label>
        <input v-model.trim="form.password" type="password" placeholder="璇疯緭鍏ュ瘑鐮侊紙6-20浣嶏級" />

        <label class="field-label">纭瀵嗙爜</label>
        <input v-model.trim="form.confirmPassword" type="password" placeholder="璇峰啀娆¤緭鍏ュ瘑鐮? />

        <button class="btn-primary submit-btn" :disabled="loading" @click="submit">
          <AppIcon name="user" :size="15" />
          {{ loading ? '鎻愪氦涓?..' : '绔嬪嵆娉ㄥ唽' }}
        </button>

        <p v-if="message" class="success">{{ message }}</p>
        <p v-if="error" class="error">{{ error }}</p>

        <p class="jump-tip">
          宸叉湁璐﹀彿锛?          <router-link to="/login">鍘荤櫥褰?/router-link>
        </p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.auth-shell {
  min-height: calc(100vh - 120px);
  display: grid;
  place-items: center;
  padding: 16px 0;
}

.auth-card {
  width: min(980px, 100%);
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 0;
  overflow: hidden;
  padding: 0;
}

.brand-panel {
  padding: 42px;
  border-right: 1px solid var(--line-strong);
  background: var(--bg-1);
  display: grid;
  align-content: center;
  gap: 12px;
}

.brand-sub {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.brand-panel h1 {
  margin: 0;
  font-size: 36px;
}

.brand-desc {
  margin: 0;
  color: var(--text-soft);
}

.brand-points {
  margin-top: 8px;
  display: grid;
  gap: 8px;
}

.brand-points span {
  font-size: 14px;
  color: var(--text-soft);
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-md);
  padding: 9px 10px;
  background: var(--bg-2);
}

.form-panel {
  padding: 36px;
  display: grid;
  gap: 8px;
  align-content: center;
}

.form-panel h2 {
  margin: 0;
}

.field-label {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--text-soft);
}

.submit-btn {
  margin-top: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.jump-tip {
  margin: 8px 0 0;
  color: var(--text-soft);
}

.jump-tip a {
  color: var(--brand);
  text-decoration: underline;
}

@media (max-width: 900px) {
  .auth-card {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    border-right: 0;
    border-bottom: 1px solid var(--line-strong);
    padding: 24px 20px;
  }

  .form-panel {
    padding: 24px 20px;
  }
}
</style>


