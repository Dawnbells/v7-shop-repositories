<template>
  <div class="personal-center-page">
    <section class="personal-header">
      <div class="identity-mark">{{ initials }}</div>
      <div>
        <span>PERSONAL PREFERENCES</span>
        <h1>个人中心</h1>
        <p>管理订单统计使用的时区、默认目标币种和个人汇率。</p>
      </div>
    </section>

    <el-card class="settings-card" shadow="never">
      <template #header>
        <div class="card-heading">
          <div>
            <h2>报表汇率</h2>
            <p>长期保存在个人配置中；统计查询时仍可临时覆盖。</p>
          </div>
          <el-button :loading="saving" type="primary" @click="save">保存设置</el-button>
        </div>
      </template>

      <el-form label-position="top">
        <div class="base-grid">
          <el-form-item label="报表时区">
            <el-select
              v-model="form.timeZoneId"
              allow-create
              filterable
              placeholder="输入 IANA 时区，例如 Asia/Shanghai"
            >
              <el-option
                v-for="timeZone in timeZoneOptions"
                :key="timeZone"
                :label="timeZone"
                :value="timeZone"
              />
            </el-select>
            <small>按天、按月边界均使用此时区，支持夏令时。</small>
          </el-form-item>

          <el-form-item label="默认目标币种">
            <el-select v-model="form.defaultTargetCurrencyCode" filterable>
              <el-option
                v-for="currency in currencies"
                :key="currency.code"
                :label="`${currency.code} · ${currency.name}`"
                :value="currency.code"
              />
            </el-select>
            <small>统计页首次打开时默认使用该币种。</small>
          </el-form-item>
        </div>

        <div class="rate-intro">
          <div>
            <strong>汇率定义</strong>
            <p>填写“1 美元可兑换多少该币种”，USD 固定为 1。</p>
          </div>
          <div class="conversion-example">
            <span>换算示例</span>
            <strong>{{ conversionExample }}</strong>
          </div>
        </div>

        <div class="rate-table">
          <div class="rate-row rate-head">
            <span>币种</span>
            <span>名称</span>
            <span>1 USD =</span>
            <span>状态</span>
          </div>
          <div v-for="currency in currencies" :key="currency.code" class="rate-row">
            <strong>{{ currency.code }}</strong>
            <span>{{ currency.name }}</span>
            <el-input
              v-model="form.exchangeRates[currency.code]"
              :disabled="currency.code === 'USD'"
              placeholder="未自定义"
            >
              <template #append>{{ currency.code }}</template>
            </el-input>
            <el-tag
              :type="form.exchangeRates[currency.code] ? 'success' : 'info'"
              effect="plain"
            >
              {{ form.exchangeRates[currency.code] ? '个人汇率' : '使用系统汇率' }}
            </el-tag>
          </div>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script lang="ts" setup>
import { ElMessage } from 'element-plus'
import type {
  CurrencyOption,
  StatisticsConfig,
} from '/@/api/orderStatistics'
import {
  getStatisticsConfig,
  getStatisticsCurrencies,
  saveStatisticsConfig,
} from '/@/api/orderStatistics'
import { useUserStore } from '/@/store/modules/user'

defineOptions({ name: 'PersonalCenter' })

const userStore = useUserStore()
const browserTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai'
const currencies = ref<CurrencyOption[]>([])
const saving = ref(false)
const form = reactive<StatisticsConfig>({
  defaultTargetCurrencyCode: 'USD',
  timeZoneId: browserTimeZone,
  exchangeRates: { USD: '1' },
})

const timeZoneOptions = [
  browserTimeZone,
  'Asia/Shanghai',
  'Asia/Tokyo',
  'Asia/Singapore',
  'Europe/London',
  'Europe/Berlin',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
].filter((value, index, values) => values.indexOf(value) === index)

const initials = computed(() => (userStore.username || 'U').slice(0, 2).toUpperCase())
const conversionExample = computed(() => {
  const code = form.defaultTargetCurrencyCode
  const rate =
    form.exchangeRates[code] ||
    currencies.value.find((currency) => currency.code === code)?.exchangeRate
  return rate ? `100 USD = ${formatDecimal(Number(rate) * 100)} ${code}` : `等待设置 ${code} 汇率`
})

const formatDecimal = (value: number) =>
  new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 8 }).format(value)

const responseData = <T,>(response: any): T => response?.data as T
const responseList = <T,>(response: any): T[] =>
  (response?.data?.list || response?.data || []) as T[]

