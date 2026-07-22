<template>
  <div class="calendar-component">
    <div class="calendar-header">
      <el-button
        :icon="ArrowLeft"
        circle
        @click="prevMonth"
      />
      <span class="current-month">{{ currentYearMonth }}</span>
      <el-button
        :icon="ArrowRight"
        circle
        @click="nextMonth"
      />
    </div>
    
    <div class="quick-actions">
      <el-button size="small" @click="selectToday">今天</el-button>
      <el-button size="small" @click="selectYesterday">昨天</el-button>
    </div>
    
    <div class="calendar-grid">
      <div class="weekday-header">
        <div v-for="day in weekdays" :key="day" class="weekday">
          {{ day }}
        </div>
      </div>
      
      <div class="dates-grid">
        <div
          v-for="date in calendarDates"
          :key="date.dateString"
          class="date-cell"
          :class="{
            'other-month': !date.isCurrentMonth,
            'has-data': date.hasData,
            'selected': date.dateString === selectedDate,
            'today': date.isToday
          }"
          @click="selectDate(date)"
        >
          <span class="date-number">{{ date.day }}</span>
          <span v-if="date.hasData" class="data-dot"></span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'

interface CalendarDate {
  dateString: string
  day: number
  isCurrentMonth: boolean
  hasData: boolean
  isToday: boolean
}

const props = defineProps<{
  dataDates?: string[]
  modelValue?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'date-change': [date: string]
}>()

const currentDate = ref(new Date())
const selectedDate = ref(props.modelValue || '')
const weekdays = ['日', '一', '二', '三', '四', '五', '六']

const currentYearMonth = computed(() => {
  const year = currentDate.value.getFullYear()
  const month = (currentDate.value.getMonth() + 1).toString().padStart(2, '0')
  return `${year}年${month}月`
})

const calendarDates = computed<CalendarDate[]>(() => {
  const year = currentDate.value.getFullYear()
  const month = currentDate.value.getMonth()
  
  const firstDay = new Date(year, month, 1)
  const lastDay = new Date(year, month + 1, 0)
  const firstDayOfWeek = firstDay.getDay()
  
  const dates: CalendarDate[] = []
  
  // 上个月的日期
  const prevMonthLastDay = new Date(year, month, 0).getDate()
  for (let i = firstDayOfWeek - 1; i >= 0; i--) {
    const day = prevMonthLastDay - i
    const dateString = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
    dates.push({
      dateString,
      day,
      isCurrentMonth: false,
      hasData: false,
      isToday: false
    })
  }
  
  // 当月的日期
  const today = new Date()
  const todayString = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  
  for (let day = 1; day <= lastDay.getDate(); day++) {
    const dateString = `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
    dates.push({
      dateString,
      day,
      isCurrentMonth: true,
      hasData: props.dataDates?.includes(dateString) || false,
      isToday: dateString === todayString
    })
  }
  
  // 下个月的日期(补齐6行)
  const remainingDays = 42 - dates.length
  for (let day = 1; day <= remainingDays; day++) {
    const dateString = `${year}-${String(month + 2).padStart(2, '0')}-${String(day).padStart(2, '0')}`
    dates.push({
      dateString,
      day,
      isCurrentMonth: false,
      hasData: false,
      isToday: false
    })
  }
  
  return dates
})

const selectDate = (date: CalendarDate) => {
  if (!date.isCurrentMonth) return
  selectedDate.value = date.dateString
  emit('update:modelValue', date.dateString)
  emit('date-change', date.dateString)
}

const prevMonth = () => {
  currentDate.value = new Date(
    currentDate.value.getFullYear(),
    currentDate.value.getMonth() - 1,
    1
  )
}

const nextMonth = () => {
  currentDate.value = new Date(
    currentDate.value.getFullYear(),
    currentDate.value.getMonth() + 1,
    1
  )
}

const selectToday = () => {
  const today = new Date()
  currentDate.value = today
  const todayString = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  selectDate({
    dateString: todayString,
    day: today.getDate(),
    isCurrentMonth: true,
    hasData: false,
    isToday: true
  })
}

const selectYesterday = () => {
  const yesterday = new Date()
  yesterday.setDate(yesterday.getDate() - 1)
  currentDate.value = yesterday
  const yesterdayString = `${yesterday.getFullYear()}-${String(yesterday.getMonth() + 1).padStart(2, '0')}-${String(yesterday.getDate()).padStart(2, '0')}`
  selectDate({
    dateString: yesterdayString,
    day: yesterday.getDate(),
    isCurrentMonth: true,
    hasData: false,
    isToday: false
  })
}

watch(() => props.modelValue, (newVal) => {
  if (newVal) selectedDate.value = newVal
})

onMounted(() => {
  if (!selectedDate.value) {
    selectToday()
  }
})
</script>

<style scoped>
.calendar-component {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.calendar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.current-month {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.quick-actions {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.weekday-header {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
  margin-bottom: 8px;
}

.weekday {
  text-align: center;
  font-size: 14px;
  color: #909399;
  padding: 8px 0;
}

.dates-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}

.date-cell {
  aspect-ratio: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  cursor: pointer;
  position: relative;
  transition: all 0.2s;
}

.date-cell:hover {
  background: #f5f7fa;
}

.date-cell.other-month {
  color: #c0c4cc;
  cursor: not-allowed;
}

.date-cell.other-month:hover {
  background: transparent;
}

.date-cell.has-data {
  font-weight: 600;
}

.date-cell.selected {
  background: #409eff;
  color: #fff;
}

.date-cell.today {
  border: 2px solid #409eff;
}

.date-number {
  font-size: 14px;
}

.data-dot {
  width: 6px;
  height: 6px;
  background: #67c23a;
  border-radius: 50%;
  position: absolute;
  bottom: 4px;
}

.date-cell.selected .data-dot {
  background: #fff;
}
</style>
