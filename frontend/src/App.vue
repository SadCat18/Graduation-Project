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
  background: rgba(243, 244, 246, 0.92);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid rgba(148, 163, 184, 0.34);
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.08);
  padding: 12px 18px;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 16px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--text);
  cursor: pointer;
}

.brand-icon {
  display: inline-grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: #111827;
  color: #fff;
}

.nav-links {
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}

.nav-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  border-radius: 11px;
  color: var(--text-soft);
  border: 1px solid rgba(148, 163, 184, 0.32);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.86), rgba(241, 245, 249, 0.62));
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  font-size: 14px;
  box-shadow: 0 6px 14px rgba(15, 23, 42, 0.08), inset 0 1px 0 rgba(255, 255, 255, 0.82);
  transition: all 0.25s ease;
}

.nav-link:hover {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(241, 245, 249, 0.72));
  border-color: rgba(100, 116, 139, 0.4);
  color: var(--text);
  transform: translateY(-1px);
  box-shadow: 0 10px 18px rgba(15, 23, 42, 0.12), inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.nav-link.router-link-active {
  background: linear-gradient(180deg, rgba(45, 62, 92, 0.94), rgba(31, 41, 55, 0.92));
  border-color: rgba(45, 62, 92, 0.6);
  color: #f8fafc;
  box-shadow: 0 10px 20px rgba(15, 23, 42, 0.2);
}

.nav-right {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.user-name {
  color: var(--text-soft);
  font-size: 14px;
}

.auth-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 11px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.62);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.76), rgba(255, 255, 255, 0.5));
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  color: var(--text);
  font-size: 14px;
  box-shadow: 0 4px 10px rgba(15, 23, 42, 0.08), inset 0 1px 0 rgba(255, 255, 255, 0.75);
}

.auth-btn-primary {
  background: linear-gradient(180deg, rgba(45, 62, 92, 0.94), rgba(31, 41, 55, 0.92));
  border-color: rgba(45, 62, 92, 0.56);
  color: #fff;
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

