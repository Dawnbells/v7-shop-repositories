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
                  <span class="stat-label">AI消耗积分</span>
                  <span class="stat-number warning">{{ formatNumber(stats.todayAiCreditsUsed) }}</span>
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

      <!-- 数据统计卡片 -->
      <el-col :lg="6" :md="12" :sm="12" :xl="6" :xs="24">
        <div class="stat-card blue">
          <div class="stat-icon">
            <el-icon :size="28"><ShoppingCart /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-title">今日订单数</span>
            <span class="stat-value">{{ stats.todayOrderCount }}</span>
          </div>
        </div>
      </el-col>

      <el-col :lg="6" :md="12" :sm="12" :xl="6" :xs="24">
        <div class="stat-card green">
          <div class="stat-icon">
            <el-icon :size="28"><TrendCharts /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-title">今日销售额</span>
            <span class="stat-value">${{ formatNumber(stats.todaySalesAmount) }}</span>
          </div>
        </div>
      </el-col>

      <el-col :lg="6" :md="12" :sm="12" :xl="6" :xs="24">
        <div class="stat-card orange">
          <div class="stat-icon">
            <el-icon :size="28"><Cpu /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-title">今日AI消耗积分</span>
            <span class="stat-value">{{ formatNumber(stats.todayAiCreditsUsed) }}</span>
          </div>
        </div>
      </el-col>

      <el-col :lg="6" :md="12" :sm="12" :xl="6" :xs="24">
        <div class="stat-card purple">
          <div class="stat-icon">
            <el-icon :size="28"><Lock /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-title">当前AI冻结积分</span>
            <span class="stat-value">{{ formatNumber(stats.currentAiFrozenCredits) }}</span>
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
  Lock,
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
})

const fetchStats = async () => {
  try {
    const { data } = await getDashboardStats()
    stats.todayOrderCount = data.todayOrderCount ?? 0
    stats.todaySalesAmount = data.todaySalesAmount ?? 0
    stats.todayAiCreditsUsed = data.todayAiCreditsUsed ?? 0
    stats.currentAiFrozenCredits = data.currentAiFrozenCredits ?? 0
  } catch (e) {
    // silently ignore
  }
}

let pollTimer: ReturnType<typeof setInterval> | null = null

const startPolling = () => {
  if (pollTimer) return
  pollTimer = setInterval(fetchStats, 10000)
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const handleVisibilityChange = () => {
  if (document.hidden) {
    stopPolling()
  } else {
    fetchStats()
    startPolling()
  }
}

onMounted(() => {
  setInterval(updateTime, 1000)
  fetchStats()
  startPolling()
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onBeforeUnmount(() => {
  stopPolling()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
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
    background: linear-gradient(135deg, #5b73b5 0%, #8b7db5 100%);
    border-radius: 16px;
    padding: 32px;
    margin-bottom: 20px;
    color: #fff;
    position: relative;
    overflow: hidden;

    &::before {
      content: '';
      position: absolute;
      top: -50%;
      right: -20%;
      width: 400px;
      height: 400px;
      background: rgba(255, 255, 255, 0.1);
      border-radius: 50%;
    }

    &::after {
      content: '';
      position: absolute;
      bottom: -30%;
      left: 10%;
      width: 200px;
      height: 200px;
      background: rgba(255, 255, 255, 0.05);
      border-radius: 50%;
    }

    .welcome-content {
      display: flex;
      justify-content: space-between;
      align-items: center;
      position: relative;
      z-index: 1;
    }

    .welcome-left {
      .welcome-title {
        font-size: 28px;
        font-weight: 600;
        margin: 0 0 8px;
      }

      .welcome-subtitle {
        font-size: 14px;
        opacity: 0.85;
        margin: 0 0 24px;
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
    background: #fafafa;
    border-radius: 12px;
    padding: 20px;
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 20px;
    transition: all 0.3s ease;
    cursor: pointer;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);

    &:hover {
      transform: translateY(-4px);
      box-shadow: 0 8px 20px rgba(0, 0, 0, 0.06);
    }

    .stat-icon {
      width: 56px;
      height: 56px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #fff;
    }

    .stat-info {
      flex: 1;
      display: flex;
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
        font-size: 12px;
        display: flex;
        align-items: center;
        gap: 2px;

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

      .header-title {
        display: flex;
        align-items: center;
        gap: 8px;
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
      align-items: center;
      gap: 12px;
      padding: 16px;
      border-radius: 10px;
      background: var(--link-bg);
      cursor: pointer;
      transition: all 0.3s ease;

      &:hover {
        transform: translateX(4px);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);

        .link-arrow {
          opacity: 1;
          transform: translateX(0);
        }
      }

      .link-icon {
        width: 44px;
        height: 44px;
        border-radius: 10px;
        background: #fafafa;
        display: flex;
        align-items: center;
        justify-content: center;
        color: var(--link-color);
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
      }

      .link-info {
        flex: 1;
        display: flex;
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
          color: #4a4a4a;
          font-weight: 500;
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
          color: #4a4a4a;
          font-weight: 500;
        }
      }

      .order-meta {
        display: flex;
        align-items: center;
        gap: 12px;

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
      align-items: center;
      gap: 12px;
      padding: 12px 0;
      border-bottom: 1px solid #e8e8e8;

      &:last-child {
        border-bottom: none;
      }

      &.done {
        .todo-content {
          text-decoration: line-through;
          color: #c0c4cc;
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
</style>
