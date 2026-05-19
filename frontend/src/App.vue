<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from './components/AppIcon.vue'
import { clearAuth, getAuthChangedEventName, getName, getRole, getToken } from './utils/auth'

const route = useRoute()
const router = useRouter()

const isAuthPage = computed(() => ['/login', '/register'].includes(route.path))
const authState = ref({
  token: '',
  role: '',
  name: ''
})

function syncAuthState() {
  authState.value = {
    token: getToken(),
    role: getRole(),
    name: getName()
  }
}

const hasToken = computed(() => !!authState.value.token)
const role = computed(() => authState.value.role)
const username = computed(() => authState.value.name)

const navItems = computed(() => {
  const base = [
    { to: '/', label: '首页', icon: 'home' },
    { to: '/community', label: '社区帖子', icon: 'community' },
    { to: '/activities', label: '同城约板', icon: 'activity' },
    { to: '/bulletins', label: '社区快讯', icon: 'news' }
  ]
  if (hasToken.value) base.push({ to: '/profile', label: '个人中心', icon: 'user' })
  if (role.value === 'ADMIN') base.push({ to: '/admin', label: '后台管理', icon: 'admin' })
  return base
})

function logout() {
  clearAuth()
  syncAuthState()
  router.replace('/login')
}

function handleAuthChange() {
  syncAuthState()
}

onMounted(() => {
  syncAuthState()
  window.addEventListener('storage', handleAuthChange)
  window.addEventListener(getAuthChangedEventName(), handleAuthChange)
})

onBeforeUnmount(() => {
  window.removeEventListener('storage', handleAuthChange)
  window.removeEventListener(getAuthChangedEventName(), handleAuthChange)
})
</script>

<template>
  <div class="app-shell">
    <header v-if="!isAuthPage" class="topbar">
      <div class="brand" @click="$router.push('/')">
        <span class="brand-icon"><AppIcon name="skateboard" :size="20" /></span>
        <span>滑板交流网站</span>
      </div>

      <nav class="nav-links">
        <router-link
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="nav-link"
        >
          <AppIcon :name="item.icon" :size="16" />
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="nav-right">
        <template v-if="hasToken">
          <span class="user-name">{{ username }}</span>
          <button type="button" class="btn-soft" @click="logout">
            <AppIcon name="logout" :size="15" />
            退出
          </button>
        </template>
        <template v-else>
          <router-link class="auth-btn" to="/login">
            <AppIcon name="login" :size="15" />
            登录
          </router-link>
          <router-link class="auth-btn auth-btn-primary" to="/register">
            注册
          </router-link>
        </template>
      </div>
    </header>

    <main class="main-container">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  background:
    linear-gradient(120deg, rgba(111, 126, 139, .12) 0 16%, transparent 17%),
    linear-gradient(180deg, rgba(252, 252, 250, .98) 0%, rgba(242, 243, 240, .98) 100%);
  border-bottom: 2px solid var(--line-strong);
  box-shadow: 0 3px 0 rgba(142, 164, 191, 0.45);
  padding: 10px 18px;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 14px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  color: var(--text-1);
  cursor: pointer;
}

.brand-icon {
  display: inline-grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: var(--radius-sm);
  background: linear-gradient(90deg, #d8e0e6 0%, #c1ced7 65%, #aab8c4 100%);
  color: #0f1a23;
  border: 2px solid var(--line-strong);
  box-shadow: var(--shadow-1);
}

.nav-links {
  display: flex;
  justify-content: center;
  gap: 6px;
  flex-wrap: wrap;
}

.nav-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 12px;
  border-radius: var(--radius-sm);
  color: var(--text-2);
  border: 2px solid var(--line-strong);
  background: linear-gradient(180deg, #ffffff 0%, #edf4ff 100%);
  color: #1a2b42;
  font-size: 14px;
  box-shadow: var(--shadow-1);
  transition: transform var(--motion-base) var(--ease-ui), filter var(--motion-base) var(--ease-ui), border-color var(--motion-base) var(--ease-ui);
}

.nav-link:hover {
  color: var(--text-1);
  border-color: var(--line-strong);
  transform: translateY(-2px);
}

.nav-link.router-link-active {
  background: linear-gradient(95deg, #d3dde4 0%, #bdccd6 58%, #a8b9c6 100%);
  border: 2px solid var(--line-strong);
  color: #101a23;
  box-shadow: var(--shadow-2);
  font-weight: 700;
}

.nav-right {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.user-name {
  color: var(--text-2);
  font-size: 14px;
}

.auth-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 11px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--line-strong);
  background: linear-gradient(180deg, #ffffff 0%, #edf4ff 100%);
  color: var(--text-2);
  font-size: 14px;
  box-shadow: var(--shadow-1);
}

.auth-btn-primary {
  background: linear-gradient(95deg, #d3dde4 0%, #bdccd6 58%, #a8b9c6 100%);
  border: 2px solid var(--line-strong);
  color: #101a23;
  font-weight: 700;
}

@media (max-width: 980px) {
  .topbar {
    grid-template-columns: 1fr;
    gap: 10px;
    padding: 10px 12px;
  }

  .nav-links {
    justify-content: flex-start;
    overflow-x: auto;
    flex-wrap: nowrap;
    padding-bottom: 2px;
  }

  .nav-right {
    justify-content: flex-end;
  }
}
</style>

