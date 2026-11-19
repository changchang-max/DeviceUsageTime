<template>
  <AuthShell
    heading="找回账号，重新开始记录"
    description="输入注册邮箱获取验证码，验证后即可设置新密码。重置成功后该账号所有已登录设备都会立即失效。"
    :points="points"
    title="重置密码"
    subtitle="验证码 3 分钟内有效，仅可使用一次"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-position="top"
      size="large"
      @submit.prevent="handleReset"
    >
      <el-form-item label="邮箱" prop="email">
        <el-input
          v-model="formData.email"
          placeholder="you@example.com"
          clearable
          autocomplete="email"
        >
          <template #prefix>
            <el-icon><Message /></el-icon>
          </template>
        </el-input>
      </el-form-item>

      <el-form-item label="验证码" prop="code">
        <div class="code-row">
          <el-input
            v-model="formData.code"
            placeholder="6 位验证码"
            maxlength="6"
            clearable
            autocomplete="one-time-code"
          />
          <button
            type="button"
            class="code-button"
            :disabled="countdown > 0 || sendingCode"
            @click="handleSendCode"
          >
            {{ codeButtonText }}
          </button>
        </div>
      </el-form-item>

      <el-form-item label="新密码" prop="password">
        <el-input
          v-model="formData.password"
          type="password"
          placeholder="至少 8 位，包含字母和数字"
          show-password
          clearable
          autocomplete="new-password"
        >
          <template #prefix>
            <el-icon><Lock /></el-icon>
          </template>
        </el-input>
      </el-form-item>

      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input
          v-model="formData.confirmPassword"
          type="password"
          placeholder="再次输入新密码"
          show-password
          clearable
          autocomplete="new-password"
          @keyup.enter="handleReset"
        />
      </el-form-item>

      <el-button
        class="submit-button"
        type="primary"
        size="large"
        :loading="loading"
        @click="handleReset"
      >
        {{ loading ? '提交中…' : '确认修改密码' }}
      </el-button>
    </el-form>

    <template #foot>
      想起来了？<router-link class="foot-link" to="/auth/login">返回登录</router-link>
      <span class="foot-divider" aria-hidden="true">·</span>
      <router-link class="foot-link" to="/register">注册账号</router-link>
    </template>
  </AuthShell>
</template>

<script setup lang="ts">
import { computed, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, Message } from '@element-plus/icons-vue'
import AuthShell from '@/components/AuthShell.vue'
import { resetPasswordApi, sendResetCodeApi } from '@/api'

const route = useRoute()
const router = useRouter()

const points = [
  { title: '验证码 3 分钟有效', desc: '验证码仅限当前邮箱使用一次，过期或使用后立即作废' },
  { title: '凭证全部失效', desc: '重置成功后，该账号在网页端与客户端的所有登录状态都会立即失效' },
  { title: '新密码即刻生效', desc: '使用新密码重新登录即可继续查看与上传使用数据' }
]

const formRef = ref<FormInstance>()
const loading = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
let timer: number | null = null

// 从登录页跳转过来时自动带入邮箱，用户少填一次
const initialEmail = typeof route.query.email === 'string' ? route.query.email : ''

const formData = reactive({
  email: initialEmail,
  code: '',
  password: '',
  confirmPassword: ''
})

const codeButtonText = computed(() => {
  if (countdown.value > 0) return `${countdown.value}s 后重发`
  return sendingCode.value ? '发送中…' : '获取验证码'
})

const validatePassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value) {
    callback(new Error('请输入新密码'))
  } else if (value.length < 8) {
    callback(new Error('密码至少 8 位'))
  } else if (!/[a-zA-Z]/.test(value) || !/[0-9]/.test(value)) {
    callback(new Error('密码必须包含字母和数字'))
  } else {
    callback()
  }
}

const validateConfirmPassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value) {
    callback(new Error('请再次输入新密码'))
  } else if (value !== formData.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码为 6 位数字', trigger: 'blur' }
  ],
  password: [{ required: true, validator: validatePassword, trigger: 'blur' }],
  confirmPassword: [{ required: true, validator: validateConfirmPassword, trigger: 'blur' }]
}

/**
 * 开始验证码倒计时
 * @param seconds 倒计时秒数(后端返回的验证码有效期)
 */
const startCountdown = (seconds: number) => {
  if (timer) {
    window.clearInterval(timer)
    timer = null
  }
  countdown.value = Math.max(1, Math.floor(seconds))
  timer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0 && timer) {
      window.clearInterval(timer)
      timer = null
    }
  }, 1000)
}

const handleSendCode = async () => {
  const email = formData.email.trim()
  if (!email) {
    ElMessage.warning('请先填写邮箱')
    return
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    ElMessage.warning('请输入正确的邮箱格式')
    return
  }

  sendingCode.value = true
  try {
    const res = await sendResetCodeApi(email)
    ElMessage.success(res.message || '验证码已发送，3 分钟内有效')
    // 后端返回的 expires_in 为 3 分钟；倒计时按有效期展示，便于用户判断何时需要重新获取
    startCountdown(res.data?.expires_in || 180)
  } catch {
    // 错误提示已由请求拦截器统一弹出
  } finally {
    sendingCode.value = false
  }
}

const handleReset = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await resetPasswordApi({
      user_email: formData.email.trim(),
      code: formData.code.trim(),
      new_password: formData.password
    })
    // 密码已变更，旧 Token 在后端已全部吊销，本地也一并清理，避免带着失效凭证跳转
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    ElMessage.success('密码已重置，请使用新密码登录')
    router.replace({ path: '/auth/login', query: { email: formData.email.trim() } })
  } catch {
    // 错误提示已由请求拦截器统一弹出
  } finally {
    loading.value = false
  }
}

onUnmounted(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})
</script>

<style scoped>
.code-row {
  display: flex;
  gap: var(--space-2);
  width: 100%;
}

.code-row :deep(.el-input) {
  flex: 1;
}

/* 验证码按钮：与输入框同高同圆角，禁用时不用 opacity 压暗 */
.code-button {
  flex-shrink: 0;
  min-width: 108px;
  min-height: var(--el-component-size-large);
  padding: 0 var(--space-3);
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--surface-1);
  color: var(--text-secondary);
  font-family: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
  transition: background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out);
}

.code-button:hover:not(:disabled) {
  border-color: var(--primary);
  color: var(--primary);
}

.code-button:disabled {
  background: var(--bg-subtle);
  border-color: var(--border);
  color: var(--text-muted);
  cursor: not-allowed;
}

.submit-button {
  width: 100%;
  margin-top: var(--space-2);
  font-weight: 600;
}

.foot-link {
  color: var(--primary);
  text-decoration: none;
}

.foot-link:hover {
  text-decoration: underline;
}

.foot-divider {
  color: var(--border-strong);
  margin: 0 var(--space-1);
}

:deep(.el-form-item__label) {
  font-size: var(--text-sm);
  color: var(--text-secondary);
  font-weight: 500;
  padding-bottom: var(--space-2);
}

:deep(.el-form-item) {
  margin-bottom: var(--space-4);
}

@media (max-width: 400px) {
  .code-row {
    flex-direction: column;
  }

  .code-button {
    width: 100%;
  }
}
</style>