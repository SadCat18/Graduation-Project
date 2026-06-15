<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import SkateboardSketch from '../components/SkateboardSketch.vue'
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
      desc: '注册后可发布帖子、参加活动、使用 AI 滑板老师，并完善你的滑板档案。',
      points: ['发帖分享动作与路线', '同城约板活动报名签到', 'AI 滑板老师一对一问答', '个人资料与头像本地上传']
    }
  }
  return {
    sub: '滑板交流网站',
    title: '欢迎回来',
    desc: '登录后可发布帖子、参加同城约板、使用 AI 滑板老师并管理个人内容。',
    points: ['内容先浏览，登录后互动', 'AI 滑板老师登录后可用', '极简社区，专注滑板交流', '支持管理员后台运营']
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

function submitLoginByEnter() {
  if (loginLoading.value) return
  submitLogin()
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
  <div class="auth-shell" :class="{ 'is-register-mode': authMode === 'register' }">
    <div class="auth-card card">
      <section class="brand-panel">
        <p class="brand-sub">{{ brandContent.sub }}</p>
        <Transition name="brand-title" mode="out-in">
          <h1 :key="brandContent.title">{{ brandContent.title }}</h1>
        </Transition>
        <Transition name="brand-desc" mode="out-in">
          <p :key="brandContent.desc" class="brand-desc">{{ brandContent.desc }}</p>
        </Transition>
        <TransitionGroup name="brand-point" tag="div" class="brand-points">
          <span v-for="item in brandContent.points" :key="item">{{ item }}</span>
        </TransitionGroup>

        <div class="skate-lane" aria-hidden="true">
          <div class="lane-label">
            <span class="lane-login">登录</span>
            <span class="lane-register">注册</span>
          </div>
          <div class="motion-streak"></div>
          <div class="rail"></div>
          <div class="skate-board">
            <SkateboardSketch class="auth-skate-sketch" />
          </div>
        </div>

        <div class="feature-card">
          <div class="feature-badge">
            <AppIcon name="skateboard" :size="15" />
            新功能
          </div>
          <h3>AI 滑板老师</h3>
          <p>
            独立页面、站内主导航同级入口，适合提问新手选板、Ollie 练习、摔倒保护和活动参与建议。
          </p>
          <div class="feature-points">
            <span>分步骤回答</span>
            <span>练习建议跟进</span>
            <span>危险动作与伤情谨慎提醒</span>
          </div>
          <strong class="feature-note">登录后才能使用该功能</strong>
        </div>
      </section>

      <section class="form-panel" @touchstart.passive="onTouchStart" @touchend.passive="onTouchEnd">
        <div class="form-viewport">
          <div class="form-track" :class="{ 'is-register': authMode === 'register' }">
            <div class="form-scene login-scene">
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
                @keydown.enter.prevent="submitLoginByEnter"
              />

              <label class="field-label">密码</label>
              <input
                v-model.trim="loginForm.password"
                type="password"
                placeholder="请输入密码"
                @keydown.enter.prevent="submitLoginByEnter"
              />

              <label class="field-label">验证码</label>
              <div class="captcha-row">
                <input
                  v-model.trim="loginForm.captchaCode"
                  class="captcha-input"
                  placeholder="请输入验证码"
                  maxlength="4"
                  @keydown.enter.prevent="submitLoginByEnter"
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

            <div class="form-scene register-scene">
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
  --auth-ease: cubic-bezier(0.16, 1, 0.3, 1);
}

.auth-card {
  width: min(980px, 100%);
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 0;
  overflow: hidden;
  padding: 0;
  position: relative;
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

.brand-title-enter-active,
.brand-title-leave-active,
.brand-desc-enter-active,
.brand-desc-leave-active {
  transition: opacity 0.34s var(--auth-ease), transform 0.34s var(--auth-ease), filter 0.34s var(--auth-ease);
}

.brand-title-enter-from,
.brand-title-leave-to,
.brand-desc-enter-from,
.brand-desc-leave-to {
  opacity: 0;
  transform: translateY(14px);
  filter: blur(8px);
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
  transition: opacity 0.38s var(--auth-ease), transform 0.38s var(--auth-ease), border-color 0.22s ease;
}

.brand-point-enter-from,
.brand-point-leave-to {
  opacity: 0;
  transform: translateX(-14px);
}

.brand-point-move {
  transition: transform 0.38s var(--auth-ease);
}

.skate-lane {
  position: relative;
  height: 108px;
  margin-top: 12px;
  border: 1px solid rgba(15, 118, 110, 0.15);
  border-radius: 16px;
  background:
    linear-gradient(90deg, rgba(15, 118, 110, 0.08), rgba(17, 24, 39, 0.04)),
    #fff;
  overflow: hidden;
}

.lane-label {
  position: absolute;
  top: 16px;
  left: 20px;
  right: 20px;
  display: flex;
  justify-content: space-between;
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 700;
}

.lane-label span {
  transition: opacity 0.28s ease, color 0.28s ease;
}

.lane-login,
.is-register-mode .lane-register {
  color: var(--accent);
  opacity: 1;
}

.lane-register,
.is-register-mode .lane-login {
  color: var(--text-muted);
  opacity: 0.48;
}

.motion-streak {
  position: absolute;
  left: 54px;
  right: 54px;
  bottom: 46px;
  height: 2px;
  background: linear-gradient(90deg, transparent, rgba(15, 118, 110, 0.32), transparent);
  opacity: 0.7;
  transform: translateX(-22%);
  transition: transform 0.78s var(--auth-ease);
}

.is-register-mode .motion-streak {
  transform: translateX(22%);
}

.rail {
  position: absolute;
  left: 28px;
  right: 28px;
  bottom: 30px;
  height: 3px;
  border-radius: 999px;
  background: #d1d5db;
}

.rail::before {
  content: '';
  position: absolute;
  inset: 0;
  width: 44%;
  border-radius: inherit;
  background: var(--accent);
  transform-origin: left;
  transition: transform 0.78s var(--auth-ease);
}

.is-register-mode .rail::before {
  transform: translateX(128%);
}

.skate-board {
  position: absolute;
  left: 34px;
  bottom: 28px;
  width: 152px;
  height: 66px;
  filter: drop-shadow(0 15px 14px rgba(17, 24, 39, 0.18));
  transform-origin: 50% 100%;
  transition: transform 0.78s var(--auth-ease);
  animation: idleRide var(--skate-motion-duration) ease-in-out infinite;
}

.is-register-mode .skate-board {
  transform: translateX(218px) rotate(-3deg);
}

.auth-skate-sketch {
  width: 100%;
  height: auto;
  --sketch-deck-fill: rgba(255, 255, 255, 0.98);
  --sketch-line: rgba(31, 41, 55, 0.86);
  --sketch-line-soft: rgba(31, 41, 55, 0.64);
}

@keyframes idleRide {
  0%,
  100% {
    translate: 0 0;
  }

  50% {
    translate: 0 -3px;
  }
}

.feature-card {
  margin-top: 10px;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid rgba(15, 118, 110, 0.18);
  background:
    radial-gradient(circle at top right, rgba(15, 118, 110, 0.12), transparent 34%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(245, 248, 247, 0.96));
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
  display: grid;
  gap: 10px;
}

.feature-badge {
  width: fit-content;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.1);
  color: var(--accent);
  font-size: 12px;
  font-weight: 700;
}

.feature-card h3 {
  margin: 0;
  font-size: 22px;
}

.feature-card p {
  margin: 0;
  color: var(--text-soft);
  line-height: 1.65;
}

.feature-points {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.feature-points span {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: rgba(255, 255, 255, 0.82);
  color: var(--text);
  font-size: 13px;
}

.feature-note {
  color: #0f172a;
  font-size: 14px;
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
  transition: transform 0.7s var(--auth-ease);
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
  opacity: 0.42;
  filter: blur(5px);
  transform: scale(0.965);
  transition: opacity 0.42s ease, filter 0.42s ease, transform 0.7s var(--auth-ease);
}

.auth-shell:not(.is-register-mode) .login-scene,
.is-register-mode .register-scene {
  opacity: 1;
  filter: blur(0);
  transform: scale(1);
}

.form-scene > * {
  opacity: 0;
  transform: translateX(18px);
  transition: opacity 0.48s var(--auth-ease), transform 0.48s var(--auth-ease);
}

.auth-shell:not(.is-register-mode) .login-scene > *,
.is-register-mode .register-scene > * {
  opacity: 1;
  transform: translateX(0);
}

.form-scene > *:nth-child(2) {
  transition-delay: 0.04s;
}

.form-scene > *:nth-child(3) {
  transition-delay: 0.08s;
}

.form-scene > *:nth-child(4) {
  transition-delay: 0.12s;
}

.form-scene > *:nth-child(5) {
  transition-delay: 0.16s;
}

.form-scene > *:nth-child(6) {
  transition-delay: 0.2s;
}

.form-scene > *:nth-child(7) {
  transition-delay: 0.24s;
}

.form-scene > *:nth-child(8) {
  transition-delay: 0.28s;
}

.form-scene > *:nth-child(9) {
  transition-delay: 0.32s;
}

.form-scene > *:nth-child(10) {
  transition-delay: 0.36s;
}

.form-scene > *:nth-child(11) {
  transition-delay: 0.4s;
}

.form-scene > *:nth-child(12) {
  transition-delay: 0.44s;
}

.login-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface-muted);
  margin-bottom: 8px;
}

