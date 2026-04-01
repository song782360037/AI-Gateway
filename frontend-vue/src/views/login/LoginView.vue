<template>
  <div class="login-page" @mousemove="onMouseMove" @mouseleave="onMouseLeave">
    <!-- 星链背景画布 -->
    <canvas ref="canvasRef" class="login-canvas" />

    <!-- 登录卡片 -->
    <div class="login-card">
      <!-- 品牌区 -->
      <div class="login-brand">
        <div class="brand-icon">
          <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
            <rect x="2" y="2" width="28" height="28" rx="8" stroke="currentColor" stroke-width="2" />
            <path d="M10 16L14 20L22 12" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </div>
        <h1 class="brand-title">AI Gateway</h1>
        <p class="brand-subtitle">管理控制台</p>
      </div>

      <!-- 表单区 -->
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        class="login-form"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            size="large"
            :prefix-icon="User"
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            size="large"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          class="login-btn"
          :loading="loading"
          @click="handleLogin"
        >
          {{ loading ? '登录中...' : '登录' }}
        </el-button>
      </el-form>

      <!-- 底部信息 -->
      <div class="login-footer">
        <span>AI Gateway &copy; {{ new Date().getFullYear() }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const canvasRef = ref<HTMLCanvasElement | null>(null)
let animFrameId = 0

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      await authStore.login({ username: form.username, password: form.password })
      ElMessage.success('登录成功')
      const redirect = (router.currentRoute.value.query.redirect as string) || '/dashboard'
      router.replace(redirect)
    } catch {
      // 错误信息已由 request 拦截器展示
    } finally {
      loading.value = false
    }
  })
}

// ==================== 星链交互背景 ====================

interface Star {
  x: number
  y: number
  vx: number
  vy: number
  baseRadius: number
  /** 闪烁相位，让每颗星呼吸节奏不同 */
  phase: number
  /** 当前亮度 [0,1] */
  brightness: number
}

/** 鼠标状态 */
const mouse = ref({ x: -9999, y: -9999, active: false })

function onMouseMove(e: MouseEvent) {
  mouse.value = { x: e.clientX, y: e.clientY, active: true }
}

function onMouseLeave() {
  mouse.value.active = false
}

