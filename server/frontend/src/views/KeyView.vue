<template>
  <div class="key-view-page">
    <div class="key-view-container">
      <div class="key-view-header">
        <h2>输入秘钥查看</h2>
        <p>输入秘钥即可查看他人的设备使用数据,无需注册</p>
      </div>
      
      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-position="top"
        size="large"
      >
        <el-form-item label="秘钥" prop="key">
          <el-input
            v-model="formData.key"
            placeholder="请输入秘钥"
            clearable
            @keyup.enter="handleVerify"
          />
        </el-form-item>
        
        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            style="width: 100%"
            @click="handleVerify"
          >
            查看
          </el-button>
        </el-form-item>
      </el-form>
      
      <div class="footer-tip">
        <el-icon><InfoFilled /></el-icon>
        <span>您只能查看数据,无法修改任何内容</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { verifyKeyApi } from '@/api'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'

const router = useRouter()

const formRef = ref<FormInstance>()
const loading = ref(false)

const formData = reactive({
  key: ''
})

const rules: FormRules = {
  key: [
    { required: true, message: '请输入秘钥', trigger: 'blur' },
    { min: 10, message: '秘钥格式不正确', trigger: 'blur' }
  ]
}

const handleVerify = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    
    loading.value = true
    try {
      const res = await verifyKeyApi(formData.key)
      ElMessage.success('验证成功')
      
      // 跳转到监控页面
      router.push({
        path: '/monitor',
        query: { key: formData.key }
      })
    } catch (error: any) {
      ElMessage.error(error.message || '秘钥无效或已失效')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.key-view-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.key-view-container {
  width: 420px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}

.key-view-header {
  text-align: center;
  margin-bottom: 32px;
}

.key-view-header h2 {
  margin: 0 0 8px 0;
  font-size: 28px;
  color: #303133;
}

.key-view-header p {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.footer-tip {
  text-align: center;
  margin-top: 20px;
  color: #909399;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
</style>
