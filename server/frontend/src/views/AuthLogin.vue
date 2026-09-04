<template>
  <div class="auth-login-page" ref="pageRef" @mousemove="handleMouseMove">
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
    
    <!-- Login Container -->
    <div class="login-container" ref="containerRef">
      <!-- Decorative Elements -->
      <div class="decoration-grid"></div>
      <div class="decoration-noise"></div>
      
      <!-- Back Button -->
      <button class="back-btn" @click="router.push('/login')" @mouseenter="isHoveringButton = true" @mouseleave="isHoveringButton = false">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
          <path d="M19 12H5M12 19l-7-7 7-7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <span>返回</span>
      </button>
      
      <!-- Header -->
      <div class="login-header">
        <h1 class="title">
          <span v-for="(char, index) in titleChars" :key="index" :style="{ animationDelay: `${index * 0.05}s` }">{{ char }}</span>
        </h1>
        <p class="subtitle">使用邮箱和密码登录您的账号</p>
      </div>
      
      <!-- Form -->
      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-position="top"
        size="large"
        class="login-form"
      >
        <el-form-item label="邮箱" prop="email" class="form-item">
          <div class="input-wrapper">
            <el-input
              v-model="formData.email"
              placeholder="请输入邮箱"
              clearable
              @focus="handleInputFocus"
              @blur="handleInputBlur"
            >
              <template #prefix>
                <el-icon><Message /></el-icon>
              </template>
            </el-input>
            <div class="input-glow"></div>
          </div>
        </el-form-item>
        
        <el-form-item label="密码" prop="password" class="form-item">
          <div class="input-wrapper">
            <el-input
              v-model="formData.password"
              type="password"
              placeholder="请输入密码"
              show-password
              clearable
              @focus="handleInputFocus"
              @blur="handleInputBlur"
              @keyup.enter="handleLogin"
            >
              <template #prefix>
                <el-icon><Lock /></el-icon>
              </template>
            </el-input>
            <div class="input-glow"></div>
          </div>
        </el-form-item>
        
        <el-form-item class="form-item">
          <button
            type="button"
            class="login-btn"
            :class="{ 'is-loading': loading }"
            :disabled="loading"
            @click="handleLogin"
            @mouseenter="isHoveringButton = true"
            @mouseleave="isHoveringButton = false"
          >
            <span class="btn-text">{{ loading ? '登录中...' : '登录' }}</span>
            <span class="btn-shine"></span>
            <span class="btn-arrow">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
                <path d="M5 12h14M12 5l7 7-7 7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </span>
          </button>
        </el-form-item>
      </el-form>
      
      <!-- Footer -->
      <div class="footer-links">
        <span>还没有账号?</span>
        <button class="link-btn" @click="router.push('/register')" @mouseenter="isHoveringButton = true" @mouseleave="isHoveringButton = false">
          立即注册
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <path d="M5 12h14M12 5l7 7-7 7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Message, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

// Refs
const pageRef = ref<HTMLElement>()
const containerRef = ref<HTMLElement>()
const canvasRef = ref<HTMLCanvasElement>()
const formRef = ref<FormInstance>()

// State
const loading = ref(false)
const mouseX = ref(0)
const mouseY = ref(0)
const isHoveringButton = ref(false)
const titleChars = '欢迎回来'.split('')

// Form data
const formData = reactive({
  email: '',
  password: ''
})

// Validation rules
const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码至少8位', trigger: 'blur' }
  ]
}

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

// Login
const handleLogin = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    
    loading.value = true
    try {
      await userStore.login(formData.email, formData.password)
      ElMessage.success('登录成功')
      router.push('/monitor')
    } catch (error: any) {
      ElMessage.error(error.message || '登录失败')
    } finally {
      loading.value = false
    }
  })
}

// Lifecycle
onMounted(() => {
  initCanvas()
})

onUnmounted(() => {
  if (animationId) {
    cancelAnimationFrame(animationId)
  }
})
</script>

<style scoped>
/* Page Layout */
.auth-login-page {
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

/* Login Container */
.login-container {
  position: relative;
  z-index: 10;
  width: 100%;
  max-width: 420px;
  padding: 48px;
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

/* Back Button */
.back-btn {
  position: absolute;
  top: 20px;
  left: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  padding: 8px 16px;
  color: rgba(255, 255, 255, 0.7);
  font-size: 14px;
  cursor: none;
  transition: all 0.3s ease;
  z-index: 20;
}

.back-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.9);
}

.back-btn svg {
  transition: transform 0.3s ease;
}

.back-btn:hover svg {
  transform: translateX(-4px);
}

/* Header */
.login-header {
  text-align: center;
  margin-bottom: 48px;
  position: relative;
}

.title {
  margin: 0 0 16px 0;
  font-size: 42px;
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

/* Form */
.login-form {
  position: relative;
}

.form-item {
  margin-bottom: 28px;
  animation: formItemReveal 0.6s cubic-bezier(0.16, 1, 0.3, 1) forwards;
  opacity: 0;
  transform: translateY(20px);
}

.form-item:nth-child(1) { animation-delay: 0.2s; }
.form-item:nth-child(2) { animation-delay: 0.3s; }
.form-item:nth-child(3) { animation-delay: 0.4s; }

@keyframes formItemReveal {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Input Wrapper */
.input-wrapper {
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

:deep(.el-form-item__label) {
  color: rgba(255, 255, 255, 0.7);
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.02em;
}

:deep(.el-icon) {
  color: rgba(255, 255, 255, 0.5);
}

/* Login Button */
.login-btn {
  position: relative;
  width: 100%;
  height: 52px;
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
}

.login-btn:hover {
  transform: translateY(-2px);
  box-shadow: 
    0 10px 30px rgba(99, 102, 241, 0.3),
    0 0 20px rgba(99, 102, 241, 0.2);
}

.login-btn:active {
  transform: translateY(0);
}

.login-btn.is-loading {
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

.login-btn:hover .btn-shine {
  left: 100%;
}

.btn-arrow {
  position: absolute;
  right: 20px;
  top: 50%;
  transform: translateY(-50%) translateX(-5px);
  opacity: 0;
  transition: all 0.3s ease;
}

.login-btn:hover .btn-arrow {
  opacity: 1;
  transform: translateY(-50%) translateX(0);
}

/* Footer Links */
.footer-links {
  text-align: center;
  margin-top: 32px;
  color: rgba(255, 255, 255, 0.5);
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  animation: footerReveal 0.6s cubic-bezier(0.16, 1, 0.3, 1) 0.5s forwards;
  opacity: 0;
}

@keyframes footerReveal {
  to {
    opacity: 1;
  }
}

.link-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: none;
  color: #a5b4fc;
  font-size: 14px;
  font-weight: 500;
  cursor: none;
  transition: all 0.3s ease;
}

.link-btn:hover {
  color: #c7d2fe;
}

.link-btn svg {
  transition: transform 0.3s ease;
}

.link-btn:hover svg {
  transform: translateX(4px);
}

/* Responsive */
@media (max-width: 768px) {
  .login-container {
    margin: 20px;
    padding: 32px;
  }
  
  .title {
    font-size: 32px;
  }
  
  .cursor-dot,
  .cursor-ring {
    display: none;
  }
  
  .auth-login-page {
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
