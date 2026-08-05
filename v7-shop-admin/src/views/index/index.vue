<template>
  <div class="index-container no-background-container">
    <el-row :gutter="20">
      <!-- 欢迎横幅 -->
      <el-col :span="24">
        <div class="welcome-banner">
          <div class="welcome-content">
            <div class="welcome-left">
              <h1 class="welcome-title">{{ greetingText }}，欢迎回来</h1>
              <p class="welcome-subtitle">{{ currentTime }}</p>
              <div class="welcome-stats">
                <div class="stat-item">
                  <span class="stat-label">今日订单</span>
                  <span class="stat-number">{{ stats.todayOrderCount }}</span>
                </div>
                <div class="stat-item">
                  <span class="stat-label">今日销售额</span>
                  <span class="stat-number success">${{ formatNumber(stats.todaySalesAmount) }}</span>
                </div>
                <div class="stat-item">
                  <span class="stat-label">今日AI消耗积分</span>
                  <span class="stat-number warning">{{ formatNumber(stats.todayAiCreditsUsed) }}</span>
                </div>
                <div class="stat-item">
                  <span class="stat-label">AI冻结积分</span>
                  <span class="stat-number">{{ formatNumber(stats.currentAiFrozenCredits) }}</span>
                </div>
                <div class="stat-item">
                  <span class="stat-label">剩余可用积分</span>
                  <span class="stat-number success">{{ formatNumber(availableCredits) }}</span>
                </div>
              </div>
            </div>
            <div class="welcome-right">
              <div class="time-display">
                <span class="time">{{ currentHour }}:{{ currentMinute }}</span>
                <span class="date">{{ currentDate }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 当月统计卡片 -->
      <el-col :lg="6" :md="12" :sm="12" :xl="6" :xs="24">
        <div class="stat-card blue">
          <div class="stat-icon">
            <el-icon :size="28"><ShoppingCart /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-title">本月订单数</span>
            <span class="stat-value">{{ stats.monthOrderCount }}</span>
          </div>
        </div>
      </el-col>

      <el-col :lg="6" :md="12" :sm="12" :xl="6" :xs="24">
        <div class="stat-card green">
          <div class="stat-icon">
            <el-icon :size="28"><TrendCharts /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-title">本月销售额</span>
            <span class="stat-value">${{ formatNumber(stats.monthSalesAmount) }}</span>
          </div>
        </div>
      </el-col>

      <el-col :lg="6" :md="12" :sm="12" :xl="6" :xs="24">
        <div class="stat-card orange">
          <div class="stat-icon">
            <el-icon :size="28"><Cpu /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-title">本月AI消耗积分</span>
            <span class="stat-value">{{ formatNumber(stats.monthAiCreditsUsed) }}</span>
          </div>
        </div>
      </el-col>

      <el-col :lg="6" :md="12" :sm="12" :xl="6" :xs="24">
        <div class="stat-card purple">
          <div class="stat-icon">
            <el-icon :size="28"><Coin /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-title">当月AI限额</span>
            <span class="stat-value">{{ formatNumber(stats.monthAiCreditsQuota) }}</span>
          </div>
        </div>
      </el-col>

      <!-- 快捷入口 -->
      <el-col :lg="16" :md="24" :sm="24" :xl="16" :xs="24">
        <el-card class="section-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span class="header-title">
                <el-icon><Grid /></el-icon>
                快捷入口
              </span>
            </div>
          </template>
          <div class="quick-links-grid">
            <div
              v-for="item in quickLinks"
              :key="item.name"
              class="quick-link-item"
              :style="{ '--link-color': item.color, '--link-bg': item.bgColor }"
              @click="navigateTo(item.path)"
            >
              <div class="link-icon">
                <el-icon :size="24">
                  <component :is="item.icon" />
                </el-icon>
              </div>
              <div class="link-info">
                <span class="link-name">{{ item.name }}</span>
                <span class="link-desc">{{ item.desc }}</span>
              </div>
              <el-icon class="link-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 系统信息 -->
      <el-col :lg="8" :md="24" :sm="24" :xl="8" :xs="24">
        <el-card class="section-card system-info-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span class="header-title">
                <el-icon><InfoFilled /></el-icon>
                系统信息
              </span>
              <el-tooltip
                v-if="healthNodes.length"
                effect="dark"
                placement="bottom-end"
                popper-class="node-health-popper"
              >
                <template #content>
                  <div class="tip-title">{{ healthTipTitle }}</div>
                  <div v-for="node in healthNodes" :key="node.role" class="tip-row">
                    <span class="tip-dot" :class="node.tone" />
                    <span class="tip-role">{{ node.roleLabel }}</span>
                    <span class="tip-ip">{{ node.ip || '—' }}</span>
                    <span class="tip-status" :class="node.tone">{{ node.statusText }}</span>
                    <span v-if="node.active" class="tip-active">当前生效</span>
                  </div>
                </template>
                <div class="node-health">
                  <span class="node-health-label">节点</span>
                  <span v-for="node in healthNodes" :key="node.role" class="node-dot" :class="node.tone" />
                </div>
              </el-tooltip>
            </div>
          </template>
          <div class="system-info-list">
            <div class="info-item">
              <span class="info-label">系统名称</span>
              <span class="info-value">V7 Shop</span>
            </div>
            <div class="info-item">
              <span class="info-label">系统版本</span>
              <span class="info-value">
                <el-tag size="small" type="primary">v16.2.0</el-tag>
              </span>
            </div>
            <div class="info-item">
              <span class="info-label">Vue 版本</span>
              <span class="info-value">3.5.x</span>
            </div>
            <div class="info-item">
              <span class="info-label">Element Plus</span>
              <span class="info-value">2.12.x</span>
            </div>
            <div class="info-item">
              <span class="info-label">运行环境</span>
              <span class="info-value">
                <el-tag size="small" type="success">Production</el-tag>
              </span>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 最近订单 -->
      <el-col :lg="12" :md="24" :sm="24" :xl="12" :xs="24">
        <el-card class="section-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span class="header-title">
                <el-icon><List /></el-icon>
                最近订单
              </span>
              <el-button type="primary" link @click="navigateTo('/order/orderManager')">
                查看全部 <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
          </template>
          <div class="order-list">
            <div v-for="order in recentOrders" :key="order.id" class="order-item">
              <div class="order-info">
                <span class="order-id">{{ order.id }}</span>
                <span class="order-product">{{ order.product }}</span>
              </div>
              <div class="order-meta">
                <span class="order-amount">￥{{ order.amount }}</span>
                <el-tag :type="order.statusType" size="small">{{ order.status }}</el-tag>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 待办事项 -->
      <el-col :lg="12" :md="24" :sm="24" :xl="12" :xs="24">
        <el-card class="section-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span class="header-title">
                <el-icon><Clock /></el-icon>
                待办事项
              </span>
              <el-tag type="danger" size="small">{{ todoList.filter(t => !t.done).length }} 项待处理</el-tag>
            </div>
          </template>
          <div class="todo-list">
            <div v-for="todo in todoList" :key="todo.id" class="todo-item" :class="{ done: todo.done }">
              <el-checkbox v-model="todo.done" />
              <span class="todo-content">{{ todo.content }}</span>
              <el-tag :type="todo.priority === 'high' ? 'danger' : todo.priority === 'medium' ? 'warning' : 'info'" size="small">
                {{ todo.priority === 'high' ? '紧急' : todo.priority === 'medium' ? '一般' : '低' }}
              </el-tag>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script lang="ts" setup>
import {
  Goods,
  User,
  ShoppingCart,
  Document,
  TrendCharts,
  Coin,
  Grid,
  ArrowRight,
  InfoFilled,
  List,
  Clock,
  Setting,
  DataAnalysis,
  Cpu,
} from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import { getDashboardStats } from '/@/api/dashboard'
import { getFrontServerHealthStatus } from '/@/api/frontServer'

dayjs.locale('zh-cn')

defineOptions({
  name: 'Index',
})

const router = useRouter()

// 时间相关
const currentTime = ref(dayjs().format('YYYY年MM月DD日 dddd'))
const currentHour = ref(dayjs().format('HH'))
const currentMinute = ref(dayjs().format('mm'))
const currentDate = ref(dayjs().format('MM月DD日'))

// 问候语
const greetingText = computed(() => {
  const hour = dayjs().hour()
  if (hour < 6) return '夜深了'
  if (hour < 9) return '早上好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 17) return '下午好'
  if (hour < 19) return '傍晚好'
  return '晚上好'
})

// 更新时间
const updateTime = () => {
  currentHour.value = dayjs().format('HH')
  currentMinute.value = dayjs().format('mm')
}

// 统计数据
const stats = reactive({
  todayOrderCount: 0,
  todaySalesAmount: 0,
  todayAiCreditsUsed: 0,
  currentAiFrozenCredits: 0,
  monthOrderCount: 0,
  monthSalesAmount: 0,
  monthAiCreditsUsed: 0,
  monthAiCreditsQuota: 0,
})

const availableCredits = computed(() => {
  return Math.max(0, stats.monthAiCreditsQuota - stats.monthAiCreditsUsed - stats.currentAiFrozenCredits)
})

const fetchStats = async () => {
  try {
    const { data } = await getDashboardStats()
    stats.todayOrderCount = data.todayOrderCount ?? 0
    stats.todaySalesAmount = data.todaySalesAmount ?? 0
    stats.todayAiCreditsUsed = data.todayAiCreditsUsed ?? 0
    stats.currentAiFrozenCredits = data.currentAiFrozenCredits ?? 0
    stats.monthOrderCount = data.monthOrderCount ?? 0
    stats.monthSalesAmount = data.monthSalesAmount ?? 0
    stats.monthAiCreditsUsed = data.monthAiCreditsUsed ?? 0
    stats.monthAiCreditsQuota = data.monthAiCreditsQuota ?? 0
  } catch (e) {
    // silently ignore
  }
}

// 前端服务器主备兜底 IP 健康状态
type HealthTone = 'success' | 'danger' | 'info'
type HealthStatusCode = 'HEALTHY' | 'UNHEALTHY' | 'UNKNOWN'

interface HealthNode {
  role: string
  roleLabel: string
  ip: string | null
  status: HealthStatusCode
  configured: boolean
  active: boolean
}

interface HealthStatus {
  enabled: boolean
  updatedAt: string | null
  servers: { serverName: string; dnsIp: string | null; nodes: HealthNode[] }[]
}

// 后端没数据时（dev 未启用 / 一台服务器都没配）也要占满三个位，
// 否则「从左到右是主/备/兜底」的位置约定就不成立了
const PLACEHOLDER_NODES: HealthNode[] = [
  { role: 'PRIMARY', roleLabel: '主IP', ip: null, status: 'UNKNOWN', configured: false, active: false },
  { role: 'FAILOVER', roleLabel: '备用IP', ip: null, status: 'UNKNOWN', configured: false, active: false },
  { role: 'FALLBACK', roleLabel: '兜底IP', ip: null, status: 'UNKNOWN', configured: false, active: false },
]

const healthStatus = ref<HealthStatus | null>(null)

const healthServer = computed(() => healthStatus.value?.servers?.[0] ?? null)

const healthTipTitle = computed(() => {
  const status = healthStatus.value
  if (!status) return ''
  if (!status.enabled) return '健康检查未启用（dev 环境）'
  if (!status.updatedAt) return '等待首次探测…'
  if (!healthServer.value) return '未配置前端服务器'
  return healthServer.value.serverName
})

const resolveStatusText = (node: HealthNode, enabled: boolean) => {
  if (node.status === 'HEALTHY') return '健康'
  if (node.status === 'UNHEALTHY') return '故障'
  if (!enabled) return '未启用'
  // 同为灰点，但「没配这个 IP」和「刚启动还没探够 3 次」是两回事
  return node.configured ? '探测中' : '未配置'
}

// 无权限（接口返回 null）或请求失败时返回空数组，整块不渲染
const healthNodes = computed(() => {
  const status = healthStatus.value
  if (!status) return []
  const nodes = healthServer.value?.nodes ?? PLACEHOLDER_NODES
  return nodes.map((node) => ({
    ...node,
    tone: (node.status === 'HEALTHY' ? 'success' : node.status === 'UNHEALTHY' ? 'danger' : 'info') as HealthTone,
    statusText: resolveStatusText(node, status.enabled),
  }))
})

const fetchHealthStatus = async () => {
  try {
    const { data } = await getFrontServerHealthStatus()
    healthStatus.value = data ?? null
  } catch (e) {
    healthStatus.value = null
  }
}

const refreshAll = () => {
  fetchStats()
  fetchHealthStatus()
}

let pollTimer: ReturnType<typeof setInterval> | null = null
let timeTimer: ReturnType<typeof setInterval> | null = null

const startPolling = () => {
  if (pollTimer) return
  pollTimer = setInterval(refreshAll, 10000)
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const stopTimeTimer = () => {
  if (timeTimer) {
    clearInterval(timeTimer)
    timeTimer = null
  }
}

const handleVisibilityChange = () => {
  if (document.hidden) {
    stopPolling()
  } else {
    refreshAll()
    startPolling()
  }
}

const startAll = () => {
  updateTime()
  timeTimer = setInterval(updateTime, 1000)
  refreshAll()
  startPolling()
  document.addEventListener('visibilitychange', handleVisibilityChange)
}

const stopAll = () => {
  stopTimeTimer()
  stopPolling()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
}

onActivated(() => {
  startAll()
})

onDeactivated(() => {
  stopAll()
})

onBeforeUnmount(() => {
  stopAll()
})

// 格式化数字
const formatNumber = (num: number) => {
  return num.toLocaleString()
}

// 快捷入口
const quickLinks = [
  { name: '商品管理', desc: '管理商品信息', path: '/product/product', icon: Goods, color: '#409EFF', bgColor: 'rgba(64, 158, 255, 0.1)' },
  { name: '订单管理', desc: '处理订单业务', path: '/order/orderManager', icon: ShoppingCart, color: '#67C23A', bgColor: 'rgba(103, 194, 58, 0.1)' },
  { name: '用户管理', desc: '管理系统用户', path: '/system/employee', icon: User, color: '#E6A23C', bgColor: 'rgba(230, 162, 60, 0.1)' },
  { name: '域名管理', desc: '管理域名配置', path: '/ssl/topLevelDomain', icon: Document, color: '#F56C6C', bgColor: 'rgba(245, 108, 108, 0.1)' },
  { name: '数据分析', desc: '查看数据报表', path: '/index', icon: DataAnalysis, color: '#909399', bgColor: 'rgba(144, 147, 153, 0.1)' },
  { name: '系统设置', desc: '配置系统参数', path: '/system/company', icon: Setting, color: '#9B59B6', bgColor: 'rgba(155, 89, 182, 0.1)' },
]

// 最近订单
const recentOrders = ref([
  { id: 'ORD20260130001', product: 'iPhone 15 Pro Max', amount: '9,999', status: '已完成', statusType: 'success' as const },
  { id: 'ORD20260130002', product: 'MacBook Pro 14寸', amount: '15,999', status: '待发货', statusType: 'warning' as const },
  { id: 'ORD20260130003', product: 'AirPods Pro 2', amount: '1,899', status: '已发货', statusType: 'primary' as const },
  { id: 'ORD20260130004', product: 'iPad Air 5', amount: '4,799', status: '待付款', statusType: 'danger' as const },
])

// 待办事项
const todoList = ref([
  { id: 1, content: '审核新商品上架申请', done: false, priority: 'high' },
  { id: 2, content: '处理客户退款请求', done: false, priority: 'high' },
  { id: 3, content: '更新促销活动配置', done: true, priority: 'medium' },
  { id: 4, content: '检查库存预警商品', done: false, priority: 'medium' },
  { id: 5, content: '优化商品详情页面', done: false, priority: 'low' },
])

// 导航
const navigateTo = (path: string) => {
  router.push(path)
}
</script>

<style lang="scss" scoped>
.index-container {
  // 欢迎横幅
  .welcome-banner {
    position: relative;
    padding: 32px;
    margin-bottom: 20px;
    overflow: hidden;
    color: #fff;
    background: linear-gradient(135deg, #5b73b5 0%, #8b7db5 100%);
    border-radius: 16px;

    &::before {
      position: absolute;
      top: -50%;
      right: -20%;
      width: 400px;
      height: 400px;
      content: '';
      background: rgba(255, 255, 255, 0.1);
      border-radius: 50%;
    }

    &::after {
      position: absolute;
      bottom: -30%;
      left: 10%;
      width: 200px;
      height: 200px;
      content: '';
      background: rgba(255, 255, 255, 0.05);
      border-radius: 50%;
    }

    .welcome-content {
      position: relative;
      z-index: 1;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .welcome-left {
      .welcome-title {
        margin: 0 0 8px;
        font-size: 28px;
        font-weight: 600;
      }

      .welcome-subtitle {
        margin: 0 0 24px;
        font-size: 14px;
        opacity: 0.85;
      }

      .welcome-stats {
        display: flex;
        gap: 32px;

        .stat-item {
          display: flex;
          flex-direction: column;
          gap: 4px;

          .stat-label {
            font-size: 13px;
            opacity: 0.75;
          }

          .stat-number {
            font-size: 24px;
            font-weight: 600;

            &.warning {
              color: #ffd666;
            }

            &.success {
              color: #95de64;
            }
          }
        }
      }
    }

    .welcome-right {
      .time-display {
        text-align: right;

        .time {
          display: block;
          font-size: 48px;
          font-weight: 300;
          letter-spacing: 2px;
        }

        .date {
          font-size: 14px;
          opacity: 0.75;
        }
      }
    }
  }

  // 统计卡片
  .stat-card {
    display: flex;
    gap: 16px;
    align-items: center;
    padding: 20px;
    margin-bottom: 20px;
    cursor: pointer;
    background: #fafafa;
    border-radius: 12px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
    transition: all 0.3s ease;

    &:hover {
      box-shadow: 0 8px 20px rgba(0, 0, 0, 0.06);
      transform: translateY(-4px);
    }

    .stat-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 56px;
      height: 56px;
      color: #fff;
      border-radius: 12px;
    }

    .stat-info {
      display: flex;
      flex: 1;
      flex-direction: column;
      gap: 4px;

      .stat-title {
        font-size: 13px;
        color: #909399;
      }

      .stat-value {
        font-size: 22px;
        font-weight: 600;
        color: #4a4a4a;
      }

      .stat-change {
        display: flex;
        gap: 2px;
        align-items: center;
        font-size: 12px;

        &.positive {
          color: #67c23a;
        }

        &.negative {
          color: #f56c6c;
        }
      }
    }

    &.blue .stat-icon {
      background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
    }

    &.green .stat-icon {
      background: linear-gradient(135deg, #67c23a 0%, #95d475 100%);
    }

    &.orange .stat-icon {
      background: linear-gradient(135deg, #e6a23c 0%, #f5c78a 100%);
    }

    &.purple .stat-icon {
      background: linear-gradient(135deg, #9b59b6 0%, #c39bd3 100%);
    }
  }

  // 区块卡片
  .section-card {
    margin-bottom: 20px;
    border-radius: 12px;

    :deep(.el-card__header) {
      padding: 16px 20px;
      border-bottom: 1px solid #e8e8e8;
    }

    .card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      // 框架主题有 `html body .el-card__header{display:flex}`，本元素因此是 flex item，
      // 不加宽度就 shrink-to-fit，space-between 没有剩余空间可分，右侧内容贴不到右边
      width: 100%;

      .header-title {
        display: flex;
        gap: 8px;
        align-items: center;
        font-size: 16px;
        font-weight: 500;
        color: #4a4a4a;

        .el-icon {
          color: #5b73b5;
        }
      }
    }
  }

  // 快捷入口网格
  .quick-links-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;

    @media (max-width: 768px) {
      grid-template-columns: repeat(2, 1fr);
    }

    .quick-link-item {
      display: flex;
      gap: 12px;
      align-items: center;
      padding: 16px;
      cursor: pointer;
      background: var(--link-bg);
      border-radius: 10px;
      transition: all 0.3s ease;

      &:hover {
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
        transform: translateX(4px);

        .link-arrow {
          opacity: 1;
          transform: translateX(0);
        }
      }

      .link-icon {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 44px;
        height: 44px;
        color: var(--link-color);
        background: #fafafa;
        border-radius: 10px;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
      }

      .link-info {
        display: flex;
        flex: 1;
        flex-direction: column;
        gap: 2px;

        .link-name {
          font-size: 14px;
          font-weight: 500;
          color: #4a4a4a;
        }

        .link-desc {
          font-size: 12px;
          color: #909399;
        }
      }

      .link-arrow {
        color: #c0c4cc;
        opacity: 0;
        transform: translateX(-8px);
        transition: all 0.3s ease;
      }
    }
  }

  // 系统信息
  .system-info-card {
    // 主备兜底 IP 健康指示灯
    .node-health {
      display: flex;
      gap: 6px;
      align-items: center;
      cursor: default;

      .node-health-label {
        font-size: 12px;
        color: #909399;
      }

      .node-dot {
        flex-shrink: 0;
        width: 9px;
        height: 9px;
        background: #c0c4cc;
        border-radius: 50%;

        &.success {
          background: #67c23a;
        }

        &.danger {
          background: #f56c6c;
          animation: node-dot-alarm 1.6s ease-out infinite;
        }
      }
    }

    .system-info-list {
      .info-item {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 12px 0;
        border-bottom: 1px dashed #e8e8e8;

        &:last-child {
          border-bottom: none;
        }

        .info-label {
          font-size: 14px;
          color: #6b6b6b;
        }

        .info-value {
          font-size: 14px;
          font-weight: 500;
          color: #4a4a4a;
        }
      }
    }
  }

  // 订单列表
  .order-list {
    .order-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 14px 0;
      border-bottom: 1px solid #e8e8e8;

      &:last-child {
        border-bottom: none;
      }

      .order-info {
        display: flex;
        flex-direction: column;
        gap: 4px;

        .order-id {
          font-size: 13px;
          color: #909399;
        }

        .order-product {
          font-size: 14px;
          font-weight: 500;
          color: #4a4a4a;
        }
      }

      .order-meta {
        display: flex;
        gap: 12px;
        align-items: center;

        .order-amount {
          font-size: 15px;
          font-weight: 600;
          color: #f56c6c;
        }
      }
    }
  }

  // 待办列表
  .todo-list {
    .todo-item {
      display: flex;
      gap: 12px;
      align-items: center;
      padding: 12px 0;
      border-bottom: 1px solid #e8e8e8;

      &:last-child {
        border-bottom: none;
      }

      &.done {
        .todo-content {
          color: #c0c4cc;
          text-decoration: line-through;
        }
      }

      .todo-content {
        flex: 1;
        font-size: 14px;
        color: #4a4a4a;
      }
    }
  }
}

@keyframes node-dot-alarm {
  0% {
    box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.55);
  }

  70% {
    box-shadow: 0 0 0 6px rgba(245, 108, 108, 0);
  }

  100% {
    box-shadow: 0 0 0 0 rgba(245, 108, 108, 0);
  }
}
</style>

<style lang="scss">
// tooltip 内容被 teleport 到 body 上，scoped 样式选不到，只能走全局类名
.node-health-popper {
  .tip-title {
    margin-bottom: 6px;
    font-size: 12px;
    opacity: 0.7;
  }

  .tip-row {
    display: flex;
    gap: 8px;
    align-items: center;
    font-size: 12px;
    line-height: 20px;
  }

  .tip-dot {
    flex-shrink: 0;
    width: 7px;
    height: 7px;
    background: #c0c4cc;
    border-radius: 50%;

    &.success {
      background: #67c23a;
    }

    &.danger {
      background: #f56c6c;
    }
  }

  .tip-role {
    width: 42px;
  }

  .tip-ip {
    min-width: 100px;
    font-family: Consolas, Monaco, monospace;
    opacity: 0.85;
  }

  .tip-status {
    &.success {
      color: #95d475;
    }

    &.danger {
      color: #f89898;
    }
  }

  .tip-active {
    padding: 0 5px;
    font-size: 11px;
    background: rgba(255, 255, 255, 0.16);
    border-radius: 3px;
  }
}
</style>
