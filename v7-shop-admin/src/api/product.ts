import { useUserStore } from '/@/store/modules/user'
import { getEnv } from '/@/utils/env'
import request from '/@/utils/request'

export function page(data: any) {
  return request({
    url: '/product/page',
    method: 'post',
    data,
  })
}

export function doEdit(data: any) {
  console.log('data', data)
  return request({
    url: '/product/doEdit',
    method: 'post',
    data,
  })
}

export function doDelete(data: any) {
  return request({
    url: '/product/doDelete',
    method: 'post',
    data,
  })
}
export function switchValidity(data: any) {
  return request({
    url: '/product/switchValidity',
    method: 'post',
    data,
  })
}

export function getRemoteQueryMerchandise(query: string) {
  return request({
    url: '/product/remoteQueryMerchandise?',
    method: 'get',
    params: { query },
  })
}

export type BatchEditMerchandiseScope = 'SELECTED' | 'OWNED_ALL'
export type BatchEditMerchandiseOperation = 'ADD' | 'REMOVE'
export type BatchEditMerchandiseEmptyResultPolicy = 'SKIP' | 'KEEP_EMPTY'

export interface BatchEditMerchandiseRequest {
  scope: BatchEditMerchandiseScope
  spuIds?: Array<string | number>
  operation: BatchEditMerchandiseOperation
  originalMerchandise: string
  field: string
  delimiter: string
  emptyResultPolicy: BatchEditMerchandiseEmptyResultPolicy
}

export interface BatchEditMerchandiseResult {
  targetSpuCount: number
  targetProductCount: number
  matchedProductCount: number
  originalMismatchCount: number
  updatedProductCount: number
  alreadyExistsCount: number
  notFoundCount: number
  emptySkippedCount: number
  emptiedProductCount: number
}

export function batchEditMerchandise(data: BatchEditMerchandiseRequest) {
  return request({
    url: '/product/batch-edit-merchandise',
    method: 'post',
    data,
  })
}

export function translateByAI(data: { productId: string; countryId: string; languageId: string; aiAccountId?: string }) {
  return request({
    url: '/product/translateByAI',
    method: 'post',
    data,
  })
}

export function aiTranslateImage(data: { multimediaFileId?: string; imageUrl?: string; imageDataBase64?: string; languageId: string; prompt?: string; aiAccountId: string }) {
  return request({
    url: '/product/ai-translate/image',
    method: 'post',
    data,
    timeout: 300000,
  })
}

export interface AiTranslateStreamOptions {
  onChunk: (text: string) => void
  onDone: () => void
  onError: (error: string) => void
}

export function aiTranslateTextStream(
  data: { text: string; languageId: string; prompt?: string; aiAccountId: string },
  options: AiTranslateStreamOptions
): AbortController {
  return doSseStream('/product/ai-translate/text-stream', data, options)
}

export function aiTranslateHtmlStream(
  data: { html: string; languageId: string; prompt?: string; aiAccountId: string },
  options: AiTranslateStreamOptions
): AbortController {
  return doSseStream('/product/ai-translate/html-stream', data, options)
}

function doSseStream(url: string, data: any, options: AiTranslateStreamOptions): AbortController {
  const controller = new AbortController()
  const baseURL = getEnv('VITE_API_BASE_URL', window.location.origin)
  const userStore = useUserStore()
  const { token } = userStore

  fetch(`${baseURL}${url}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(data),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        const text = await response.text()
        options.onError(text || `HTTP ${response.status}`)
        return
      }
      const reader = response.body!.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const payload = line.slice(5)
            if (payload === '[DONE]') {
              options.onDone()
              return
            }
            options.onChunk(payload)
          } else if (line.startsWith('event:done')) {
            // next data line will be [DONE]
          } else if (line.startsWith('event:error')) {
            // next data line is error message
          }
        }
      }
      options.onDone()
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        options.onError(err.message || '网络错误')
      }
    })

  return controller
}
