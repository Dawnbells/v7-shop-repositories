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

export function uploadFiles(folderId?: any) {
  return (options: any) => {
    console.log(options)
    request({
      url: `/multimedia-file/uploadFiles${folderId ? '' : `/${folderId}`}`,
      method: 'post',
    })
  }
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