function initCanvas() {
  const canvas = canvasRef.value
  if (!canvas) return

  const ctx = canvas.getContext('2d')
  if (!ctx) return

  let width = 0
  let height = 0
  let stars: Star[] = []

  // 配置参数
  const STAR_DENSITY = 0.00012          // 每像素星星数
  const MAX_LINK_DIST = 150             // 星星间最大连线距离
  const MOUSE_RADIUS = 200              // 鼠标影响半径
  const MOUSE_ATTRACT = 0.015           // 鼠标吸引力强度
  const MOUSE_REPEL_DIST = 80           // 鼠标排斥半径（太近时推开）
  const MOUSE_REPEL = 0.05              // 排斥力强度
  const FRICTION = 0.98                 // 速度衰减（模拟摩擦）
  const BASE_SPEED = 0.25               // 星星基础移动速度
  const PULSE_SPEED = 0.02              // 闪烁频率

  let time = 0

  // 颜色主题：蓝色 + 青色星链，偶尔暖色星点
  const COLORS = [
    { r: 67, g: 97, b: 238 },   // 主蓝
    { r: 105, g: 131, b: 242 },  // 亮蓝
    { r: 6, g: 182, b: 212 },    // 青
    { r: 140, g: 160, b: 242 },  // 淡紫蓝
    { r: 200, g: 160, b: 100 },  // 暖金（少量）
  ]

  function resize() {
    width = canvas!.width = window.innerWidth
    height = canvas!.height = window.innerHeight
    rebuildStars()
  }

  function rebuildStars() {
    // 根据屏幕面积自适应数量
    const count = Math.floor(width * height * STAR_DENSITY)
    const clamped = Math.max(40, Math.min(count, 200))
    stars = []
    for (let i = 0; i < clamped; i++) {
      stars.push(createStar())
    }
  }

  function createStar(): Star {
    return {
      x: Math.random() * width,
      y: Math.random() * height,
      vx: (Math.random() - 0.5) * BASE_SPEED * 2,
      vy: (Math.random() - 0.5) * BASE_SPEED * 2,
      baseRadius: 1 + Math.random() * 2,
      phase: Math.random() * Math.PI * 2,
      brightness: 0.4 + Math.random() * 0.6,
    }
  }

  function update() {
    time += 1

    for (const s of stars) {
      // 闪烁呼吸
      s.phase += PULSE_SPEED
      s.brightness = 0.3 + 0.7 * ((Math.sin(s.phase) + 1) / 2)

      // 鼠标交互：吸引 + 近距排斥
      if (mouse.value.active) {
        const dx = mouse.value.x - s.x
        const dy = mouse.value.y - s.y
        const dist = Math.sqrt(dx * dx + dy * dy)

        if (dist < MOUSE_RADIUS && dist > 0.1) {
          if (dist > MOUSE_REPEL_DIST) {
            // 吸引
            const force = MOUSE_ATTRACT * (1 - dist / MOUSE_RADIUS)
            s.vx += (dx / dist) * force
            s.vy += (dy / dist) * force
          } else {
            // 排斥（避免星点堆在鼠标中心）
            const force = MOUSE_REPEL * (1 - dist / MOUSE_REPEL_DIST)
            s.vx -= (dx / dist) * force
            s.vy -= (dy / dist) * force
          }
        }
      }

      // 摩擦衰减
      s.vx *= FRICTION
      s.vy *= FRICTION

      // 速度限制
      const speed = Math.sqrt(s.vx * s.vx + s.vy * s.vy)
      if (speed < BASE_SPEED * 0.3) {
        // 太慢时给一点随机推力，避免画面静止
        s.vx += (Math.random() - 0.5) * 0.05
        s.vy += (Math.random() - 0.5) * 0.05
      } else if (speed > 3) {
        s.vx = (s.vx / speed) * 3
        s.vy = (s.vy / speed) * 3
      }

      // 更新位置
      s.x += s.vx
      s.y += s.vy

      // 边界处理：软回弹，避免硬跳变
      if (s.x < -20) s.x = width + 20
      if (s.x > width + 20) s.x = -20
      if (s.y < -20) s.y = height + 20
      if (s.y > height + 20) s.y = -20
    }
  }

  function draw() {
    ctx!.clearRect(0, 0, width, height)

    // ---- 画星链连线 ----
    for (let i = 0; i < stars.length; i++) {
      for (let j = i + 1; j < stars.length; j++) {
        const dx = stars[i].x - stars[j].x
        const dy = stars[i].y - stars[j].y
        const dist = Math.sqrt(dx * dx + dy * dy)
        if (dist < MAX_LINK_DIST) {
          const alpha = (1 - dist / MAX_LINK_DIST) * 0.25
          // 连线亮度取两颗星中较亮的那个
          const b = Math.max(stars[i].brightness, stars[j].brightness)
          const c = pickLineColor(i)
          ctx!.strokeStyle = `rgba(${c.r},${c.g},${c.b},${alpha * b})`
          ctx!.lineWidth = (1 - dist / MAX_LINK_DIST) * 1.5
          ctx!.beginPath()
          ctx!.moveTo(stars[i].x, stars[i].y)
          ctx!.lineTo(stars[j].x, stars[j].y)
          ctx!.stroke()
        }
      }
    }

    // ---- 画鼠标与附近星星的连线 ----
    if (mouse.value.active) {
      for (const s of stars) {
        const dx = mouse.value.x - s.x
        const dy = mouse.value.y - s.y
        const dist = Math.sqrt(dx * dx + dy * dy)
        if (dist < MOUSE_RADIUS) {
          const alpha = (1 - dist / MOUSE_RADIUS) * 0.4
          ctx!.strokeStyle = `rgba(105,131,242,${alpha})`
          ctx!.lineWidth = (1 - dist / MOUSE_RADIUS) * 2
          ctx!.beginPath()
          ctx!.moveTo(mouse.value.x, mouse.value.y)
          ctx!.lineTo(s.x, s.y)
          ctx!.stroke()
        }
      }
    }

    // ---- 画星星 ----
    for (const s of stars) {
      const radius = s.baseRadius * s.brightness
      const c = starColor(s)

      // 外发光
      const glow = ctx!.createRadialGradient(s.x, s.y, 0, s.x, s.y, radius * 4)
      glow.addColorStop(0, `rgba(${c.r},${c.g},${c.b},${0.3 * s.brightness})`)
      glow.addColorStop(1, `rgba(${c.r},${c.g},${c.b},0)`)
      ctx!.fillStyle = glow
      ctx!.beginPath()
      ctx!.arc(s.x, s.y, radius * 4, 0, Math.PI * 2)
      ctx!.fill()

      // 星点核心
      ctx!.fillStyle = `rgba(${c.r},${c.g},${c.b},${s.brightness})`
      ctx!.beginPath()
      ctx!.arc(s.x, s.y, radius, 0, Math.PI * 2)
      ctx!.fill()
    }

    // ---- 鼠标光标光晕 ----
    if (mouse.value.active) {
      const cursorGlow = ctx!.createRadialGradient(
        mouse.value.x, mouse.value.y, 0,
        mouse.value.x, mouse.value.y, MOUSE_RADIUS * 0.5,
      )
      cursorGlow.addColorStop(0, 'rgba(67,97,238,0.06)')
      cursorGlow.addColorStop(0.5, 'rgba(67,97,238,0.02)')
      cursorGlow.addColorStop(1, 'rgba(67,97,238,0)')
      ctx!.fillStyle = cursorGlow
      ctx!.beginPath()
      ctx!.arc(mouse.value.x, mouse.value.y, MOUSE_RADIUS * 0.5, 0, Math.PI * 2)
      ctx!.fill()
    }

    update()
    animFrameId = requestAnimationFrame(draw)
  }

  // 基于索引选连线颜色（确定性，避免每帧随机闪烁）
  function pickLineColor(index: number) {
    return COLORS[index % 4]
  }

  // 基于相位选星星颜色（确定性）
  function starColor(s: Star) {
    const idx = Math.floor(s.phase / (Math.PI * 2) * COLORS.length) % COLORS.length
    return COLORS[idx]
  }

  resize()
  draw()
  window.addEventListener('resize', resize)
}

