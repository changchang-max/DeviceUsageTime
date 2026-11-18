<template>
  <AppShell>
    <section class="page-head">
      <h1 class="page-title">个人中心</h1>
      <p class="page-desc">管理你的账号信息、只读秘钥与分享链接。</p>
    </section>

    <!-- 账号信息 -->
    <section class="panel">
      <header class="panel-head">
        <h2 class="panel-title">账号信息</h2>
      </header>

      <dl class="info-grid">
        <div class="info-row">
          <dt>邮箱</dt>
          <dd>{{ userStore.userInfo?.email || '—' }}</dd>
        </div>
        <div class="info-row">
          <dt>注册时间</dt>
          <dd>{{ formatDateTime(userStore.userInfo?.createdAt || '') || '—' }}</dd>
        </div>
        <div class="info-row">
          <dt>最后登录</dt>
          <dd>{{ formatDateTime(userStore.userInfo?.lastLoginAt || '') || '—' }}</dd>
        </div>
      </dl>
    </section>

    <!-- 秘钥 -->
    <section class="panel">
      <header class="panel-head">
        <h2 class="panel-title">只读秘钥</h2>
        <p class="panel-desc">
          秘钥用于把数据分享给他人查看。对方无需注册，只能查看，不能修改任何内容。
        </p>
      </header>

      <div v-if="userStore.userInfo?.secretKey" class="field-block">
        <label class="field-label" for="secret-key">当前秘钥</label>
        <div class="field-row">
          <input id="secret-key" class="readonly-input tabular" :value="userStore.userInfo.secretKey" readonly>
          <button type="button" class="solid-button" @click="handleCopyKey">复制秘钥</button>
        </div>
      </div>

      <div v-else class="field-block">
        <p class="empty-note">秘钥已作废，需要重新生成后才能分享。</p>
      </div>

      <div class="field-block">
        <label class="field-label" for="share-link">分享链接</label>
        <div class="field-row">
          <input id="share-link" class="readonly-input" :value="shareLink" readonly>
          <button type="button" class="ghost-button" :disabled="!shareLink" @click="handleCopyLink">
            复制链接
          </button>
        </div>
        <p class="field-hint">拿到链接的人可以直接查看你的实时与历史数据。</p>
      </div>

      <div class="button-row">
        <button type="button" class="ghost-button" @click="handleRegenerateKey">重新生成秘钥</button>
        <button type="button" class="danger-button" @click="handleRevokeKey">作废秘钥</button>
      </div>
    </section>

    <!-- 账号安全 -->
    <section class="panel">
      <header class="panel-head">
        <h2 class="panel-title">账号安全</h2>
      </header>

      <p class="panel-desc">
        注销账号会<b class="danger-text">永久删除账号及全部使用数据，无法恢复</b>。
      </p>

      <div class="button-row">
        <button type="button" class="ghost-button" @click="handleOpenChangePassword">修改密码</button>
        <button type="button" class="danger-button" @click="handleDeregister">注销账号</button>
      </div>
    </section>

    <!-- 修改密码 -->
    <el-dialog
      v-model="changePasswordVisible"
      title="修改密码"
      width="420px"
      :close-on-click-modal="false"
      @closed="resetChangePasswordForm"
    >
      <el-form
        ref="changePasswordFormRef"
        :model="changePasswordForm"
        :rules="changePasswordRules"
        label-position="top"
      >
        <el-form-item label="当前密码" prop="old_password">
          <el-input
            v-model="changePasswordForm.old_password"
            type="password"
            placeholder="请输入当前密码"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-form-item label="新密码" prop="new_password">
          <el-input
            v-model="changePasswordForm.new_password"
            type="password"
            placeholder="至少 8 位，包含字母和数字"
            show-password
            autocomplete="new-password"
            @keyup.enter="handleSubmitChangePassword"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="changePasswordVisible = false">取消</el-button>
        <el-button type="primary" :loading="changePasswordLoading" @click="handleSubmitChangePassword">
          确认修改
        </el-button>
      </template>
    </el-dialog>

    <!-- 注销第一步：验证密码 -->
    <el-dialog
      v-model="deregisterPasswordVisible"
      title="注销账号"
      width="420px"
      :close-on-click-modal="false"
    >
      <p class="dialog-tip">注销前需验证身份，请输入当前登录密码。</p>
      <el-form @submit.prevent>
        <el-form-item label="登录密码">
          <el-input
            v-model="deregisterPassword"
            type="password"
            placeholder="请输入登录密码"
            show-password
            autocomplete="current-password"
            @keyup.enter="handleNextDeregister"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleCancelDeregisterPassword">取消</el-button>
        <el-button type="danger" @click="handleNextDeregister">下一步</el-button>
      </template>
    </el-dialog>

    <!-- 注销第二步：不可恢复确认 -->
    <el-dialog
      v-model="deregisterConfirmVisible"
      title="确认注销"
      width="420px"
      :close-on-click-modal="false"
    >
      <p class="dialog-tip">
        注销后，当前账号及其全部数据将被<b class="danger-text">永久删除</b>，且<b class="danger-text">无法恢复</b>。
        确定要继续吗？
      </p>
      <template #footer>
        <el-button @click="handleCancelDeregisterConfirm">再想想</el-button>
        <el-button type="danger" :loading="deregisterLoading" @click="handleConfirmDeregister">
          确认注销
        </el-button>
      </template>
    </el-dialog>
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import { useUserStore } from '@/stores/user'
import { changePasswordApi, deregisterApi, regenerateKeyApi, revokeKeyApi } from '@/api'
import { formatDateTime } from '@/utils/format'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const shareLink = computed(() => {
  if (!userStore.userInfo?.secretKey) return ''
  // 秘钥可能含特殊字符，必须 URL 编码后再拼入分享链接
  return `${window.location.origin}/monitor?key=${encodeURIComponent(userStore.userInfo.secretKey)}`
})