.login-switch button {
  border: none;
  background: transparent;
  color: var(--text-soft);
  padding: 10px 8px;
}

.login-switch button.active {
  background: #111827;
  color: #fff;
  border-radius: 8px;
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
  border-radius: 8px;
  border: 1px solid var(--line);
  object-fit: cover;
  cursor: pointer;
  background: #fff;
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
  color: #111827;
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
    border-bottom: 1px solid var(--line);
    padding: 24px 20px;
  }

  .form-panel {
    padding: 24px 20px;
  }

  .is-register-mode .skate-board {
    transform: translateX(178px) rotate(-3deg);
  }
}

@media (max-width: 520px) {
  .skate-lane {
    height: 96px;
  }

  .skate-board {
    left: 24px;
    width: 128px;
  }

  .is-register-mode .skate-board {
    transform: translateX(132px) rotate(-3deg);
  }

  .captcha-row {
    grid-template-columns: 1fr;
  }

  .captcha-image {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .brand-title-enter-active,
  .brand-title-leave-active,
  .brand-desc-enter-active,
  .brand-desc-leave-active,
  .brand-points span,
  .motion-streak,
  .rail::before,
  .skate-board,
  .form-track,
  .form-scene,
  .form-scene > * {
    animation: none;
    transition-duration: 0.01ms;
  }
}
</style>
