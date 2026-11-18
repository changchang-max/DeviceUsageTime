<template>
  <section class="calendar">
    <header class="calendar-head">
      <button
        type="button"
        class="nav-button"
        aria-label="上个月"
        @click="shiftMonth(-1)"
      >
        <el-icon><ArrowLeft /></el-icon>
      </button>

      <div class="month-label">
        <h2 class="month-title">{{ currentYearLabel }}</h2>
        <span class="month-sub">{{ currentYearMonth }}</span>
      </div>

      <button
        type="button"
        class="nav-button"
        aria-label="下个月"
        @click="shiftMonth(1)"
      >
        <el-icon><ArrowRight /></el-icon>
      </button>
    </header>

    <div class="quick-actions">
      <button type="button" class="chip" :class="{ 'is-current': selectedDate === todayKey }" @click="selectDate(todayKey)">
        今天
      </button>
      <button type="button" class="chip" @click="selectDate(yesterdayKey)">
        昨天
      </button>
      <button type="button" class="chip" @click="jumpToLatest">
        最近有数据
      </button>
    </div>

    <div class="weekdays" aria-hidden="true">
      <span v-for="day in WEEKDAYS" :key="day">{{ day }}</span>
    </div>

    <div class="dates" role="grid" :aria-label="`${currentYearLabel} 日期选择`">
      <button
        v-for="date in calendarDates"
        :key="date.dateString"
        type="button"
        role="gridcell"
        class="date-cell"
        :class="{
          'is-outside': !date.isCurrentMonth,
          'is-today': date.isToday,
          'is-selected': date.dateString === selectedDate,
          'has-data': date.hasData
        }"
        :disabled="!date.isCurrentMonth || date.isFuture"
        :aria-current="date.isToday ? 'date' : undefined"
        :aria-selected="date.dateString === selectedDate"
        :aria-label="dateLabel(date)"
        @click="selectDate(date.dateString)"
      >
        <span class="date-number tabular">{{ date.day }}</span>
        <span v-if="date.hasData" class="data-dot" aria-hidden="true"></span>
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { toLocalDateKey } from '@/utils/format'

interface CalendarDate {
  dateString: string
  day: number
  isCurrentMonth: boolean
  hasData: boolean
  isToday: boolean
  isFuture: boolean
}

