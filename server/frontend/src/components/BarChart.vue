<template>
  <div class="bar-chart">
    <Bar :data="chartData" :options="chartOptions" :plugins="plugins" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Bar } from 'vue-chartjs'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  Title,
  Tooltip,
  Legend,
  type ChartData,
  type ChartOptions,
  type Plugin
} from 'chart.js'
import { formatDuration } from '@/utils/format'

ChartJS.register(CategoryScale, LinearScale, BarElement, Title, Tooltip, Legend)

interface DataItem {
  name: string
  value: number
}

const props = defineProps<{
  data: DataItem[]
  title?: string
}>()

// ---------------------------------------------------------------------------
// 坐标刻度步长: 依据当前最大时长自适应选取整秒/整分候选值,
// 避免出现"小时级粗刻度",让秒/分钟级的变化在坐标上有对应刻度可对照。
// ---------------------------------------------------------------------------
const AXIS_STEP_CANDIDATES = [
  1, 2, 5, 10, 15, 20, 30, 60, 120, 300, 600, 900, 1200, 1800,
  3600, 5400, 7200, 10800, 14400, 21600, 28800, 43200, 86400
] as const

const pickAxisStep = (maxValue: number): number => {
  const rawStep = maxValue / 8
  for (const candidate of AXIS_STEP_CANDIDATES) {
    if (candidate >= rawStep) {
      return candidate
    }
  }
  return AXIS_STEP_CANDIDATES[AXIS_STEP_CANDIDATES.length - 1]
}

// ---------------------------------------------------------------------------
// 自定义插件: 在每条条形末端绘制实时时长文本。
// 数值通过 getValueForPixel 读取动画中的实时像素位置换算而来,
// 使条形增长的同时末尾的 HH:MM:SS 也逐秒跳动,实时推送的变化清晰可见。
// ---------------------------------------------------------------------------
const durationLabelsPlugin: Plugin<'bar'> = {
  id: 'durationLabels',
  afterDatasetsDraw(chart) {
    const { ctx, chartArea } = chart
    if (!chartArea || chartArea.width <= 0) {
      return
    }

    const meta = chart.getDatasetMeta(0)
    if (!meta || meta.hidden || meta.data.length === 0) {
      return
    }

    const xScale = chart.scales.x as unknown as {
      getValueForPixel(pixel: number): number
    }

    ctx.save()
    ctx.font = '600 12px system-ui, -apple-system, "Segoe UI", Roboto, sans-serif'
    ctx.textBaseline = 'middle'

    for (let index = 0; index < meta.data.length; index++) {
      const element = meta.data[index] as unknown as {
        x: number
        y: number
        base: number
      }

      const seconds = Math.round(xScale.getValueForPixel(element.x))
      if (!Number.isFinite(seconds) || seconds <= 0) {
        continue
      }

      const label = formatDuration(seconds)
      const labelWidth = ctx.measureText(label).width
      const gap = 6
      const rightBound = chartArea.right - 4

      if (element.x + gap + labelWidth <= rightBound) {
        // 条形末端与图表右边界之间有空余: 标签画在条形右侧
        ctx.textAlign = 'left'
        ctx.fillStyle = '#909399'
        ctx.fillText(label, element.x + gap, element.y)
      } else if (element.x - element.base >= labelWidth + gap) {
        // 条形接近右边界: 白色标签画在条形内部右端
        ctx.textAlign = 'right'
        ctx.fillStyle = 'rgba(255, 255, 255, 0.95)'
        ctx.fillText(label, element.x - gap, element.y)
      }
    }
    ctx.restore()
  }
}

const plugins: Plugin<'bar'>[] = [durationLabelsPlugin]

const chartData = computed<ChartData<'bar'>>(() => {
  return {
    labels: props.data.map(item => item.name),
    datasets: [
      {
        label: '使用时长',
        data: props.data.map(item => item.value),
        backgroundColor: '#409eff'
      }
    ]
  }
})

const chartOptions = computed<ChartOptions<'bar'>>(() => {
  const values = props.data.map(item => item.value)
  const topValue = values.length > 0 ? Math.max(...values) : 0
  const step = pickAxisStep(topValue)
  // 坐标上界取步长的整数倍,保证刻度规整; 数据全为0时预留给 1 分钟的生长空间
  const axisMax = topValue <= 0 ? 60 : Math.ceil(topValue / step) * step

  return {
    responsive: true,
    maintainAspectRatio: false,
    indexAxis: 'y',
    // 每次推送都从旧值平滑增长到新值; 动画时长(400ms)小于推送间隔(约1s),
    // 避免动画互相打断堆积,导致图表看起来几乎静止
    animation: {
      duration: 400,
      easing: 'easeOutQuart'
    },
    plugins: {
      legend: {
        display: false
      },
      tooltip: {
        callbacks: {
          label: (context) => {
            return `时长: ${formatDuration(Math.round(context.parsed.x ?? 0))}`
          }
        }
      }
    },
    scales: {
      x: {
        beginAtZero: true,
        min: 0,
        max: axisMax,
        ticks: {
          maxTicksLimit: 10,
          maxRotation: 0,
          callback: (value) => {
            return formatDuration(Number(value))
          }
        }
      }
    }
  }
})
</script>

<style scoped>
.bar-chart {
  width: 100%;
  height: 400px;
}
</style>
