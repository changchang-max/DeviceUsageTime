<template>
  <div class="bar-chart">
    <Bar :data="chartData" :options="chartOptions" />
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
  type ChartOptions
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
  return {
    responsive: true,
    maintainAspectRatio: false,
    indexAxis: 'y',
    plugins: {
      legend: {
        display: false
      },
      tooltip: {
        callbacks: {
          label: (context) => {
            return `时长: ${formatDuration(context.parsed.x ?? 0)}`
          }
        }
      }
    },
    scales: {
      x: {
        beginAtZero: true,
        ticks: {
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
