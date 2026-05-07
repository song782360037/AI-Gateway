<template>
  <div class="landing-page">
    <!-- 顶部导航栏 -->
    <header class="landing-nav">
      <div class="landing-nav__inner">
        <div class="landing-brand">
          <div class="landing-brand__mark">
            <!-- AI Gateway 品牌图标：统一入口 → 棱镜网关 → 多路模型分发 -->
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
              <!-- 统一输入（单条粗线代表标准接口入口） -->
              <path
                d="M2 12H7"
                stroke="white"
                stroke-width="3"
                stroke-linecap="round"
                opacity="0.9"
              />
              <!-- 棱镜网关主体（半透明填充 + 粗描边） -->
              <path
                d="M7 3L19 12L7 21V3Z"
                fill="white"
                fill-opacity="0.4"
                stroke="white"
                stroke-width="2.5"
                stroke-linejoin="round"
              />
              <!-- 多路分发到不同 AI 模型提供商 -->
              <path
                d="M19 12L22 7"
                stroke="white"
                stroke-width="2"
                stroke-linecap="round"
                opacity="0.95"
              />
              <path
                d="M19 12L22 12"
                stroke="white"
                stroke-width="2"
                stroke-linecap="round"
                opacity="0.65"
              />
              <path
                d="M19 12L22 17"
                stroke="white"
                stroke-width="2"
                stroke-linecap="round"
                opacity="0.95"
              />
            </svg>
          </div>
          <span class="landing-brand__text">AI Gateway</span>
        </div>
        <div class="landing-nav__actions">
          <router-link
            v-if="authStore.isAuthenticated"
            to="/dashboard"
            class="el-button el-button--primary"
          >
            进入控制台
          </router-link>
          <router-link v-else to="/login" class="el-button el-button--primary"> 登录 </router-link>
        </div>
      </div>
    </header>

    <!-- Hero 区域 -->
    <section class="landing-hero">
      <div class="landing-hero__bg" />
      <div class="landing-hero__content">
        <h1 class="landing-hero__title">
          统一接入 AI 模型<br />
          <span class="landing-hero__gradient">让每一次调用更高效</span>
        </h1>
        <p class="landing-hero__desc">
          AI Gateway 提供多模型提供商聚合、智能路由、负载均衡与统一鉴权，<br class="hide-mobile" />
          帮助开发团队以一套标准接口，无缝调用全球主流大模型。
        </p>
        <div class="landing-hero__actions">
          <router-link
            v-if="authStore.isAuthenticated"
            to="/dashboard"
            class="el-button el-button--primary el-button--large landing-hero__btn--primary"
          >
            进入控制台
          </router-link>
          <router-link
            v-else
            to="/login"
            class="el-button el-button--primary el-button--large landing-hero__btn--primary"
          >
            立即开始使用
          </router-link>
        </div>
      </div>
    </section>

    <!-- 核心特性 -->
    <section class="landing-section">
      <div class="landing-section__header">
        <h2 class="landing-section__title">核心能力</h2>
        <p class="landing-section__subtitle">为 AI 应用提供稳定、可控、可观测的模型接入层</p>
      </div>
      <div class="feature-grid">
        <div v-for="f in features" :key="f.title" class="feature-card">
          <div class="feature-card__icon" :style="{ background: f.iconBg, color: f.iconColor }">
            <el-icon :size="22">
              <component :is="f.icon" />
            </el-icon>
          </div>
          <h3 class="feature-card__title">{{ f.title }}</h3>
          <p class="feature-card__desc">{{ f.desc }}</p>
        </div>
      </div>
    </section>

    <!-- 页脚 -->
    <footer class="landing-footer">
      <div class="landing-footer__inner">
        <span>AI Gateway</span>
        <span class="landing-footer__sep">·</span>
        <span>统一 AI 模型接入网关</span>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import {
  Connection,
  DataAnalysis,
  Key,
  Lightning,
  MagicStick,
  Odometer,
} from '@element-plus/icons-vue'
import { useAuthStore } from '../../stores/auth'

const authStore = useAuthStore()

