<template>
  <div class="auth-shell">
    <!-- 左侧品牌区：大留白 + 一句产品主张，小屏时隐藏 -->
    <section class="auth-aside" aria-hidden="false">
      <div class="aside-top">
        <BrandMark />
      </div>

      <div class="aside-copy">
        <h1 class="aside-title">{{ heading }}</h1>
        <p class="aside-desc">{{ description }}</p>
      </div>

      <ul class="aside-points">
        <li v-for="point in points" :key="point.title" class="aside-point">
          <span class="point-dot" aria-hidden="true"></span>
          <span class="point-text">
            <strong>{{ point.title }}</strong>
            <span>{{ point.desc }}</span>
          </span>
        </li>
      </ul>
    </section>

    <!-- 右侧表单区 -->
    <section class="auth-main">
      <div class="auth-card">
        <div class="auth-card-head">
          <BrandMark class="auth-brand-mobile" />
          <h2 class="auth-title">{{ title }}</h2>
          <p class="auth-subtitle">{{ subtitle }}</p>
        </div>

        <slot />
      </div>

      <p class="auth-foot">
        <slot name="foot" />
      </p>
    </section>
  </div>
</template>

<script setup lang="ts">
import BrandMark from '@/components/BrandMark.vue'

// 认证类页面(秘钥访问/账号登录/注册)共用外壳：
// 左侧固定品牌叙事区，右侧自适应表单卡片，小屏合并为单列
withDefaults(defineProps<{
  /** 左侧主标题 */
  heading: string
  /** 左侧说明文案 */
  description: string
  /** 左侧卖点(2~3 条) */
  points?: Array<{ title: string; desc: string }>
  /** 右侧表单标题 */
  title: string
  /** 右侧表单副标题 */
  subtitle?: string
}>(), {
  points: () => [],
  subtitle: ''
})
</script>

<style scoped>
.auth-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(360px, 1fr) minmax(420px, 480px);
  background: var(--bg);
}

/* ---------- 左侧品牌区 ---------- */
.auth-aside {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: var(--space-7) var(--space-8);
  background:
    radial-gradient(120% 90% at 12% 8%, var(--primary-soft) 0%, transparent 55%),
    radial-gradient(90% 70% at 88% 92%, var(--secondary-soft) 0%, transparent 60%),
    var(--bg-subtle);
  border-right: 1px solid var(--border);
}

.aside-copy {
  max-width: 480px;
}

.aside-title {
  /* 中文标题按语义断行，不让"用了多久"被拆开 */
  font-size: clamp(26px, 2.6vw, 38px);
  line-height: 1.25;
  letter-spacing: -0.02em;
  margin-bottom: var(--space-4);
  word-break: keep-all;
  overflow-wrap: anywhere;
}

.aside-desc {
  font-size: var(--text-md);
  line-height: var(--leading-relaxed);
  color: var(--text-muted);
}

.aside-points {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.aside-point {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
  font-size: var(--text-sm);
  color: var(--text-muted);
}

.point-dot {
  width: 6px;
  height: 6px;
  margin-top: 8px;
  border-radius: var(--radius-pill);
  background: var(--primary);
  flex-shrink: 0;
}

.point-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.point-text strong {
  color: var(--text-secondary);
  font-weight: 600;
  font-size: var(--text-base);
}

/* ---------- 右侧表单区 ---------- */
.auth-main {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: var(--space-6) var(--space-5);
}

/* 卡片与页脚作为一组居中，避免页脚被推到屏幕底部 */
.auth-main > * {
  width: 100%;
  max-width: 400px;
  align-self: center;
}

.auth-card {
  margin-bottom: var(--space-5);
}

.auth-card-head {
  margin-bottom: var(--space-6);
}

.auth-brand-mobile {
  display: none;
  margin-bottom: var(--space-4);
}

.auth-title {
  font-size: var(--text-xl);
  margin-bottom: var(--space-2);
}

.auth-subtitle {
  font-size: var(--text-sm);
  color: var(--text-muted);
  line-height: var(--leading-normal);
}

.auth-foot {
  font-size: var(--text-sm);
  color: var(--text-muted);
  text-align: center;
}

/* ---------- 响应式 ---------- */
@media (max-width: 900px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }

  .auth-aside {
    display: none;
  }

  .auth-brand-mobile {
    display: inline-flex;
  }

  .auth-main {
    min-height: 100vh;
    justify-content: center;
    padding: var(--space-5);
  }
}

@media (max-width: 480px) {
  .auth-main {
    padding: var(--space-4) var(--space-4) var(--space-6);
  }

  .auth-title {
    font-size: var(--text-lg);
  }
}
</style>