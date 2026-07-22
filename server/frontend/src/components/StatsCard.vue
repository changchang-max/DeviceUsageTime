<template>
  <div class="stats-card" :class="type">
    <div class="icon-wrapper">
      <el-icon :size="28" class="icon">
        <component :is="iconComponent" />
      </el-icon>
    </div>
    <div class="content">
      <div class="label">{{ label }}</div>
      <div class="value">{{ formattedValue }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Edit, Mouse as MouseIcon, Position } from '@element-plus/icons-vue'
import { formatNumber, formatDistance } from '@/utils/format'

const props = defineProps<{
  type: 'keyboard' | 'mouse-click' | 'mouse-distance'
  value: number
  label: string
}>()

const iconComponent = computed(() => {
  switch (props.type) {
    case 'keyboard':
      return Edit
    case 'mouse-click':
      return MouseIcon
    case 'mouse-distance':
      return Position
    default:
      return Edit
  }
})

const formattedValue = computed(() => {
  switch (props.type) {
    case 'keyboard':
    case 'mouse-click':
      return formatNumber(props.value) + '次'
    case 'mouse-distance':
      return formatDistance(props.value)
    default:
      return String(props.value)
  }
})
</script>

<style scoped>
.stats-card {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 24px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  transition: transform 0.2s, box-shadow 0.2s;
}

.stats-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}

.icon-wrapper {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stats-card.keyboard .icon-wrapper {
  background: #e8f4ff;
}

.stats-card.mouse-click .icon-wrapper {
  background: #e8f8f0;
}

.stats-card.mouse-distance .icon-wrapper {
  background: #fef0e8;
}

.icon {
  font-size: 28px;
}

.stats-card.keyboard .icon {
  color: #409eff;
}

.stats-card.mouse-click .icon {
  color: #67c23a;
}

.stats-card.mouse-distance .icon {
  color: #e6a23c;
}

.content {
  flex: 1;
}

.label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}
</style>
