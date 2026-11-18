<template>
  <AuthShell
    :heading="isKeyMode ? '看看这台电脑，今天用了多久' : '欢迎回来'"
    :description="isKeyMode
      ? '输入对方分享的秘钥，即可查看这台设备的实时使用情况，无需注册。'
      : '登录后可管理自己的秘钥、分享链接与账号安全。'"
    :points="asidePoints"
    :title="isKeyMode ? '秘钥访问' : '账号登录'"
    :subtitle="isKeyMode ? '秘钥由数据所有者在「个人中心」生成' : '使用注册邮箱登录'"
  >
    <!-- ========== 秘钥模式 ========== -->
    <el-form
      v-if="isKeyMode"
      ref="keyFormRef"
      :model="keyForm"
      :rules="keyRules"
      label-position="top"
      size="large"
      @submit.prevent="handleVerifyKey"
    >
      <el-form-item label="访问秘钥" prop="key">
        <el-input
          ref="keyInputRef"
          v-model="keyForm.key"
          placeholder="粘贴完整秘钥"
          clearable
          autocomplete="off"
          @keyup.enter="handleVerifyKey"
        >
          <template #prefix>
            <el-icon><Key /></el-icon>
          </template>
        </el-input>
      </el-form-item>

      <el-button
        class="submit-button"
        type="primary"
        size="large"
        :loading="keyLoading"
        @click="handleVerifyKey"
      >
        {{ keyLoading ? '验证中…' : '查看数据' }}
      </el-button>
    </el-form>

    <!-- ========== 账号模式 ========== -->
    <el-form
      v-else
      ref="accountFormRef"
      :model="accountForm"
      :rules="accountRules"
      label-position="top"
      size="large"
      @submit.prevent="handleLogin"
    >
      <el-form-item label="邮箱" prop="email">
        <el-input
          v-model="accountForm.email"
          placeholder="you@example.com"
          clearable
          autocomplete="username"
        >
          <template #prefix>
            <el-icon><Message /></el-icon>
          </template>
        </el-input>
      </el-form-item>

      <el-form-item label="密码" prop="password">
        <el-input
          v-model="accountForm.password"
          type="password"
          placeholder="请输入登录密码"
          show-password
          clearable
          autocomplete="current-password"
          @keyup.enter="handleLogin"
        >
          <template #prefix>
            <el-icon><Lock /></el-icon>
          </template>
        </el-input>
      </el-form-item>

      <el-button
        class="submit-button"
        type="primary"
        size="large"
        :loading="accountLoading"
        @click="handleLogin"
      >
        {{ accountLoading ? '登录中…' : '登录' }}
      </el-button>
    </el-form>

    <!-- 模式切换：两种入口互换，而不是让用户去猜哪个是"首页" -->
    <template #foot>
      <span class="foot-row">
        <template v-if="isKeyMode">
          已有账号？
          <button type="button" class="foot-link" @click="switchMode('account')">账号登录</button>
        </template>
        <template v-else>
          只想查看分享数据？
          <button type="button" class="foot-link" @click="switchMode('key')">用秘钥访问</button>
          <span class="foot-divider" aria-hidden="true">·</span>
          <router-link class="foot-link" to="/register">注册账号</router-link>
        </template>
      </span>
    </template>
  </AuthShell>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Key, Lock, Message } from '@element-plus/icons-vue'
import AuthShell from '@/components/AuthShell.vue'
import { verifyKeyApi } from '@/api'
import { useUserStore } from '@/stores/user'

// 一个组件承载"秘钥访问"与"账号登录"两种模式，
// 原来 Login.vue / AuthLogin.vue 里约 95% 重复的 Canvas 背景与表单代码就此合并
type Mode = 'key' | 'account'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const mode = ref<Mode>(route.meta.authMode === 'account' ? 'account' : 'key')
const isKeyMode = computed(() => mode.value === 'key')

const asidePoints = computed(() => {
  if (isKeyMode.value) {
    return [
      { title: '只读查看', desc: '秘钥只能看数据，不能修改任何内容' },
      { title: '实时同步', desc: '对方正在使用时会自动刷新，无需手动刷新页面' },
      { title: '随时作废', desc: '数据所有者可以随时重新生成或作废旧秘钥' }
    ]
  }
  return [
    { title: '数据归你所有', desc: '使用记录保存在你自己的服务器上，可一键注销并永久删除' },
    { title: '分享用秘钥', desc: '把只读秘钥发给朋友，对方无需注册即可查看' },
    { title: '格式透明', desc: '时长、应用排行、键鼠统计都以本机时区展示' }
  ]
})

// ---------- 秘钥模式 ----------
const keyFormRef = ref<FormInstance>()
const keyInputRef = ref()
const keyLoading = ref(false)
const keyForm = reactive({ key: '' })

const keyRules: FormRules = {
  key: [
    { required: true, message: '请输入秘钥', trigger: 'blur' },
    { min: 8, message: '秘钥长度不足，请检查是否复制完整', trigger: 'blur' }
  ]
}

const handleVerifyKey = async () => {
  if (!keyFormRef.value) return
  const valid = await keyFormRef.value.validate().catch(() => false)
  if (!valid) return

  keyLoading.value = true
  try {
    await verifyKeyApi(keyForm.key.trim())
    ElMessage.success('验证成功')
    router.push({ path: '/monitor', query: { key: keyForm.key.trim() } })
  } catch {
    // 错误提示已由请求拦截器统一弹出
  } finally {
    keyLoading.value = false
  }
}

// ---------- 账号模式 ----------
const accountFormRef = ref<FormInstance>()
const accountLoading = ref(false)
const accountForm = reactive({ email: '', password: '' })

const accountRules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码至少 8 位', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (!accountFormRef.value) return
  const valid = await accountFormRef.value.validate().catch(() => false)
  if (!valid) return

  accountLoading.value = true
  try {
    await userStore.login(accountForm.email, accountForm.password)
    ElMessage.success('登录成功')
    router.push('/monitor')
  } catch {
    // 错误提示已由请求拦截器统一弹出
  } finally {
    accountLoading.value = false
  }
}

// ---------- 模式切换 ----------
const switchMode = (next: Mode) => {
  mode.value = next
  // 同步路由，保证刷新/分享链接后落在同一个模式
  router.replace(next === 'account' ? '/auth/login' : '/login')
}

// URL 变化(直接访问 /login 或 /auth/login)时跟随切换模式
watch(() => route.meta.authMode, (authMode) => {
  mode.value = authMode === 'account' ? 'account' : 'key'
})

// 首屏焦点：秘钥模式聚焦秘钥输入框，账号模式聚焦邮箱
watch(mode, async () => {
  await nextTick()
  keyInputRef.value?.focus?.()
}, { immediate: true })
</script>

<style scoped>
.submit-button {
  width: 100%;
  margin-top: var(--space-2);
  font-weight: 600;
}

.foot-row {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
  justify-content: center;
}

.foot-link {
  border: none;
  background: none;
  padding: 0;
  font-family: inherit;
  font-size: var(--text-sm);
  color: var(--primary);
  cursor: pointer;
  text-decoration: none;
}

.foot-link:hover {
  color: var(--primary-hover);
  text-decoration: underline;
}

.foot-divider {
  color: var(--border-strong);
}

/* Element 表单标签：中文加粗一点，行距收紧 */
:deep(.el-form-item__label) {
  font-size: var(--text-sm);
  color: var(--text-secondary);
  font-weight: 500;
  padding-bottom: var(--space-2);
}

:deep(.el-form-item) {
  margin-bottom: var(--space-4);
}
</style>