const features = [
  {
    title: '多提供商聚合',
    desc: '一键接入 OpenAI、Anthropic、阿里云、百度等主流模型提供商，统一接口标准。',
    icon: Connection,
    iconBg: 'rgba(67, 97, 238, 0.08)',
    iconColor: '#4361ee',
  },
  {
    title: '智能路由',
    desc: '支持按模型名称自动路由，灵活配置候选通道与优先级，实现故障自动切换。',
    icon: MagicStick,
    iconBg: 'rgba(139, 92, 246, 0.08)',
    iconColor: '#8b5cf6',
  },
  {
    title: '负载均衡',
    desc: '基于权重与轮询策略分配请求流量，避免单点过载，提升整体吞吐量。',
    icon: Odometer,
    iconBg: 'rgba(16, 185, 129, 0.08)',
    iconColor: '#10b981',
  },
  {
    title: '统一鉴权',
    desc: '通过 API Key 进行统一鉴权与限速，支持过期时间、额度限制与状态管理。',
    icon: Key,
    iconBg: 'rgba(6, 182, 212, 0.08)',
    iconColor: '#06b6d4',
  },
  {
    title: '请求监控',
    desc: '实时记录每次请求的模型、通道、耗时与费用，提供多维度的数据仪表盘。',
    icon: DataAnalysis,
    iconBg: 'rgba(245, 158, 11, 0.08)',
    iconColor: '#f59e0b',
  },
  {
    title: '缓存优化',
    desc: '支持 Prompt 缓存与 Token 命中统计，有效降低重复请求成本与延迟。',
    icon: Lightning,
    iconBg: 'rgba(239, 68, 68, 0.08)',
    iconColor: '#ef4444',
  },
]
</script>

<style scoped>
/* ==================== 页面容器 ==================== */
.landing-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #ffffff;
}

/* ==================== 导航栏 ==================== */
.landing-nav {
  position: sticky;
  top: 0;
  z-index: 50;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(226, 232, 240, 0.6);
}

.landing-nav__inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.landing-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.landing-brand__mark {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #4361ee 0%, #06b6d4 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 2px 6px -1px rgba(67, 97, 238, 0.35);
}

.landing-brand__text {
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
  letter-spacing: 0.01em;
}

/* ==================== Hero 区域 ==================== */
.landing-hero {
  position: relative;
  padding: 100px 24px 120px;
  text-align: center;
  overflow: hidden;
  background: linear-gradient(180deg, #f8fafc 0%, #ffffff 100%);
}

.landing-hero__bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(ellipse 60% 50% at 50% 0%, rgba(67, 97, 238, 0.08) 0%, transparent 70%),
    radial-gradient(ellipse 40% 40% at 80% 80%, rgba(6, 182, 212, 0.06) 0%, transparent 60%);
}

.landing-hero__content {
  position: relative;
  z-index: 1;
  max-width: 800px;
  margin: 0 auto;
}

.landing-hero__title {
  font-size: 42px;
  font-weight: 800;
  color: #0f172a;
  line-height: 1.2;
  margin: 0 0 20px;
  letter-spacing: -0.02em;
}

.landing-hero__gradient {
  background: linear-gradient(90deg, #4361ee, #06b6d4);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.landing-hero__desc {
  font-size: 16px;
  color: #475569;
  line-height: 1.7;
  margin: 0 auto 36px;
  max-width: 600px;
}

.landing-hero__actions {
  display: flex;
  justify-content: center;
  gap: 16px;
}

.landing-hero__btn--primary {
  padding: 14px 32px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 10px;
  height: auto;
}

/* ==================== 特性区域 ==================== */
.landing-section {
  padding: 80px 24px;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}

.landing-section__header {
  text-align: center;
  margin-bottom: 56px;
}

.landing-section__title {
  font-size: 28px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 10px;
}

.landing-section__subtitle {
  font-size: 15px;
  color: #64748b;
  margin: 0;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

@media (max-width: 1024px) {
  .feature-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .feature-grid {
    grid-template-columns: 1fr;
  }

  .landing-hero__title {
    font-size: 28px;
  }

  .hide-mobile {
    display: none;
  }
}

/* 特性卡片 */
.feature-card {
  padding: 28px;
  border-radius: 16px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  transition: all 0.25s ease;
}

.feature-card:hover {
  border-color: #cbd5e1;
  box-shadow: 0 8px 24px -6px rgba(15, 23, 42, 0.08);
  transform: translateY(-3px);
}

.feature-card__icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 18px;
}

.feature-card__title {
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
  margin: 0 0 8px;
}

.feature-card__desc {
  font-size: 14px;
  color: #64748b;
  line-height: 1.6;
  margin: 0;
}

/* ==================== 页脚 ==================== */
.landing-footer {
  margin-top: auto;
  padding: 32px 24px;
  border-top: 1px solid #e2e8f0;
  text-align: center;
}

.landing-footer__inner {
  font-size: 13px;
  color: #94a3b8;
}

.landing-footer__sep {
  margin: 0 8px;
}
</style>