const props = withDefaults(defineProps<{
  /** 有数据记录的日期列表(YYYY-MM-DD) */
  dataDates?: string[]
  /** 当前选中日期(YYYY-MM-DD) */
  modelValue?: string
}>(), {
  dataDates: () => [],
  modelValue: ''
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'date-change': [date: string]
}>()

const WEEKDAYS = ['一', '二', '三', '四', '五', '六', '日']

const todayKey = toLocalDateKey()
const yesterdayKey = (() => {
  const d = new Date()
  d.setDate(d.getDate() - 1)
  return toLocalDateKey(d)
})()

const cursor = ref(new Date())
const selectedDate = ref(props.modelValue || todayKey)

const currentYearLabel = computed(() => `${cursor.value.getFullYear()} 年 ${cursor.value.getMonth() + 1} 月`)

const currentYearMonth = computed(
  () => `${cursor.value.getFullYear()}-${String(cursor.value.getMonth() + 1).padStart(2, '0')}`
)

// 数据集转 Set，避免每个格子都 includes 扫一遍
const dataDateSet = computed(() => new Set(props.dataDates))

const calendarDates = computed<CalendarDate[]>(() => {
  const year = cursor.value.getFullYear()
  const month = cursor.value.getMonth()
  const firstDay = new Date(year, month, 1)
  // 周一为一周第一天：JS getDay() 周日为 0，这里换算成 0=周一
  const leading = (firstDay.getDay() + 6) % 7
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const daysInPrevMonth = new Date(year, month, 0).getDate()

  const dates: CalendarDate[] = []

  for (let i = leading - 1; i >= 0; i -= 1) {
    const day = daysInPrevMonth - i
    dates.push({
      dateString: `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`,
      day,
      isCurrentMonth: false,
      hasData: false,
      isToday: false,
      isFuture: false
    })
  }

  for (let day = 1; day <= daysInMonth; day += 1) {
    const dateString = `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
    dates.push({
      dateString,
      day,
      isCurrentMonth: true,
      hasData: dataDateSet.value.has(dateString),
      isToday: dateString === todayKey,
      isFuture: dateString > todayKey
    })
  }

  // 补齐到 6 行(42 格)，保证月份切换时高度不跳动
  let nextMonthDay = 1
  while (dates.length < 42) {
    const dateString = `${month === 11 ? year + 1 : year}-${String(((month + 1) % 12) + 1).padStart(2, '0')}-${String(nextMonthDay).padStart(2, '0')}`
    dates.push({
      dateString,
      day: nextMonthDay,
      isCurrentMonth: false,
      hasData: false,
      isToday: false,
      isFuture: true
    })
    nextMonthDay += 1
  }

  return dates
})

const dateLabel = (date: CalendarDate): string => {
  const base = `${date.dateString}${date.isToday ? ' 今天' : ''}`
  return date.hasData ? `${base}，有数据` : `${base}，无数据`
}

const selectDate = (dateString: string) => {
  if (dateString > todayKey) return
  selectedDate.value = dateString
  // 选中其他月份时同步切换日历视图
  const [y, m] = dateString.split('-').map(Number)
  if (y !== cursor.value.getFullYear() || m - 1 !== cursor.value.getMonth()) {
    cursor.value = new Date(y, m - 1, 1)
  }
  emit('update:modelValue', dateString)
  emit('date-change', dateString)
}

const shiftMonth = (delta: number) => {
  cursor.value = new Date(cursor.value.getFullYear(), cursor.value.getMonth() + delta, 1)
}

// 跳到最近一个有数据的日期(本月内)
const jumpToLatest = () => {
  const latest = [...props.dataDates].sort().reverse().find((date) => date <= todayKey)
  if (!latest) return
  selectDate(latest)
}

watch(() => props.modelValue, (value) => {
  if (value && value !== selectedDate.value) {
    selectedDate.value = value
  }
})
</script>

<style scoped>
.calendar {
  background: var(--surface-1);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: var(--space-5);
}

.calendar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.month-label {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.month-title {
  font-size: var(--text-base);
  font-weight: 600;
}

.month-sub {
  font-size: var(--text-xs);
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

/* 导航按钮：44×44 触控目标 */
.nav-button {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-1);
  color: var(--text-muted);
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.nav-button:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.quick-actions {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
  flex-wrap: wrap;
}

.chip {
  min-height: 28px;
  padding: 0 var(--space-3);
  border: 1px solid var(--border);
  border-radius: var(--radius-pill);
  background: var(--surface-1);
  color: var(--text-muted);
  font-family: inherit;
  font-size: var(--text-xs);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
}

.chip:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.chip.is-current {
  background: var(--primary-soft);
  border-color: transparent;
  color: var(--primary);
  font-weight: 500;
}

.weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: var(--space-1);
  margin-bottom: var(--space-2);
}

.weekdays span {
  text-align: center;
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.dates {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: var(--space-1);
}

.date-cell {
  position: relative;
  aspect-ratio: 1;
  min-height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  font-family: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
  transition: background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out);
}

.date-cell:hover:not(:disabled) {
  background: var(--surface-2);
}

.date-cell.is-outside {
  color: var(--border-strong);
}

.date-cell.is-outside,
.date-cell:disabled {
  cursor: default;
}

.date-cell.is-today {
  border-color: var(--primary);
  color: var(--primary);
  font-weight: 600;
}

.date-cell.is-selected {
  background: var(--primary);
  border-color: var(--primary);
  color: var(--text-on-primary);
  font-weight: 600;
}

.date-cell.has-data .date-number {
  font-weight: 600;
}

.data-dot {
  position: absolute;
  bottom: 4px;
  width: 4px;
  height: 4px;
  border-radius: var(--radius-pill);
  background: var(--primary);
}

.date-cell.is-selected .data-dot {
  background: var(--text-on-primary);
}

@media (max-width: 640px) {
  .calendar {
    padding: var(--space-4);
  }

  .date-cell {
    min-height: 32px;
    font-size: var(--text-xs);
  }
}
</style>