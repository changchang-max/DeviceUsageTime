<template>
  <div class="profile-page">
    <AppHeader />
    
    <div class="profile-container">
      <el-card class="profile-card">
        <template #header>
          <h2>个人中心</h2>
        </template>
        
        <div class="profile-content">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="邮箱">
              {{ userStore.userInfo?.email }}
            </el-descriptions-item>
            <el-descriptions-item label="注册时间">
              {{ formatDateTime(userStore.userInfo?.createdAt || '') }}
            </el-descriptions-item>
            <el-descriptions-item label="最后登录">
              {{ formatDateTime(userStore.userInfo?.lastLoginAt || '') }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </el-card>
      
      <el-card class="secret-card">
        <template #header>
          <h2>秘钥管理</h2>
        </template>
        
        <div class="secret-content">
          <el-alert
            title="说明"
            type="info"
            :closable="false"
            style="margin-bottom: 20px"
          >
            秘钥用于分享您的设备使用数据给他人查看。请妥善保管,避免泄露给不信任的人。
          </el-alert>
          
          <div class="secret-display">
            <div class="secret-label">当前秘钥:</div>
            <div class="secret-value">
              <el-input
                :model-value="userStore.userInfo?.secretKey"
                readonly
                size="large"
              >
                <template #append>
                  <el-button @click="handleCopyKey">
                    <el-icon><DocumentCopy /></el-icon>
                    复制
                  </el-button>
                </template>
              </el-input>
            </div>
          </div>
          
          <div class="secret-actions">
            <el-button
              type="primary"
              @click="handleRegenerateKey"
            >
              重新生成秘钥
            </el-button>
            <el-button
              type="danger"
              @click="handleRevokeKey"
            >
              作废秘钥
            </el-button>
          </div>
          
          <el-divider />
          
          <div class="share-link-section">
            <div class="share-label">分享链接:</div>
            <div class="share-value">
              <el-input
                :model-value="shareLink"
                readonly
                size="large"
              >
                <template #append>
                  <el-button @click="handleCopyLink">
                    <el-icon><DocumentCopy /></el-icon>
                    复制链接
                  </el-button>
                </template>
              </el-input>
            </div>
            <div class="share-tip">
              将此链接分享给他人,他们无需注册即可查看您的设备使用数据
            </div>
          </div>
        </div>
      </el-card>

      <el-card class="account-card">
        <template #header>
          <h2>账号安全</h2>
        </template>

        <div class="account-content">
          <div class="account-tip">
            管理登录密码与账号。注销账号将<em>永久删除账号及全部关联数据,无法恢复</em>。
          </div>
          <div class="account-actions">
            <el-button
              type="primary"
              @click="handleOpenChangePassword"
            >
              修改密码
            </el-button>
            <el-button
              type="danger"
              plain
              @click="handleDeregister"
            >
              注销用户
            </el-button>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 修改密码弹窗 -->
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
        <el-form-item label="原密码" prop="old_password">
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
            placeholder="至少8位,包含字母和数字"
            show-password
            autocomplete="new-password"
            @keyup.enter="handleSubmitChangePassword"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="changePasswordVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="changePasswordLoading"
          @click="handleSubmitChangePassword"
        >
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 注销用户:第一步 输入登录密码 -->
    <el-dialog
      v-model="deregisterPasswordVisible"
      title="注销用户"
      width="420px"
      :close-on-click-modal="false"
    >
      <div class="deregister-tip">
        注销前需验证身份,请输入您的登录密码。
      </div>
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
        <el-button
          type="danger"
          @click="handleNextDeregister"
        >
          下一步
        </el-button>
      </template>
    </el-dialog>

    <!-- 注销用户:第二步 不可恢复确认 -->
    <el-dialog
      v-model="deregisterConfirmVisible"
      title="确认注销"
      width="420px"
      :close-on-click-modal="false"
    >
      <div class="deregister-warning">
        <p>
          注销后,当前账号及其全部数据将被<b>永久删除</b>,<b>账户不可恢复</b>。
          确定要继续注销吗?
        </p>
      </div>
      <template #footer>
        <el-button @click="handleCancelDeregisterConfirm">再想想</el-button>
        <el-button
          type="danger"
          :loading="deregisterLoading"
          @click="handleConfirmDeregister"
        >
          确认注销
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  changePasswordApi,
  deregisterApi,
  regenerateKeyApi,
  revokeKeyApi
} from '@/api'
import { formatDateTime } from '@/utils/format'
import AppHeader from '@/components/AppHeader.vue'
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules
} from 'element-plus'
import { DocumentCopy } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

const shareLink = computed(() => {
  if (!userStore.userInfo?.secretKey) return ''
  const origin = window.location.origin
  // 秘钥可能含特殊字符,必须URL编码后再拼入分享链接
  return `${origin}/monitor?key=${encodeURIComponent(userStore.userInfo.secretKey)}`
})

const handleCopyKey = async () => {
  if (!userStore.userInfo?.secretKey) return
  
  try {
    await navigator.clipboard.writeText(userStore.userInfo.secretKey)
    ElMessage.success('秘钥已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败,请手动复制')
  }
}

