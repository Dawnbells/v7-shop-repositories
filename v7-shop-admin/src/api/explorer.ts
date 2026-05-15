import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/multimedia-file/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  return request({
    url: '/multimedia-file/doEdit',
    method: 'post',
    data,
  })
}

/**
 * 上传单个多媒体文件到指定文件夹
 * - 走全局 axios 实例，自动注入 token、走统一的业务码/错误处理
 * - 单独放宽 timeout，并支持外部取消与上传进度回调
 * @param folderId 目标文件夹 id，缺省落到 root
 * @param file     原始 File 对象
 * @param options  可选：onProgress / signal
 */
export function uploadFile(
  folderId: string | number | undefined,
  file: File,
  options: {
    onProgress?: (percent: number) => void
    signal?: AbortSignal
    timeout?: number
  } = {}
) {
  const formData = new FormData()
  formData.append('files', file, file.name)

  return request({
    url: `/multimedia-file/uploadFiles/${folderId || 'root'}`,
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    // 图片上传给足 2 分钟，避免命中全局 10s 默认超时
    timeout: options.timeout ?? 120_000,
    signal: options.signal,
    onUploadProgress: (e: any) => {
      if (!options.onProgress) return
      const total = e.total || (e as any).estimated || 0
      if (total > 0) {
        // 上传完最后一个字节但服务端还没回包之前，封顶 99%，避免进度条卡 100% 误判
        options.onProgress(Math.min(99, Math.round((e.loaded * 100) / total)))
      }
    },
  })
}

export function doDelete(data: any) {
  return request({
    url: '/multimedia-file/doDelete',
    method: 'post',
    data,
  })
}
export function switchValidity(data: any) {
  return request({
    url: '/multimedia-file/switchValidity',
    method: 'post',
    data,
  })
}

export function folderTree(query?: string) {
  return request({
    url: '/folder/tree',
    method: 'get',
    params: query ? { query } : undefined,
  })
}

export function mkdirFolderApi(data: any) {
  return request({
    url: '/folder/mkdir',
    method: 'post',
    data,
  })
}

export function renameFolderApi(data: any) {
  return request({
    url: '/folder/rename',
    method: 'post',
    data,
  })
}

export function deleteFolderApi(data: any) {
  return request({
    url: '/folder/delete',
    method: 'post',
    data,
  })
}
