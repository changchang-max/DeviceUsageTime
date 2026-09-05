<template>
  <div class="login-page" ref="pageRef" @mousemove="handleMouseMove">
    <!-- Canvas Background -->
    <canvas ref="canvasRef" class="canvas-bg"></canvas>
    
    <!-- Dynamic Glow -->
    <div 
      class="glow-layer" 
      :style="{ 
        background: `radial-gradient(circle at ${mouseX}px ${mouseY}px, rgba(99, 102, 241, 0.15), transparent 40%)`
      }"
    ></div>
    
    <!-- Custom Cursor -->
    <div class="cursor-dot" :style="{ left: mouseX + 'px', top: mouseY + 'px' }"></div>
    <div class="cursor-ring" :style="{ left: mouseX + 'px', top: mouseY + 'px', transform: `translate(-50%, -50%) scale(${isHoveringButton ? 1.5 : 1})` }"></div>
    
    <!-- Main Container -->
    <div class="main-container" ref="containerRef">
      <!-- Decorative Elements -->
      <div class="decoration-grid"></div>
      <div class="decoration-noise"></div>
      
      <!-- Header -->
      <div class="main-header">
        <h1 class="title">
          <span v-for="(char, index) in titleChars" :key="index" :style="{ animationDelay: `${index * 0.05}s` }">{{ char }}</span>
        </h1>
        <p class="subtitle">输入秘钥查看设备使用数据</p>
      </div>
      
      <!-- Key Input Section -->
      <div class="key-input-section">
        <div class="input-row">
          <div class="input-wrapper">
            <el-input
              ref="keyInputRef"
              v-model="keyValue"
              placeholder="请输入秘钥..."
              clearable
              size="large"
              @focus="handleInputFocus"
              @blur="handleInputBlur"
              @keyup.enter="handleVerifyKey"
            >
              <template #prefix>
                <el-icon><Key /></el-icon>
              </template>
            </el-input>
            <div class="input-glow"></div>
          </div>
          <button
            type="button"
            class="access-btn"
            :class="{ 'is-loading': loading }"
            :disabled="loading || !keyValue.trim()"
            @click="handleVerifyKey"
            @mouseenter="isHoveringButton = true"
            @mouseleave="isHoveringButton = false"
          >
            <span class="btn-text">{{ loading ? '验证中...' : '访问' }}</span>
            <span class="btn-shine"></span>
            <span class="btn-arrow">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
                <path d="M5 12h14M12 5l7 7-7 7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </span>
          </button>
        </div>
      </div>
      
      <!-- Divider -->
      <div class="divider">
        <span class="divider-line"></span>
        <span class="divider-text">或</span>
        <span class="divider-line"></span>
      </div>
      
      <!-- Action Icons -->
      <div class="action-icons">
        <div 
          class="icon-item"
          @click="router.push('/auth/login')"
          @mouseenter="isHoveringButton = true"
          @mouseleave="isHoveringButton = false"
        >
          <div class="icon-circle">
            <el-icon :size="28"><User /></el-icon>
          </div>
          <span class="icon-label">用户登录</span>
        </div>
        
        <div 
          class="icon-item"
          @click="router.push('/register')"
          @mouseenter="isHoveringButton = true"
          @mouseleave="isHoveringButton = false"
        >
          <div class="icon-circle">
            <el-icon :size="28"><UserFilled /></el-icon>
          </div>
          <span class="icon-label">注册账号</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { verifyKeyApi } from '@/api'
import { ElMessage } from 'element-plus'
import { Key, User, UserFilled } from '@element-plus/icons-vue'

const router = useRouter()

// Refs
const pageRef = ref<HTMLElement>()
const containerRef = ref<HTMLElement>()
const canvasRef = ref<HTMLCanvasElement>()
const keyInputRef = ref()

// State
const loading = ref(false)
const mouseX = ref(0)
const mouseY = ref(0)
const isHoveringButton = ref(false)
const titleChars = '设备使用时间追踪'.split('')
const keyValue = ref('')

// Canvas animation
let animationId: number
let particles: Array<{x: number, y: number, vx: number, vy: number, size: number, alpha: number}>

