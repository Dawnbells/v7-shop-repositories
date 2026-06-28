import { onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import utc from 'dayjs/plugin/utc'
import timezone from 'dayjs/plugin/timezone'
import { getStatisticsConfig } from '/@/api/orderStatistics'

dayjs.extend(utc)
dayjs.extend(timezone)

/**
 * 订单查询时区对齐：统一按【个人中心配置的报表时区】解释所选的下单时间，
 * 与统计分析口径一致，不再跟随浏览器时区。未取到配置时回退浏览器时区（行为同改前）。
 *
 * 用法：在订单查询页 setup 中调用，发请求前用 toReportZoneRange 转换 dateRange，
 * 并把 reportTimeZone 传给筛选组件展示时区标签。
 */
export function useReportTimeZone() {
  const browserTimeZone =
    Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai'
  const reportTimeZone = ref(browserTimeZone)

  onMounted(async () => {
    try {
      const res: any = await getStatisticsConfig(browserTimeZone)
      if (res?.data?.timeZoneId) reportTimeZone.value = res.data.timeZoneId
    } catch {
      // 忽略：保持浏览器时区回退
    }
  })

  /**
   * 把选择器里（按浏览器本地显示的）墙钟时间，重新按配置时区解释，得到正确的瞬时点。
   * 非法/缺失区间原样返回。
   */
  const toReportZoneRange = (range: any) => {
    if (
      !Array.isArray(range) ||
      range.length !== 2 ||
      !range.every((item) => item instanceof Date)
    ) {
      return range
    }
    return range.map((item) =>
      dayjs.tz(dayjs(item).format('YYYY-MM-DD HH:mm:ss'), reportTimeZone.value).toDate()
    )
  }

  return { browserTimeZone, reportTimeZone, toReportZoneRange }
}