const handleCopyKey = async () => {
  if (!userStore.userInfo?.secretKey) return
  try {
    await navigator.clipboard.writeText(userStore.userInfo.secretKey)
    ElMessage.success('秘钥已复制')
  } catch {
    ElMessage.error('浏览器未授权剪贴板，请手动选择复制')
  }
}

const handleCopyLink = async () => {
  try {
    await navigator.clipboard.writeText(shareLink.value)
    ElMessage.success('分享链接已复制')
  } catch {
    ElMessage.error('浏览器未授权剪贴板，请手动选择复制')
  }
}

const handleRegenerateKey = async () => {
  try {
    await ElMessageBox.confirm(
      '重新生成后旧秘钥立即失效，已分享的链接将无法打开。确定继续吗？',
      '重新生成秘钥',
      { confirmButtonText: '重新生成', cancelButtonText: '取消', type: 'warning' }
    )
    const res = await regenerateKeyApi()
    userStore.updateSecretKey(res.data.secretKey)
    ElMessage.success('秘钥已重新生成')
  } catch {
    // 用户取消或请求失败(错误提示已由拦截器弹出)
  }
}

const handleRevokeKey = async () => {
  try {
    await ElMessageBox.confirm(
      '作废后已分享的链接将无法使用，且不会自动生成新秘钥。确定继续吗？',
      '作废秘钥',
      { confirmButtonText: '作废', cancelButtonText: '取消', type: 'warning' }
    )
    await revokeKeyApi()
    userStore.updateSecretKey('')
    ElMessage.success('秘钥已作废')
  } catch {
    // 用户取消或请求失败
  }
}

// ===== 修改密码 =====
const changePasswordVisible = ref(false)
const changePasswordLoading = ref(false)
const changePasswordFormRef = ref<FormInstance>()
const changePasswordForm = reactive({ old_password: '', new_password: '' })

const validateNewPassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value) {
    callback(new Error('请输入新密码'))
  } else if (value.length < 8) {
    callback(new Error('密码至少 8 位'))
  } else if (!/[a-zA-Z]/.test(value) || !/[0-9]/.test(value)) {
    callback(new Error('密码必须包含字母和数字'))
  } else if (value === changePasswordForm.old_password) {
    callback(new Error('新密码不能与当前密码相同'))
  } else {
    callback()
  }
}

const changePasswordRules: FormRules = {
  old_password: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  new_password: [{ required: true, validator: validateNewPassword, trigger: 'blur' }]
}

const handleOpenChangePassword = () => {
  resetChangePasswordForm()
  changePasswordVisible.value = true
}

const resetChangePasswordForm = () => {
  changePasswordForm.old_password = ''
  changePasswordForm.new_password = ''
  changePasswordFormRef.value?.clearValidate()
}

const handleSubmitChangePassword = async () => {
  if (!changePasswordFormRef.value) return
  const valid = await changePasswordFormRef.value.validate().catch(() => false)
  if (!valid) return

  changePasswordLoading.value = true
  try {
    await changePasswordApi({
      old_password: changePasswordForm.old_password,
      new_password: changePasswordForm.new_password
    })
    ElMessage.success('密码已修改，请重新登录')
    changePasswordVisible.value = false
    clearSessionAndGoLogin()
  } catch {
    // 弹窗保持打开以便重试
  } finally {
    changePasswordLoading.value = false
  }
}