const handleCopyLink = async () => {
  try {
    await navigator.clipboard.writeText(shareLink.value)
    ElMessage.success('分享链接已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败,请手动复制')
  }
}

const handleRegenerateKey = async () => {
  try {
    await ElMessageBox.confirm(
      '重新生成秘钥后,旧秘钥将立即失效,已分享的链接将无法使用。确定要继续吗?',
      '确认操作',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    const res = await regenerateKeyApi()
    userStore.updateSecretKey(res.data.secretKey)
    ElMessage.success('秘钥已重新生成')
  } catch {
    // 用户取消弹窗或请求失败(错误提示已由请求拦截器统一弹出)
  }
}

const handleRevokeKey = async () => {
  try {
    await ElMessageBox.confirm(
      '作废秘钥后,已分享的链接将无法使用,且不会生成新秘钥。确定要继续吗?',
      '确认操作',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await revokeKeyApi()
    userStore.updateSecretKey('')
    ElMessage.success('秘钥已作废')
  } catch {
    // 用户取消弹窗或请求失败(错误提示已由请求拦截器统一弹出)
  }
}

// ===== 账号安全:修改密码 =====
const changePasswordVisible = ref(false)
const changePasswordLoading = ref(false)
const changePasswordFormRef = ref<FormInstance>()
const changePasswordForm = reactive({
  old_password: '',
  new_password: ''
})

// 新密码校验:至少8位且同时包含字母和数字(与后端校验规则保持一致)
const validateNewPassword = (rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value) {
    callback(new Error('请输入新密码'))
  } else if (value.length < 8) {
    callback(new Error('密码至少8位'))
  } else if (!/[a-zA-Z]/.test(value) || !/[0-9]/.test(value)) {
    callback(new Error('密码必须包含字母和数字'))
  } else if (value === changePasswordForm.old_password) {
    callback(new Error('新密码不能与原密码相同'))
  } else {
    callback()
  }
}

const changePasswordRules: FormRules = {
  old_password: [
    { required: true, message: '请输入原密码', trigger: 'blur' }
  ],
  new_password: [
    { required: true, validator: validateNewPassword, trigger: 'blur' }
  ]
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

// 修改密码成功后后端已使当前Token立即失效,清空本地会话并跳回登录页重新登录
const handleSubmitChangePassword = async () => {
  if (!changePasswordFormRef.value) return
  
  await changePasswordFormRef.value.validate(async (valid) => {
    if (!valid) return
    
    changePasswordLoading.value = true
    try {
      await changePasswordApi({
        old_password: changePasswordForm.old_password,
        new_password: changePasswordForm.new_password
      })
      ElMessage.success('密码修改成功,请重新登录')
      changePasswordVisible.value = false
      clearSessionAndGoLogin()
    } catch {
      // 原密码错误等提示已由请求拦截器统一弹出,弹窗保持打开以便重试
    } finally {
      changePasswordLoading.value = false
    }
  })
}

// ===== 账号安全:注销用户 =====
const deregisterPasswordVisible = ref(false)
const deregisterConfirmVisible = ref(false)
const deregisterPassword = ref('')
const deregisterLoading = ref(false)

const handleDeregister = () => {
  deregisterPassword.value = ''
  deregisterPasswordVisible.value = true
}

// 第一步:输入密码通过后,弹出第二步"账户不可恢复"确认框
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

// 第二步:用户确认"不可恢复"后真正执行注销
const handleConfirmDeregister = async () => {
  deregisterLoading.value = true
  try {
    await deregisterApi({
      user_password: deregisterPassword.value
    })
    ElMessage.success('账号已注销')
    deregisterConfirmVisible.value = false
    deregisterPassword.value = ''
    clearSessionAndGoLogin()
  } catch {
    // 密码错误等提示已由请求拦截器统一弹出;回到第一步让用户重新输入密码
    deregisterConfirmVisible.value = false
    deregisterPasswordVisible.value = true
  } finally {
    deregisterLoading.value = false
  }
}

// 清空本地会话状态并跳转登录页(注销/改密成功后Token已失效)
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
.profile-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.profile-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.profile-card,
.secret-card,
.account-card {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.profile-card h2,
.secret-card h2,
.account-card h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.profile-content {
  padding: 20px 0;
}

.secret-content {
  padding: 20px 0;
}

.secret-display {
  margin-bottom: 20px;
}

.secret-label,
.share-label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
}

.secret-value,
.share-value {
  margin-bottom: 12px;
}

.secret-actions {
  display: flex;
  gap: 12px;
}

.share-link-section {
  margin-top: 20px;
}

.share-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

.account-content {
  padding: 0;
}

.account-tip {
  font-size: 14px;
  color: #606266;
  margin-bottom: 16px;
  line-height: 1.6;
}

.account-tip em {
  color: #f56c6c;
  font-style: normal;
}

.account-actions {
  display: flex;
  gap: 12px;
}

.deregister-tip {
  font-size: 14px;
  color: #606266;
  margin-bottom: 16px;
}

.deregister-warning p {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: #606266;
}

.deregister-warning b {
  color: #f56c6c;
}
</style>
