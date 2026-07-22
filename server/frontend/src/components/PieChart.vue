<template>
  <div class="pie-chart">
    <Pie :data="chartData" :options="chartOptions" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Pie } from 'vue-chartjs'
import {
  Chart as ChartJS,
  ArcElement,
  Tooltip,
  Legend,
  type ChartData,
  type ChartOptions
} from 'chart.js'

ChartJS.register(ArcElement, Tooltip, Legend)

interface DataItem {
  name: string
  value: number
}

const props = defineProps<{
  data: DataItem[]
  title?: string
}>()

const chartData = computed<ChartData<'pie'>>(() => {
  return {
    labels: props.data.map(item => item.name),
    datasets: [
      {
        data: props.data.map(item => item.value),
        backgroundColor: [
          '#409eff',
          '#67c23a',
          '#e6a23c',
          '#f56c6c',
          '#909399',
          '#c0c4cc',
          '#79bbff',
          '#95d475'
        ]
      }
    ]
  }
})

const chartOptions = computed<ChartOptions<'pie'>>(() => {
  return {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'right',
        labels: {
          padding: 15,
          font: {
            size: 12
          }
        }
      },
      tooltip: {
        callbacks: {
          label: (context) => {
            const label = context.label || ''
            const value = context.parsed
            const total = context.dataset.data.reduce((a: number, b: number) => a + b, 0)
            const percentage = ((value / total) * 100).toFixed(1)
            return `${label}: ${value}s (${percentage}%)`
          }
        }
      }
    }
  }
})
</script>

<style scoped>
.pie-chart {
  width: 100%;
  height: 300px;
}
</style>
