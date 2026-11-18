<template>
  <header class="app-header">
    <div class="header-inner">
      <router-link class="header-brand" to="/monitor" aria-label="回到实时监控">
        <BrandMark />
      </router-link>

      <nav v-if="userStore.isLoggedIn" class="header-nav" aria-label="主导航">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-link"
          :class="{ 'is-active': isActive(item.path) }"
          :aria-current="isActive(item.path) ? 'page' : undefined"
        >
          {{ item.label }}
        </router-link>
      </nav>

      <div class="header-actions">
        <template v-if="userStore.isLoggedIn">
          <span class="header-email" :title="userStore.userInfo?.email">
            {{ maskedEmail }}
          </span>
          <button type="button" class="text-button" @click="handleLogout">
            退出登录
          </button>
        </template>
        <template v-else>
          <router-link class="ghost-button" to="/login">秘钥查看</router-link>
          <router-link class="solid-button" to="/auth/login">登录</router-link>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import BrandMark from '@/components/BrandMark.vue'
import { maskEmail } from '@/utils/format'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const navItems = [
  { path: '/monitor', label: '实时监控' },
  { path: '/profile', label: '个人中心' }
]

// 邮箱在顶栏只显示脱敏结果，避免截图/投屏泄露完整账号
const maskedEmail = computed(() => maskEmail(userStore.userInfo?.email || ''))

const isActive = (path: string): boolean => route.path === path

const handleLogout = async () => {
  try {
    await userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  } catch {
    // 错误提示已由请求拦截器统一弹出
  }
}
</script>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 100;
  height: var(--header-height);
  background: color-mix(in srgb, var(--bg) 88%, transparent);
  backdrop-filter: saturate(160%) blur(12px);
  border-bottom: 1px solid var(--border);
}

/* 不支持 backdrop-filter 时退化为实色，避免文字压在内容上 */
@supports not (backdrop-filter: blur(12px)) {
  .app-header {
    background: var(--bg);
  }
}

.header-inner {
  height: 100%;
  max-width: var(--content-max);
  margin: 0 auto;
  padding: 0 var(--space-5);
  display: flex;
  align-items: center;
  gap: var(--space-5);
}

.header-brand {
  display: inline-flex;
  align-items: center;
  text-decoration: none;
  flex-shrink: 0;
}

.header-nav {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  flex: 1;
}

.nav-link {
  position: relative;
  display: inline-flex;
  align-items: center;
  height: 36px;
  padding: 0 var(--space-3);
  border-radius: var(--radius-sm);
  font-size: var(--text-base);
  font-weight: 500;
  color: var(--text-muted);
  text-decoration: none;
  transition: color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.nav-link:hover {
  color: var(--text);
  background: var(--surface-2);
}

.nav-link.is-active {
  color: var(--text);
}

.nav-link.is-active::after {
  content: '';
  position: absolute;
  left: var(--space-3);
  right: var(--space-3);
  bottom: 2px;
  height: 2px;
  border-radius: var(--radius-pill);
  background: var(--primary);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-shrink: 0;
}

.header-email {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-sm);
  color: var(--text-muted);
}

/* 三个基础按钮：文字按钮 / 幽灵按钮 / 实心按钮，全站复用 */
.text-button {
  min-height: 36px;
  padding: 0 var(--space-2);
  border: none;
  background: none;
  border-radius: var(--radius-sm);
  font-family: inherit;
  font-size: var(--text-sm);
  color: var(--text-muted);
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.text-button:hover {
  color: var(--danger);
  background: var(--danger-soft);
}

.ghost-button,
.solid-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  padding: 0 var(--space-4);
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
  font-weight: 500;
  text-decoration: none;
  transition: background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.ghost-button {
  border: 1px solid var(--border);
  color: var(--text-secondary);
  background: var(--surface-1);
}

.ghost-button:hover {
  border-color: var(--border-strong);
  color: var(--text);
}

.solid-button {
  border: 1px solid transparent;
  background: var(--primary);
  color: var(--text-on-primary);
}

.solid-button:hover {
  background: var(--primary-hover);
  color: var(--text-on-primary);
}

@media (max-width: 640px) {
  .header-inner {
    padding: 0 var(--space-4);
    gap: var(--space-3);
  }

  .header-email {
    display: none;
  }

  .nav-link {
    padding: 0 var(--space-2);
    font-size: var(--text-sm);
  }

  .ghost-button {
    display: none;
  }
}
</style>