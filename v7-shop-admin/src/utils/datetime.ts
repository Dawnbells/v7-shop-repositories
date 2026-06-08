// Method to calculate days until a given target date
export const calculateDaysUntil = (dateString: string) => {
  const target = new Date(dateString)
  const today = new Date()

  // Calculate the time difference in milliseconds and convert to days
  const timeDiff = target.getTime() - today.getTime()
  const daysDiff = Math.ceil(timeDiff / (1000 * 60 * 60 * 24)) // convert to days

  return daysDiff
}

export const expiryDateFormat = (dateString: string) => {
  const days = calculateDaysUntil(dateString)
  if (!days) {
    return '未设置'
  }
  if (days < 0) {
    return `已过期${-days}天`
  }
  return `${days}天`
}

export const showCertbotInfo = (row: any) => {
  return row.certificateRequestStatus === 'ERROR'
}
export const certbotInfoStatus = (row: any) => {
  if (row.certificateRequestStatus === 'ERROR') {
    return '申请失败'
  }
  if (row.certificateRequestStatus === 'QUEUE') {
    return row.queuePosition ? `队列中（第${row.queuePosition}位）` : '队列中'
  }
  if (row.certificateRequestStatus === 'REQUESTING') {
    return '申请中'
  }
  return expiryDateFormat(row.sslExpiryDate)
}

export const certbotInfoType = (row: any) => {
  if (row.certificateRequestStatus === 'QUEUE') {
    return 'info'
  }
  if (row.certificateRequestStatus === 'REQUESTING') {
    return 'success'
  }
  if (row.certificateRequestStatus === 'ERROR') {
    return 'danger'
  }
  return expiryDateType(row.sslExpiryDate)
}
export const expiryDateType = (dateString: string) => {
  const days = calculateDaysUntil(dateString)
  if (!days) {
    return 'info'
  }
  if (days <= 0) {
    return 'danger'
  }
  if (days <= 7) {
    return 'warning'
  }
  if (days <= 30) {
    return 'primary'
  }
  return 'success'
}
