<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { api } from '../api'
import { setAuth } from '../utils/auth'

const router = useRouter()
const route = useRoute()

const authMode = ref('login')
const loginRole = ref('USER')
const loginForm = ref({ account: '', password: '', captchaCode: '', captchaId: '' })
const loginCaptchaSrc = ref('')
const loginLoading = ref(false)
const loginError = ref('')

const registerForm = ref({ username: '', phone: '', password: '', confirmPassword: '' })
const registerLoading = ref(false)
const registerMessage = ref('')
const registerError = ref('')

const touchStartX = ref(0)
const touchStartY = ref(0)

const brandContent = computed(() => {
  if (authMode.value === 'register') {
    return {
      sub: '加入滑板交流网站',
      title: '创建账号',
      desc: '注册后可发布帖子、参加活动、完善你的滑板档案。',
      points: ['发帖分享动作与路线', '同城约板活动报名签到', '个人资料与头像本地上传']
    }
  }
  return {
    sub: '滑板交流网站',
    title: '欢迎回来',
    desc: '登录后可发布帖子、参加同城约板、管理个人内容。',
    points: ['内容先浏览，登录后互动', '极简社区，专注滑板交流', '支持管理员后台运营']
  }
})

function refreshCaptcha() {
  loginForm.value.captchaId = `${Date.now()}${Math.random().toString(36).slice(2, 10)}`
  loginCaptchaSrc.value = `/api/auth/captcha?captchaId=${loginForm.value.captchaId}&t=${Date.now()}`
}

function setMode(mode, syncRoute = true) {
  authMode.value = mode
  if (!syncRoute) return
  const targetPath = mode === 'register' ? '/register' : '/login'
  if (route.path !== targetPath) {
    router.replace(targetPath)
  }
}

function onTouchStart(event) {
  const touch = event.changedTouches[0]
  touchStartX.value = touch.clientX
  touchStartY.value = touch.clientY
}

function onTouchEnd(event) {
  const touch = event.changedTouches[0]
  const deltaX = touch.clientX - touchStartX.value
  const deltaY = touch.clientY - touchStartY.value

  if (Math.abs(deltaX) < 50 || Math.abs(deltaX) < Math.abs(deltaY)) {
    return
  }

  if (deltaX < 0 && authMode.value === 'login') {
    setMode('register')
  } else if (deltaX > 0 && authMode.value === 'register') {
    setMode('login')
  }
}

async function submitLogin() {
  loginLoading.value = true
  loginError.value = ''
  try {
    const account = (loginForm.value.account || '').trim()
    const inferredLoginType = /^1\d{10}$/.test(account) ? 'phone' : 'username'
    const payload = {
      ...loginForm.value,
      account,
      loginType: inferredLoginType
    }
    const data = loginRole.value === 'ADMIN'
      ? await api.adminLogin(loginForm.value)
      : await api.userLogin(payload)
    setAuth(data.token, data.role, data.name)
    router.push(data.role === 'ADMIN' ? '/admin' : '/')
  } catch (e) {
    loginError.value = e.message || '登录失败，请检查账号、密码或验证码'
    loginForm.value.captchaCode = ''
    refreshCaptcha()
  } finally {
    loginLoading.value = false
  }
}

async function submitRegister() {
  registerLoading.value = true
  registerMessage.value = ''
  registerError.value = ''
  try {
    const phone = registerForm.value.phone.trim()
    if (!/^1\d{10}$/.test(phone)) {
      registerError.value = '手机号格式不正确，请输入11位手机号'
      return
    }

    if (registerForm.value.password !== registerForm.value.confirmPassword) {
      registerError.value = '两次输入的密码不一致'
      return
    }

    await api.register({
      username: registerForm.value.username,
      phone,
      password: registerForm.value.password
    })

    registerMessage.value = '注册成功，请立即登录'
    setMode('login')
  } catch (e) {
    registerError.value = e.message || '注册失败，请稍后重试'
  } finally {
    registerLoading.value = false
  }
}

onMounted(() => {
  refreshCaptcha()
  setMode(route.path === '/register' ? 'register' : 'login', false)
})

watch(
  () => route.path,
  (path) => {
    setMode(path === '/register' ? 'register' : 'login', false)
  }
)
</script>