const initCanvas = () => {
  if (!canvasRef.value) return
  
  const canvas = canvasRef.value
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  
  // Set canvas size
  const setCanvasSize = () => {
    canvas.width = window.innerWidth * window.devicePixelRatio
    canvas.height = window.innerHeight * window.devicePixelRatio
    canvas.style.width = window.innerWidth + 'px'
    canvas.style.height = window.innerHeight + 'px'
    ctx.scale(window.devicePixelRatio, window.devicePixelRatio)
  }
  
  setCanvasSize()
  window.addEventListener('resize', setCanvasSize)
  
  // Create particles
  particles = []
  const particleCount = Math.min(80, Math.floor(window.innerWidth * window.innerHeight / 15000))
  
  for (let i = 0; i < particleCount; i++) {
    particles.push({
      x: Math.random() * window.innerWidth,
      y: Math.random() * window.innerHeight,
      vx: (Math.random() - 0.5) * 0.5,
      vy: (Math.random() - 0.5) * 0.5,
      size: Math.random() * 2 + 1,
      alpha: Math.random() * 0.5 + 0.2
    })
  }
  
  // Animation loop
  const animate = () => {
    ctx.clearRect(0, 0, window.innerWidth, window.innerHeight)
    
    particles.forEach(p => {
      // Update position
      p.x += p.vx
      p.y += p.vy
      
      // Wrap around edges
      if (p.x < 0) p.x = window.innerWidth
      if (p.x > window.innerWidth) p.x = 0
      if (p.y < 0) p.y = window.innerHeight
      if (p.y > window.innerHeight) p.y = 0
      
      // Draw particle
      ctx.beginPath()
      ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(99, 102, 241, ${p.alpha})`
      ctx.fill()
    })
    
    // Draw connections
    particles.forEach((p1, i) => {
      particles.slice(i + 1).forEach(p2 => {
        const dx = p1.x - p2.x
        const dy = p1.y - p2.y
        const distance = Math.sqrt(dx * dx + dy * dy)
        
        if (distance < 150) {
          ctx.beginPath()
          ctx.moveTo(p1.x, p1.y)
          ctx.lineTo(p2.x, p2.y)
          ctx.strokeStyle = `rgba(99, 102, 241, ${0.1 * (1 - distance / 150)})`
          ctx.stroke()
        }
      })
    })
    
    animationId = requestAnimationFrame(animate)
  }
  
  animate()
}

// Mouse tracking
const handleMouseMove = (e: MouseEvent) => {
  mouseX.value = e.clientX
  mouseY.value = e.clientY
}

// Input interactions
const handleInputFocus = (e: FocusEvent) => {
  const target = e.target as HTMLElement
  const wrapper = target.closest('.input-wrapper')
  if (wrapper) {
    wrapper.classList.add('focused')
  }
}

const handleInputBlur = (e: FocusEvent) => {
  const target = e.target as HTMLElement
  const wrapper = target.closest('.input-wrapper')
  if (wrapper) {
    wrapper.classList.remove('focused')
  }
}

// Verify key
const handleVerifyKey = async () => {
  if (!keyValue.value.trim()) {
    ElMessage.warning('请输入秘钥')
    return
  }
  
  loading.value = true
  try {
    const res = await verifyKeyApi(keyValue.value.trim())
    ElMessage.success('验证成功')
    
    // 跳转到监控页面
    router.push({
      path: '/monitor',
      query: { key: keyValue.value.trim() }
    })
  } catch {
    // 错误提示已由请求拦截器统一弹出(如"秘钥无效或已失效")
  } finally {
    loading.value = false
  }
}

// Lifecycle
onMounted(() => {
  initCanvas()
  // Auto focus on input
  nextTick(() => {
    keyInputRef.value?.focus()
  })
})

onUnmounted(() => {
  if (animationId) {
    cancelAnimationFrame(animationId)
  }
})
</script>

<style scoped>
/* Page Layout */
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f0f23 0%, #1a1a3e 50%, #0f0f23 100%);
  overflow: hidden;
  cursor: none;
}

/* Canvas Background */
.canvas-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

/* Glow Layer */
.glow-layer {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 2;
  transition: background 0.1s ease;
}

/* Custom Cursor */
.cursor-dot {
  position: fixed;
  width: 8px;
  height: 8px;
  background: #6366f1;
  border-radius: 50%;
  pointer-events: none;
  z-index: 9999;
  transform: translate(-50%, -50%);
  mix-blend-mode: difference;
}

.cursor-ring {
  position: fixed;
  width: 40px;
  height: 40px;
  border: 2px solid rgba(99, 102, 241, 0.5);
  border-radius: 50%;
  pointer-events: none;
  z-index: 9998;
  transition: transform 0.15s ease;
}

/* Main Container */
.main-container {
  position: relative;
  z-index: 10;
  width: 100%;
  max-width: 600px;
  padding: 48px 56px;
  background: rgba(255, 255, 255, 0.03);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 24px;
  box-shadow: 
    0 20px 60px rgba(0, 0, 0, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.1);
  animation: containerReveal 0.8s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

@keyframes containerReveal {
  from {
    opacity: 0;
    transform: translateY(40px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* Decorative Elements */
.decoration-grid {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image: 
    linear-gradient(rgba(99, 102, 241, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(99, 102, 241, 0.03) 1px, transparent 1px);
  background-size: 40px 40px;
  pointer-events: none;
  border-radius: 24px;
}

.decoration-noise {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' /%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)' opacity='0.02'/%3E%3C/svg%3E");
  pointer-events: none;
  border-radius: 24px;
}

/* Header */
.main-header {
  text-align: center;
  margin-bottom: 40px;
  position: relative;
}

.title {
  margin: 0 0 16px 0;
  font-size: 38px;
  font-weight: 700;
  letter-spacing: -0.02em;
  background: linear-gradient(135deg, #fff 0%, #a5b4fc 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.title span {
  display: inline-block;
  animation: titleReveal 0.6s cubic-bezier(0.16, 1, 0.3, 1) forwards;
  opacity: 0;
  transform: translateY(20px);
}

@keyframes titleReveal {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.subtitle {
  margin: 0;
  color: rgba(255, 255, 255, 0.5);
  font-size: 15px;
  font-weight: 400;
  letter-spacing: 0.02em;
  animation: subtitleReveal 0.8s cubic-bezier(0.16, 1, 0.3, 1) 0.4s forwards;
  opacity: 0;
}

@keyframes subtitleReveal {
  to {
    opacity: 1;
  }
}

/* Key Input Section */
.key-input-section {
  margin-bottom: 32px;
  animation: sectionReveal 0.6s cubic-bezier(0.16, 1, 0.3, 1) 0.3s forwards;
  opacity: 0;
  transform: translateY(20px);
}

@keyframes sectionReveal {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.input-row {
  display: flex;
  gap: 12px;
  align-items: stretch;
}

.input-wrapper {
  flex: 1;
  position: relative;
}

.input-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 90%;
  height: 0;
  background: radial-gradient(ellipse at center, rgba(99, 102, 241, 0.2) 0%, transparent 70%);
  transform: translate(-50%, -50%);
  pointer-events: none;
  opacity: 0;
  transition: all 0.3s ease;
}

.input-wrapper.focused .input-glow {
  height: 120%;
  opacity: 1;
}

/* Override Element Plus Input Styles */
:deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  box-shadow: none;
  padding: 12px 16px;
  transition: all 0.3s ease;
  height: 52px;
}

:deep(.el-input__wrapper):hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.15);
}

:deep(.el-input__wrapper.is-focus) {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(99, 102, 241, 0.5);
  box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.1);
}

:deep(.el-input__inner) {
  color: #fff;
  font-size: 15px;
}

:deep(.el-input__inner::placeholder) {
  color: rgba(255, 255, 255, 0.35);
}

:deep(.el-icon) {
  color: rgba(255, 255, 255, 0.5);
}

/* Access Button */
.access-btn {
  position: relative;
  height: 52px;
  padding: 0 32px;
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  border: none;
  border-radius: 12px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.02em;
  cursor: none;
  overflow: hidden;
  transition: all 0.3s ease;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.access-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 
    0 10px 30px rgba(99, 102, 241, 0.3),
    0 0 20px rgba(99, 102, 241, 0.2);
}

.access-btn:active:not(:disabled) {
  transform: translateY(0);
}

.access-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.access-btn.is-loading {
  pointer-events: none;
}

.btn-text {
  position: relative;
  z-index: 2;
}

.btn-shine {
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.3), transparent);
  transition: left 0.5s ease;
}

.access-btn:hover:not(:disabled) .btn-shine {
  left: 100%;
}

.btn-arrow {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  opacity: 0;
  transform: translateX(-5px);
  transition: all 0.3s ease;
}

.access-btn:hover:not(:disabled) .btn-arrow {
  opacity: 1;
  transform: translateX(0);
}

/* Divider */
.divider {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
  animation: dividerReveal 0.6s cubic-bezier(0.16, 1, 0.3, 1) 0.4s forwards;
  opacity: 0;
}

@keyframes dividerReveal {
  to {
    opacity: 1;
  }
}

.divider-line {
  flex: 1;
  height: 1px;
  background: rgba(255, 255, 255, 0.1);
}

.divider-text {
  color: rgba(255, 255, 255, 0.4);
  font-size: 14px;
  font-weight: 500;
}

/* Action Icons */
.action-icons {
  display: flex;
  justify-content: center;
  gap: 48px;
  animation: iconsReveal 0.6s cubic-bezier(0.16, 1, 0.3, 1) 0.5s forwards;
  opacity: 0;
  transform: translateY(20px);
}

@keyframes iconsReveal {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.icon-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  cursor: none;
  transition: transform 0.3s ease;
}

.icon-item:hover {
  transform: translateY(-4px);
}

.icon-circle {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: rgba(99, 102, 241, 0.1);
  border: 1px solid rgba(99, 102, 241, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a5b4fc;
  transition: all 0.3s ease;
}

.icon-item:hover .icon-circle {
  background: rgba(99, 102, 241, 0.2);
  border-color: rgba(99, 102, 241, 0.5);
  box-shadow: 0 0 20px rgba(99, 102, 241, 0.3);
  color: #c7d2fe;
}

.icon-label {
  color: rgba(255, 255, 255, 0.6);
  font-size: 14px;
  font-weight: 500;
  transition: color 0.3s ease;
}

.icon-item:hover .icon-label {
  color: rgba(255, 255, 255, 0.8);
}

/* Responsive */
@media (max-width: 768px) {
  .main-container {
    margin: 20px;
    padding: 32px 24px;
  }
  
  .title {
    font-size: 28px;
  }
  
  .input-row {
    flex-direction: column;
  }
  
  .access-btn {
    width: 100%;
  }
  
  .action-icons {
    gap: 32px;
  }
  
  .icon-circle {
    width: 56px;
    height: 56px;
  }
  
  .cursor-dot,
  .cursor-ring {
    display: none;
  }
  
  .login-page {
    cursor: auto;
  }
}

/* Accessibility */
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