// ===== 注销账号 =====
const deregisterPasswordVisible = ref(false)
const deregisterConfirmVisible = ref(false)
const deregisterPassword = ref('')
const deregisterLoading = ref(false)

const handleDeregister = () => {
  deregisterPassword.value = ''
  deregisterPasswordVisible.value = true
}

const handleNextDeregister = () => {
  if (!deregisterPassword.value) {
    ElMessage.warning('请输入登录密码')
    return
  }
  deregisterPasswordVisible.value = false
  deregisterConfirmVisible.value = true
}

const handleCancelDeregisterPassword = () => {
  deregisterPassword.value = ''
  deregisterPasswordVisible.value = false
}

const handleCancelDeregisterConfirm = () => {
  deregisterPassword.value = ''
  deregisterConfirmVisible.value = false
}

const handleConfirmDeregister = async () => {
  deregisterLoading.value = true
  try {
    await deregisterApi({ user_password: deregisterPassword.value })
    ElMessage.success('账号已注销')
    deregisterConfirmVisible.value = false
    deregisterPassword.value = ''
    clearSessionAndGoLogin()
  } catch {
    // 密码错误等情况回到第一步重新输入
    deregisterConfirmVisible.value = false
    deregisterPasswordVisible.value = true
  } finally {
    deregisterLoading.value = false
  }
}

// 注销/改密成功后 Token 已失效，清空本地会话并回到登录页
const clearSessionAndGoLogin = () => {
  userStore.token = ''
  userStore.userInfo = null
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  router.replace('/login')
}

onMounted(async () => {
  if (!userStore.userInfo) {
    await userStore.fetchUserInfo()
  }
})
</script>

<style scoped>
.page-head {
  padding-top: var(--space-2);
}

.page-title {
  font-size: var(--text-xl);
  margin-bottom: var(--space-2);
}

.page-desc {
  font-size: var(--text-sm);
  color: var(--text-muted);
}

.panel {
  background: var(--surface-1);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.panel-head {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.panel-title {
  font-size: var(--text-md);
}

.panel-desc {
  font-size: var(--text-sm);
  color: var(--text-muted);
  line-height: var(--leading-normal);
}

/* 定义列表：信息只读，用 dt/dd 语义 */
.info-grid {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.info-row {
  display: flex;
  align-items: baseline;
  gap: var(--space-4);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--border);
}

.info-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.info-row dt {
  width: 88px;
  flex-shrink: 0;
  font-size: var(--text-sm);
  color: var(--text-muted);
}

.info-row dd {
  margin: 0;
  font-size: var(--text-base);
  color: var(--text);
  word-break: break-all;
}

.field-block {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.field-label {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--text-secondary);
}

.field-row {
  display: flex;
  gap: var(--space-2);
  align-items: stretch;
  flex-wrap: wrap;
}

/* 只读值用 input 而不是 div：可选中、可键盘聚焦、移动端可长按复制 */
.readonly-input {
  flex: 1;
  min-width: 220px;
  height: 40px;
  padding: 0 var(--space-3);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-family: var(--font-sans);
  font-size: var(--text-sm);
}

.readonly-input:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 1px;
}

.field-hint {
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.empty-note {
  font-size: var(--text-sm);
  color: var(--text-muted);
}

.button-row {
  display: flex;
  gap: var(--space-3);
  flex-wrap: wrap;
}

/* 三个按钮基类：保证 min-height 44px 的触控目标 */
.solid-button,
.ghost-button,
.danger-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  padding: 0 var(--space-4);
  border-radius: var(--radius-sm);
  font-family: inherit;
  font-size: var(--text-sm);
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.solid-button {
  border: 1px solid transparent;
  background: var(--primary);
  color: var(--text-on-primary);
}

.solid-button:hover {
  background: var(--primary-hover);
}

.ghost-button {
  border: 1px solid var(--border);
  background: var(--surface-1);
  color: var(--text-secondary);
}

.ghost-button:hover {
  border-color: var(--border-strong);
  color: var(--text);
}

.danger-button {
  border: 1px solid var(--border);
  background: var(--surface-1);
  color: var(--danger);
}

.danger-button:hover {
  border-color: var(--danger);
  background: var(--danger-soft);
}

.ghost-button:disabled {
  border-color: var(--border);
  background: var(--bg-subtle);
  color: var(--text-muted);
  cursor: not-allowed;
}

.danger-text {
  color: var(--danger);
  font-weight: 600;
}

.dialog-tip {
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  color: var(--text-secondary);
}

@media (max-width: 640px) {
  .panel {
    padding: var(--space-4);
  }

  .info-row {
    flex-direction: column;
    gap: var(--space-1);
  }

  .button-row {
    flex-direction: column;
  }

  .button-row > button {
    width: 100%;
  }
}
</style>