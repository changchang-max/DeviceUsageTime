<template>
  <AuthShell
    heading="创建账号，开始记录"
    description="注册后你的设备使用数据会自动同步到这台服务器，随时回看每一天。"
    :points="points"
    title="注册账号"
    subtitle="注册成功后会获得一个专属只读秘钥"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-position="top"
      size="large"
      @submit.prevent="handleRegister"
    >
      <el-form-item label="邮箱" prop="email">
        <el-input
          v-model="formData.email"
          placeholder="you@example.com"
          clearable
          autocomplete="email"
        />
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

      <el-form-item label="密码" prop="password">
        <el-input
          v-model="formData.password"
          type="password"
          placeholder="至少 8 位，包含字母和数字"
          show-password
          clearable
          autocomplete="new-password"
        />
      </el-form-item>

      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input
          v-model="formData.confirmPassword"
          type="password"
          placeholder="再次输入密码"
          show-password
          clearable
          autocomplete="new-password"
          @keyup.enter="handleRegister"
        />
      </el-form-item>

      <el-button
        class="submit-button"
        type="primary"
        size="large"
        :loading="loading"
        @click="handleRegister"
      >
        {{ loading ? '注册中…' : '创建账号' }}
      </el-button>
    </el-form>

    <template #foot>
      已有账号？<router-link class="foot-link" to="/auth/login">直接登录</router-link>
      <span class="foot-divider" aria-hidden="true">·</span>
      <router-link class="foot-link" to="/login">用秘钥访问</router-link>
    </template>
  </AuthShell>
</template>

<script setup lang="ts">
import { computed, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import AuthShell from '@/components/AuthShell.vue'
import { sendCodeApi } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const points = [
  { title: '自动同步', desc: '客户端每分钟上报一次，网页实时可见' },
  { title: '历史可回看', desc: '按日期查看每一天的时长、排行与键鼠统计' },
  { title: '隐私可控', desc: '只分享只读秘钥，随时可一键作废' }
]

const formRef = ref<FormInstance>()
const loading = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
let timer: number | null = null

const formData = reactive({
  email: '',
  code: '',
  password: '',
  confirmPassword: ''
})

const codeButtonText = computed(() => {
  if (countdown.value > 0) return `${countdown.value}s 后重发`
  return sendingCode.value ? '发送中…' : '发送验证码'
})

const validatePassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value) {
    callback(new Error('请输入密码'))
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
    callback(new Error('请再次输入密码'))
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

const startCountdown = () => {
  countdown.value = 60
  timer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0 && timer) {
      window.clearInterval(timer)
      timer = null
    }
  }, 1000)
}

const handleSendCode = async () => {
  if (!formData.email) {
    ElMessage.warning('请先填写邮箱')
    return
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) {
    ElMessage.warning('请输入正确的邮箱格式')
    return
  }

  sendingCode.value = true
  try {
    await sendCodeApi(formData.email)
    ElMessage.success('验证码已发送，请查收邮件')
    startCountdown()
  } catch {
    // 错误提示已由请求拦截器统一弹出
  } finally {
    sendingCode.value = false
  }
}

const handleRegister = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.register(formData.email, formData.password, formData.code)
    ElMessage.success('注册成功，请登录')
    router.push('/auth/login')
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