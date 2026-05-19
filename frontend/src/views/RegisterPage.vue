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
      error.value = '两次输入的密码不一致'
      return
    }
    await api.register({ username: form.value.username, password: form.value.password })
    message.value = '注册成功，正在跳转登录页...'
    setTimeout(() => router.push('/login'), 700)
  } catch (e) {
    error.value = e.message || '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-shell">
    <div class="auth-card card">
      <section class="brand-panel">
        <p class="brand-sub">加入滑板交流网站</p>
        <h1>创建账号</h1>
        <p class="brand-desc">注册后可发布帖子、参加活动、完善你的滑板档案。</p>
        <div class="brand-points">
          <span>发帖分享动作与路线</span>
          <span>同城约板活动报名签到</span>
          <span>个人资料与头像本地上传</span>
        </div>
      </section>

      <section class="form-panel">
        <div class="section-head">
          <h2>注册</h2>
        </div>

        <label class="field-label">用户名</label>
        <input v-model.trim="form.username" placeholder="请输入用户名" />

        <label class="field-label">密码</label>
        <input v-model.trim="form.password" type="password" placeholder="请输入密码（6-20位）" />

        <label class="field-label">确认密码</label>
        <input v-model.trim="form.confirmPassword" type="password" placeholder="请再次输入密码" />

        <button class="btn-primary submit-btn" :disabled="loading" @click="submit">
          <AppIcon name="user" :size="15" />
          {{ loading ? '提交中...' : '立即注册' }}
        </button>

        <p v-if="message" class="success">{{ message }}</p>
        <p v-if="error" class="error">{{ error }}</p>

        <p class="jump-tip">
          已有账号？
          <router-link to="/login">去登录</router-link>
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
  border-right: 1px solid var(--line);
  background: linear-gradient(180deg, #ffffff 0%, #f7f8fa 100%);
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
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 9px 10px;
  background: #fff;
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
  color: #111827;
  text-decoration: underline;
}

@media (max-width: 900px) {
  .auth-card {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    border-right: 0;
    border-bottom: 1px solid var(--line);
    padding: 24px 20px;
  }

  .form-panel {
    padding: 24px 20px;
  }
}
</style>
