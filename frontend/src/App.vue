<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { localImageFallback } from './utils/imageFallback'
import AppIcon from './components/AppIcon.vue'
import SiteNavigation from './components/SiteNavigation.vue'
import AppToast from './components/AppToast.vue'
import { api } from './api'
import { clearAuth, getAuthChangedEventName, getName, getRole, getToken } from './utils/auth'
import { warmRouteChunk } from './utils/routePrefetch'
import { installToastAlertBridge, restoreToastAlertBridge } from './utils/toast'

const route = useRoute()
const router = useRouter()

const isAuthPage = computed(() => ['/login', '/register'].includes(route.path))
const useWideLayout = computed(() => route.meta.wideLayout === true)
const isMessagesPage = computed(() => route.path.startsWith('/messages'))
const authState = ref({
  token: '',
  role: '',
  name: ''
})
const privateUnreadCount = ref(0)
let unreadTimer = null

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
  const base = []
  if (hasToken.value) {
    base.push({ to: '/messages', label: '私信', icon: 'message', badge: privateUnreadCount.value })
    base.push({ to: '/profile', label: '个人中心', icon: 'user' })
  }
  if (role.value === 'ADMIN') base.push({ to: '/admin', label: '后台管理', icon: 'admin' })
  return base
})

function logout() {
  clearAuth()
  syncAuthState()
  privateUnreadCount.value = 0
  router.replace('/login')
}

function handleAuthChange() {
  syncAuthState()
  loadPrivateUnreadCount()
}

function prefetchRoute(path) {
  warmRouteChunk(path)
}

async function loadPrivateUnreadCount() {
  if (!getToken()) {
    privateUnreadCount.value = 0
    return
  }
  try {
    const result = await api.privateUnreadCount()
    privateUnreadCount.value = Number(result?.unreadCount || 0)
  } catch (_) {
    privateUnreadCount.value = 0
  }
}

function startUnreadPolling() {
  stopUnreadPolling()
  unreadTimer = window.setInterval(loadPrivateUnreadCount, 30000)
}

function stopUnreadPolling() {
  if (unreadTimer) {
    window.clearInterval(unreadTimer)
    unreadTimer = null
  }
}

onMounted(() => {
  syncAuthState()
  installToastAlertBridge()
  window.addEventListener('storage', handleAuthChange)
  window.addEventListener(getAuthChangedEventName(), handleAuthChange)
  loadPrivateUnreadCount()
  startUnreadPolling()
})

onBeforeUnmount(() => {
  window.removeEventListener('storage', handleAuthChange)
  window.removeEventListener(getAuthChangedEventName(), handleAuthChange)
  stopUnreadPolling()
  restoreToastAlertBridge()
})

watch(() => route.path, () => {
  loadPrivateUnreadCount()
})
</script>

<template>
  <div class="app-shell">
    <header v-if="!isAuthPage" class="topbar">
      <RouterLink class="brand" to="/" aria-label="滑板知识网站首页">
        <span class="brand-icon"><AppIcon name="skateboard" :size="20" /></span>
        <span>滑板知识网站</span>
      </RouterLink>

      <SiteNavigation :personal="navItems" />

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

    <main :class="['main-container', { 'main-container-wide': useWideLayout, 'main-container-messages': isMessagesPage }]">
      <RouterView v-slot="{ Component, route: viewRoute }">
        <Transition name="route-glide" mode="out-in">
          <KeepAlive include="HomePage,CommunityPage,ActivitiesPage,BulletinListPage">
            <component :is="Component" :key="viewRoute.path" :data-route-path="viewRoute.path" />
          </KeepAlive>
        </Transition>
      </RouterView>
    </main>

    <SiteNavigation v-if="!isAuthPage" mobile :personal="navItems" />

    <footer class="site-footer">
      <a
        class="beian-link"
        href="https://beian.miit.gov.cn/"
        target="_blank"
        rel="noopener noreferrer"
      >
        浙ICP备2026040697号-1
      </a>
      <span class="footer-divider" aria-hidden="true">|</span>
      <a
        class="beian-link beian-police"
        href="https://beian.mps.gov.cn/#/query/webSearch?code=33080202000739"
        target="_blank"
        rel="noopener noreferrer"
      >
        <img
          class="police-icon"
          @error="localImageFallback($event, '/fallback/decoration.svg')"
          src="https://www.beian.gov.cn/img/ghs.png"
          alt=""
          width="20"
          height="20"
        >
        <span>浙公网安备33080202000739号</span>
      </a>
    </footer>

    <AppToast />
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  background: rgba(243, 244, 246, 0.98);
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
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  border-radius: 11px;
  color: var(--text-soft);
  border: 1px solid rgba(148, 163, 184, 0.32);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.86), rgba(241, 245, 249, 0.62));
  font-size: 14px;
  box-shadow: 0 6px 14px rgba(15, 23, 42, 0.08), inset 0 1px 0 rgba(255, 255, 255, 0.82);
  transition:
    transform 0.24s var(--motion-ease),
    box-shadow 0.24s ease,
    border-color 0.24s ease,
    background-color 0.24s ease,
    color 0.24s ease;
}