onMounted(() => {
  if (authStore.isAuthenticated) {
    router.replace('/dashboard')
    return
  }
  initCanvas()
})

onUnmounted(() => {
  cancelAnimationFrame(animFrameId)
})
</script>

<style scoped>
.login-page {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0a0b1a;
  overflow: hidden;
}

.login-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

/* 登录卡片 — 玻璃态 */
.login-card {
  position: relative;
  z-index: 1;
  width: 400px;
  padding: 40px 36px 32px;
  background: rgba(29, 30, 44, 0.85);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  box-shadow:
    0 32px 64px rgba(0, 0, 0, 0.4),
    0 0 0 1px rgba(255, 255, 255, 0.04) inset,
    0 1px 0 rgba(255, 255, 255, 0.06) inset;
}

/* 品牌区 */
.login-brand {
  text-align: center;
  margin-bottom: 32px;
}

.brand-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: linear-gradient(135deg, #4361ee 0%, #3451b2 100%);
  color: #fff;
  margin-bottom: 16px;
  box-shadow: 0 8px 24px rgba(67, 97, 238, 0.3);
}

.brand-title {
  font-size: 22px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 0.01em;
  margin: 0 0 4px;
}

.brand-subtitle {
  font-size: 13px;
  color: rgba(163, 166, 183, 0.8);
  margin: 0;
}

/* 表单 — Element Plus 暗色覆盖 */
.login-form :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  box-shadow: none !important;
  transition: border-color 0.2s, background 0.2s;
}

.login-form :deep(.el-input__wrapper:hover) {
  border-color: rgba(67, 97, 238, 0.4);
  background: rgba(255, 255, 255, 0.08);
}

.login-form :deep(.el-input__wrapper.is-focus) {
  border-color: #4361ee;
  background: rgba(255, 255, 255, 0.1);
}

.login-form :deep(.el-input__inner) {
  color: #e0e0e8;
}

.login-form :deep(.el-input__inner::placeholder) {
  color: rgba(163, 166, 183, 0.5);
  color: rgba(163, 166, 183, 0.6);
}

.login-form :deep(.el-form-item__error) {
  color: #f56c6c;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 8px;
  margin-top: 4px;
  background: linear-gradient(135deg, #4361ee, #3451b2);
  border: none;
  letter-spacing: 0.02em;
}

.login-btn:hover {
  background: linear-gradient(135deg, #5575f0, #3f5ec2);
}

/* 底部 */
.login-footer {
  text-align: center;
  margin-top: 24px;
  font-size: 12px;
  color: rgba(163, 166, 183, 0.4);
}

/* 响应式 */
@media (max-width: 480px) {
  .login-card {
    width: calc(100% - 32px);
    padding: 32px 24px 24px;
  }
}
</style>
