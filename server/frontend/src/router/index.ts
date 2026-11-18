import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

// 路由 meta 约定：
// - requiresAuth: true   需要登录 Token
// - authMode: 'key' | 'account'  认证页默认模式(AuthView 复用同一组件)
// - title: 浏览器标题后缀
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/monitor'
  },
  {
    path: '/login',
    name: 'KeyLogin',
    component: () => import('@/views/AuthView.vue'),
    meta: { requiresAuth: false, authMode: 'key', title: '秘钥访问' }
  },
  {
    path: '/auth/login',
    name: 'AuthLogin',
    component: () => import('@/views/AuthView.vue'),
    meta: { requiresAuth: false, authMode: 'account', title: '账号登录' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { requiresAuth: false, title: '注册' }
  },
  {
    path: '/monitor/:userId?',
    name: 'Monitor',
    component: () => import('@/views/Monitor.vue'),
    meta: { requiresAuth: false, title: '实时监控' } // 可以通过秘钥访问
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/Profile.vue'),
    meta: { requiresAuth: true, title: '个人中心' }
  },
  {
    path: '/view',
    name: 'View',
    redirect: '/login'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  // 路由切换回到顶部，避免长列表页跳转后停在中间
  scrollBehavior: () => ({ top: 0 })
})

const BASE_TITLE = '设备使用时间'

// 路由守卫：需要登录但无 Token 时跳登录页；同时维护 document.title
router.beforeEach((to) => {
  const token = localStorage.getItem('token')

  if (to.meta.requiresAuth && !token) {
    return { path: '/login', replace: true }
  }

  const pageTitle = to.meta.title as string | undefined
  document.title = pageTitle ? `${pageTitle} · ${BASE_TITLE}` : BASE_TITLE
  return true
})

export default router