.nav-badge,
.mobile-nav-badge {
  display: inline-grid;
  place-items: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #ef4444;
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  line-height: 1;
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
  color: var(--text);
  font-size: 14px;
  box-shadow: 0 4px 10px rgba(15, 23, 42, 0.08), inset 0 1px 0 rgba(255, 255, 255, 0.75);
}

.auth-btn-primary {
  background: linear-gradient(180deg, rgba(45, 62, 92, 0.94), rgba(31, 41, 55, 0.92));
  border-color: rgba(45, 62, 92, 0.56);
  color: #fff;
}

.main-container {
  flex: 1;
}

.main-container-wide {
  max-width: 1680px;
  width: 100%;
}

.main-container-messages {
  max-width: none;
  padding: 0;
}

.site-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 16px 18px 22px;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.4;
  border-top: 1px solid rgba(209, 213, 219, 0.62);
  background: rgba(248, 250, 252, 0.72);
}

.beian-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: inherit;
  transition: color 0.2s ease;
}

.beian-link:hover {
  color: var(--text);
}

.police-icon {
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
}

.footer-divider {
  color: #cbd5e1;
}

.route-glide-enter-active,
.route-glide-leave-active {
  transition: opacity 0.16s var(--motion-ease), transform 0.16s var(--motion-ease);
}

.route-glide-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.route-glide-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.mobile-bottom-nav {
  display: none;
}

@media (max-width: 980px) {
  .topbar {
    grid-template-columns: 1fr auto;
    gap: 10px;
    padding: 10px 12px;
  }

  .nav-links {
    display: none;
  }

  .nav-right {
    justify-content: flex-end;
  }

  .main-container {
    padding-bottom: 92px;
  }

  .main-container-messages {
    padding: 0;
  }

  .mobile-bottom-nav {
    position: fixed;
    left: 12px;
    right: 12px;
    bottom: max(10px, env(safe-area-inset-bottom));
    z-index: 40;
    display: grid;
    grid-template-columns: repeat(6, minmax(0, 1fr));
    gap: 4px;
    padding: 7px;
    border: 1px solid rgba(255, 255, 255, 0.72);
    border-radius: 18px;
    background: rgba(255, 255, 255, 0.98);
    box-shadow: 0 18px 38px rgba(18, 21, 19, 0.18);
  }

  .mobile-bottom-link {
    position: relative;
    display: grid;
    place-items: center;
    gap: 3px;
    min-width: 0;
    min-height: 50px;
    border-radius: 13px;
    color: var(--text-muted);
    font-size: 11px;
    font-weight: 700;
    transition: background-color 0.2s ease, color 0.2s ease, transform 0.2s var(--motion-ease);
  }

  .mobile-bottom-link span {
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-bottom-link.router-link-active {
    background: #151917;
    color: #fff;
    transform: translateY(-1px);
  }

  .mobile-nav-badge {
    position: absolute;
    top: 5px;
    right: 10px;
    min-width: 17px;
    height: 17px;
    font-size: 10px;
  }

  .site-footer {
    padding: 14px 12px 96px;
    gap: 6px 8px;
    font-size: 12px;
  }

  .footer-divider {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .route-glide-enter-active,
  .route-glide-leave-active {
    transition-duration: 0.01ms;
  }
}
</style>

