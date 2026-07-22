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
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { regenerateKeyApi, revokeKeyApi } from '@/api'
import { formatDateTime } from '@/utils/format'
import AppHeader from '@/components/AppHeader.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DocumentCopy } from '@element-plus/icons-vue'

const userStore = useUserStore()

const shareLink = computed(() => {
  if (!userStore.userInfo?.secretKey) return ''
  const origin = window.location.origin
  return `${origin}/monitor?key=${userStore.userInfo.secretKey}`
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
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
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
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
  }
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
.secret-card {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.profile-card h2,
.secret-card h2 {
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
</style>