const load = async () => {
  const [configResponse, currencyResponse] = await Promise.all([
    getStatisticsConfig(browserTimeZone),
    getStatisticsCurrencies(),
  ])
  currencies.value = responseList<CurrencyOption>(currencyResponse)
  Object.assign(form, responseData<StatisticsConfig>(configResponse))
  form.exchangeRates = { ...form.exchangeRates, USD: '1' }
  currencies.value.forEach((currency) => {
    if (!(currency.code in form.exchangeRates)) form.exchangeRates[currency.code] = ''
  })
}

const validate = () => {
  try {
    new Intl.DateTimeFormat('zh-CN', { timeZone: form.timeZoneId }).format()
  } catch {
    return '请输入有效的 IANA 时区'
  }
  if (!/^[A-Z]{3}$/.test(form.defaultTargetCurrencyCode)) return '默认币种格式不正确'
  for (const [code, rate] of Object.entries(form.exchangeRates)) {
    if (code === 'USD' || !rate) continue
    if (!/^\d+(\.\d{1,8})?$/.test(rate) || Number(rate) <= 0)
      return `${code} 汇率必须大于 0，且最多保留 8 位小数`
  }
  return ''
}

const save = async () => {
  const error = validate()
  if (error) {
    ElMessage.warning(error)
    return
  }
  saving.value = true
  try {
    const rates = Object.fromEntries(
      Object.entries(form.exchangeRates).filter(
        ([code, rate]) => code === 'USD' || Boolean(rate)
      )
    )
    const response = await saveStatisticsConfig({
      ...form,
      exchangeRates: rates,
    })
    Object.assign(form, responseData<StatisticsConfig>(response))
    ElMessage.success('报表设置已保存')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  load().catch(() => ElMessage.error('个人报表设置加载失败'))
})
</script>

<style lang="scss" scoped>
.personal-center-page {
  display: grid;
  gap: 18px;
  max-width: 1180px;
  margin: 0 auto;
}

.personal-header {
  display: flex;
  gap: 20px;
  align-items: center;
  padding: 26px 28px;
  color: #eef4f8;
  background: #20364a;
  border-radius: 14px;
}

.identity-mark {
  display: grid;
  width: 64px;
  height: 64px;
  font-family: 'DIN Alternate', sans-serif;
  font-size: 22px;
  place-items: center;
  background: #c58b37;
  border-radius: 8px;
}

.personal-header span {
  font-family: 'Courier New', monospace;
  font-size: 10px;
  letter-spacing: 0.18em;
  opacity: 0.58;
}

.personal-header h1 {
  margin: 5px 0 4px;
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  font-size: 27px;
}

.personal-header p,
.card-heading p,
.rate-intro p {
  margin: 0;
  color: #667085;
}

.personal-header p {
  color: rgba(238, 244, 248, 0.7);
}

.settings-card {
  border-color: #e2e8ee;
  border-radius: 12px;
}

.card-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-heading h2 {
  margin: 0 0 5px;
}

.base-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(260px, 1fr));
  gap: 24px;
}

.base-grid :deep(.el-select) {
  width: 100%;
}

.base-grid small {
  display: block;
  margin-top: 7px;
  color: #98a2b3;
}

.rate-intro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  margin: 10px 0 14px;
  background: #f5f8fa;
  border-left: 3px solid #315f8c;
}

.conversion-example {
  display: grid;
  gap: 4px;
  text-align: right;
}

.conversion-example span {
  font-size: 11px;
  color: #98a2b3;
}

.conversion-example strong {
  color: #8b5d18;
}

.rate-table {
  overflow: hidden;
  border: 1px solid #e4e9ef;
  border-radius: 10px;
}

.rate-row {
  display: grid;
  grid-template-columns: 90px minmax(150px, 1fr) minmax(240px, 1.2fr) 130px;
  gap: 18px;
  align-items: center;
  min-height: 64px;
  padding: 8px 18px;
  border-bottom: 1px solid #edf1f5;
}

.rate-row:last-child {
  border-bottom: 0;
}

.rate-head {
  min-height: 42px;
  font-size: 12px;
  font-weight: 700;
  color: #667085;
  background: #f8fafb;
}

@media (max-width: 760px) {
  .base-grid {
    grid-template-columns: 1fr;
  }

  .card-heading,
  .rate-intro {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
  }

  .conversion-example {
    text-align: left;
  }

  .rate-row {
    grid-template-columns: 68px 1fr;
  }

  .rate-head {
    display: none;
  }
}
</style>
