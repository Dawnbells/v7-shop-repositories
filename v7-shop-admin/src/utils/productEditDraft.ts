export interface ProductEditDraftRecord {
  draftKey: string
  userKey: string
  title: string
  spuId: string | number | undefined
  productId: string | number | undefined
  mode: 'new' | 'edit' | 'copy'
  updatedAt: number
  payload: any
}

const DB_NAME = 'v7-shop-admin-drafts'
const STORE_NAME = 'product-edit-drafts'
const DB_VERSION = 1

const openDraftDb = (): Promise<IDBDatabase> => {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)

    request.onupgradeneeded = () => {
      const db = request.result
      const store = db.objectStoreNames.contains(STORE_NAME)
        ? request.transaction?.objectStore(STORE_NAME)
        : db.createObjectStore(STORE_NAME, { keyPath: 'draftKey' })

      if (store && !store.indexNames.contains('updatedAt')) {
        store.createIndex('updatedAt', 'updatedAt')
      }
      if (store && !store.indexNames.contains('userKey')) {
        store.createIndex('userKey', 'userKey')
      }
    }

    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

const withStore = async <T>(
  mode: IDBTransactionMode,
  callback: (store: IDBObjectStore) => IDBRequest<T> | void
): Promise<T | undefined> => {
  const db = await openDraftDb()
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(STORE_NAME, mode)
    const store = transaction.objectStore(STORE_NAME)
    const request = callback(store)
    let result: T | undefined

    if (request) {
      request.onsuccess = () => {
        result = request.result
      }
      request.onerror = () => reject(request.error)
    }

    transaction.oncomplete = () => {
      db.close()
      resolve(result)
    }
    transaction.onerror = () => {
      db.close()
      reject(transaction.error)
    }
    transaction.onabort = () => {
      db.close()
      reject(transaction.error)
    }
  })
}

export const getProductEditDraft = async (
  draftKey: string
): Promise<ProductEditDraftRecord | undefined> => {
  return withStore<ProductEditDraftRecord>('readonly', (store) => store.get(draftKey))
}

export const listProductEditDrafts = async (
  userKey: string
): Promise<ProductEditDraftRecord[]> => {
  const drafts = (await withStore<ProductEditDraftRecord[]>('readonly', (store) => store.getAll())) || []
  return drafts
    .filter((draft) => draft.userKey === userKey)
    .sort((a, b) => b.updatedAt - a.updatedAt)
}

export const saveProductEditDraft = async (
  draft: ProductEditDraftRecord,
  limit = 10
): Promise<void> => {
  await withStore('readwrite', (store) => {
    store.put(draft)
  })

  const drafts = await listProductEditDrafts(draft.userKey)
  const expiredDrafts = drafts.slice(limit)
  if (expiredDrafts.length === 0) {
    return
  }
  await withStore('readwrite', (store) => {
    expiredDrafts.forEach((item) => store.delete(item.draftKey))
  })
}

export const deleteProductEditDraft = async (draftKey: string): Promise<void> => {
  await withStore('readwrite', (store) => {
    store.delete(draftKey)
  })
}
