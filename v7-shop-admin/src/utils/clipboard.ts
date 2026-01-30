import { gp } from '/@vab/plugins/vab'

const clipboardSuccess = (text: any) => {
  gp.$baseMessage(`拷贝${text}成功`, 'success', 'hey')
}

const clipboardError = (text: any) => {
  gp.$baseMessage(`拷贝${text}失败`, 'error', 'hey')
}

/**
 * @description 复制数据
 * @param text
 */
const handleClipboard = (text: string) => {
  const { isSupported, copy } = useClipboard({ legacy: true })
  if (!isSupported) usePermission('clipboard-write')

  copy(text)
    .then(() => {
      clipboardSuccess(text)
    })
    .catch(() => {
      clipboardError(text)
    })
}

export default handleClipboard
