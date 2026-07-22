<template>
  <header class="app-header">
    <div class="header-content">
      <div class="logo">
        <h1>设备使用时间追踪</h1>
      </div>
      
      <nav class="nav-menu" v-if="userStore.isLoggedIn">
        <el-menu
          mode="horizontal"
          :default-active="activeMenu"
          :router="true"
        >
          <el-menu-item index="/monitor">实时监控</el-menu-item>
          <el-menu-item index="/profile">个人中心</el-menu-item>
        </el-menu>
      </nav>
      
      <div class="user-info">
        <template v-if="userStore.isLoggedIn">
          <span class="username">{{ userStore.userInfo?.email }}</span>
          <el-button type="text" @click="handleLogout">退出登录</el-button>
        </template>
        <template v-else>
          <el-button type="primary" @click="router.push('/login')">登录</el-button>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)

const handleLogout = async () => {
  try {
    await userStore.logout()
    ElMessage.success('退出成功')
    router.push('/login')
  } catch (error) {
    ElMessage.error('退出失败')
  }
}
</script>

<style scoped>
.app-header {
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  position: sticky;
  top: 0;
  z-index: 1000;
}

.header-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
}

.logo h1 {
  margin: 0;
  font-size: 20px;
  color: #409eff;
}

.nav-menu {
  flex: 1;
  margin: 0 40px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.username {
  color: #606266;
}
</style>