<template>
  <div class="auth-shell">
    <div class="auth-card card">
      <section class="brand-panel">
        <p class="brand-sub">{{ brandContent.sub }}</p>
        <h1>{{ brandContent.title }}</h1>
        <p class="brand-desc">{{ brandContent.desc }}</p>
        <div class="brand-points">
          <span v-for="item in brandContent.points" :key="item">{{ item }}</span>
        </div>
      </section>

      <section class="form-panel" @touchstart.passive="onTouchStart" @touchend.passive="onTouchEnd">
        <div class="form-viewport">
          <div class="form-track" :class="{ 'is-register': authMode === 'register' }">
            <div class="form-scene">
              <div class="section-head">
                <h2>登录</h2>
              </div>

              <div class="login-switch">
                <button :class="{ active: loginRole === 'USER' }" @click="loginRole = 'USER'">用户登录</button>
                <button :class="{ active: loginRole === 'ADMIN' }" @click="loginRole = 'ADMIN'">管理员登录</button>
              </div>

              <label class="field-label">账号</label>
              <input
                v-model.trim="loginForm.account"
                :placeholder="loginRole === 'ADMIN' ? '请输入管理员账号' : '请输入用户名或手机号'"
              />

              <label class="field-label">密码</label>
              <input v-model.trim="loginForm.password" type="password" placeholder="请输入密码" />

              <label class="field-label">验证码</label>
              <div class="captcha-row">
                <input
                  v-model.trim="loginForm.captchaCode"
                  class="captcha-input"
                  placeholder="请输入验证码"
                  maxlength="4"
                />
                <img
                  class="captcha-image"
                  :src="loginCaptchaSrc"
                  alt="验证码"
                  title="点击刷新验证码"
                  @click="refreshCaptcha"
                />
              </div>
              <button type="button" class="captcha-refresh" @click="refreshCaptcha">看不清？换一张</button>

              <button class="btn-primary submit-btn" :disabled="loginLoading" @click="submitLogin">
                <AppIcon name="login" :size="15" />
                {{ loginLoading ? '登录中...' : '立即登录' }}
              </button>

              <p v-if="loginError" class="error">{{ loginError }}</p>

              <p class="jump-tip">
                还没有账号？
                <button type="button" class="jump-link" @click="setMode('register')">马上注册</button>
              </p>
            </div>

            <div class="form-scene">
              <div class="section-head">
                <h2>注册</h2>
              </div>

              <label class="field-label">用户名</label>
              <input v-model.trim="registerForm.username" placeholder="请输入用户名" />

              <label class="field-label">手机号</label>
              <input v-model.trim="registerForm.phone" maxlength="11" placeholder="请输入11位手机号" />

              <label class="field-label">密码</label>
              <input v-model.trim="registerForm.password" type="password" placeholder="请输入密码（6-20位）" />

              <label class="field-label">确认密码</label>
              <input v-model.trim="registerForm.confirmPassword" type="password" placeholder="请再次输入密码" />

              <button class="btn-primary submit-btn register-submit-btn" :disabled="registerLoading" @click="submitRegister">
                <AppIcon name="user" :size="15" />
                {{ registerLoading ? '提交中...' : '立即注册' }}
              </button>

              <p v-if="registerMessage" class="success">{{ registerMessage }}</p>
              <p v-if="registerError" class="error">{{ registerError }}</p>

              <p class="jump-tip">
                已有账号？
                <button type="button" class="jump-link" @click="setMode('login')">立即登录</button>
              </p>
            </div>
          </div>
        </div>
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
  border-right: 2px solid var(--line-strong);
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
  border: 2px solid var(--line-strong);
  border-radius: 2px;
  padding: 9px 10px;
  background: var(--bg-2);
}

.form-panel {
  padding: 36px;
  display: flex;
  align-items: center;
}

.form-panel h2 {
  margin: 0;
}

.form-viewport {
  width: 100%;
  overflow: hidden;
}

.form-track {
  width: 200%;
  display: flex;
  transform: translateX(0);
  transition: transform 300ms ease-in-out;
  will-change: transform;
}

.form-track.is-register {
  transform: translateX(-50%);
}

.form-scene {
  width: 50%;
  display: grid;
  gap: 8px;
  align-content: center;
  padding: 2px;
}

.login-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
  padding: 4px;
  border: 2px solid var(--line-strong);
  border-radius: var(--radius-md);
  background: var(--bg-2);
  margin-bottom: 8px;
}

.login-switch button {
  border: none;
  background: transparent;
  color: var(--text-soft);
  padding: 10px 8px;
}

.login-switch button.active {
  background: var(--brand);
  color: #07131a;
  border-radius: 2px;
}

.field-label {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--text-soft);
}

.captcha-row {
  display: grid;
  grid-template-columns: 1fr 120px;
  gap: 10px;
  align-items: center;
}

.captcha-input {
  margin: 0;
}

.captcha-image {
  width: 120px;
  height: 42px;
  border-radius: 2px;
  border: 2px solid var(--line-strong);
  object-fit: cover;
  cursor: pointer;
  background: var(--bg-2);
}

.captcha-refresh {
  justify-self: flex-end;
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--text-soft);
  font-size: 13px;
}

.submit-btn {
  margin-top: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.register-submit-btn,
.register-submit-btn:hover:not(:disabled),
.register-submit-btn:active:not(:disabled) {
  box-shadow: none;
}

.jump-tip {
  margin: 8px 0 0;
  color: var(--text-soft);
}

.jump-link {
  border: 0;
  background: transparent;
  color: var(--brand);
  text-decoration: underline;
  padding: 0;
  font: inherit;
  cursor: pointer;
  box-shadow: none !important;
  outline: none;
}

.jump-link:focus,
.jump-link:focus-visible,
.jump-link:active {
  box-shadow: none !important;
  outline: none;
}

@media (max-width: 900px) {
  .auth-card {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    border-right: 0;
    border-bottom: 2px solid var(--line-strong);
    padding: 24px 20px;
  }

  .form-panel {
    padding: 24px 20px;
  }
}
</style>
