<template>
  <vab-dialog v-model="dialogFormVisible" append-to-body :title="title" width="1000px" @close="close">
    <el-table
      ref="tableRef"
      border
      :data="routerList"
      row-key="id"
      style="width: 100%; margin-bottom: 20px"
      @expand-change="handleExpandChange"
    >
      <el-table-column label="名称" prop="meta.icon">
        <template #default="{ row }">
          <el-space wrap>
            <vab-icon :icon="row.meta.icon" />
            <el-text>{{ row.meta.title }}</el-text>
            <el-text v-if="row.platform === 'MALL_MANAGER'">【商城】</el-text>
          </el-space>
        </template>
      </el-table-column>
      <el-table-column align="center" label="路径" width="200px">
        <template #default="{ row }">
          <el-text v-if="row.path.startsWith('/')">{{ row.path }}</el-text>
          <el-text v-else>/{{ row.path }}</el-text>
        </template>
      </el-table-column>
      <el-table-column align="center" label="类型" prop="type" width="150px">
        <template #default="{ row }">
          <el-text v-if="row.type === 'MENU'">菜单</el-text>
          <el-text v-else-if="row.type === 'FEATURE'">功能</el-text>
          <el-text v-else-if="row.type === 'PERMISSION'">权限</el-text>
          <el-text v-else-if="row.type === 'DATA'">数据</el-text>
        </template>
      </el-table-column>
      <el-table-column align="center" label="状态" prop="status" width="150px">
        <template #default="{ row }">
          <el-switch
            v-model="row.status"
            active-value="VALID"
            inactive-value="INVALID"
            :loading="saveLoading"
            @change="($event) => handleSwitchValidity($event, row)"
          />
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button :loading="saveLoading" type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { grantRouters } from '/@/api/role'
defineOptions({
  name: 'RoleGrantPermission',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const tableRef = ref<any>(null)
const title = ref<string>('')
const routerList = ref<any>()
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(true)
const form = reactive<any>({
  name: '',
  description: '',
})

const showGrant = (row: any, routers: any) => {
  dialogFormVisible.value = true
  routerList.value = routers
  saveLoading.value = false
  // 折叠所有航
  deepRefreshChecked(routerList.value, row)
  handleExpandChange({ id: -1 }, true)
  nextTick(() => {
    if (!row) title.value = '权限分配'
    else {
      title.value = '权限分配'
      Object.assign(form, row)
    }
  })
}

const deepRefreshChecked = (routerList: { status: string; id: number; children: any }[], row: { systemRouterIds: number[] }) => {
  let router: { status: string; id: number; children: any }
  for (router of routerList) {
    router.status = row.systemRouterIds.includes(router.id) ? 'VALID' : 'INVALID'
    if (router.children && router.children.length > 0) {
      deepRefreshChecked(router.children, row)
    }
  }
}

defineExpose({
  showGrant,
})

const close = () => {
  Object.assign(form, {
    id: undefined,
  })
  emit('fetch-data')
}

const getAllCheckedRouter = (routers: any[]): any[] => {
  let router
  let result = []
  for (router of routers) {
    if (router.status == 'VALID') {
      result.push(router.id)
    }
    if (router.children && router.children.length > 0) {
      const childrenResult: any[] = getAllCheckedRouter(router.children)
      if (childrenResult.length > 0) {
        result = [...result, ...childrenResult]
      }
    }
  }
  return result
}
const save = async () => {
  try {
    saveLoading.value = true
    const checkedRouterIds = getAllCheckedRouter(routerList.value)
    const { msg }: any = await grantRouters({ id: form.id, routerIds: checkedRouterIds })
    await $baseMessage(msg, 'success', 'hey')
    dialogFormVisible.value = false
  } finally {
    saveLoading.value = false
  }
}

const handleSwitchValidity = (
  newVal: boolean | string | number,
  row: { id: number; status: string; statusLoading: boolean; parentId: number; children: any[] }
) => {
  row.statusLoading = true
  if (newVal == 'VALID') {
    deepSetParentStatus(routerList.value, row.parentId, 'VALID')
  }
  deepSetChildrenStatus(row.children, newVal == 'VALID' ? 'VALID' : 'INVALID')
  if (newVal == 'INVALID') {
    deepCheckAllChildrenInvalid(routerList.value)
  }
  row.statusLoading = false
}

const deepCheckAllChildrenInvalid = (routers: any[]) => {
  if (routers && routers.length > 0) {
    let router
    for (router of routers) {
      if (router.children && router.children.length > 0) {
        deepCheckAllChildrenInvalid(router.children)
        const anyChildrenValid = router.children.find((r: any) => r.status === 'VALID')
        if (!anyChildrenValid) {
          router.status = 'INVALID'
        }
      }
    }
  }
}
const deepSetChildrenStatus = (routers: any[], newVal: string) => {
  if (routers && routers.length > 0) {
    let router
    for (router of routers) {
      router.status = newVal
      deepSetChildrenStatus(router.children, newVal)
    }
  }
}
const deepSetParentStatus = (routers: any[], parentId: number, newVal: string) => {
  let router

  for (router of routers) {
    if (router.id === parentId) {
      router.status = newVal
      if (!router.parentId || router.parentId <= 0) {
        return
      }
      if (router.parentId) {
        deepSetParentStatus(routerList.value, router.parentId, newVal)
      }
      return
    }
    if (router.children && router.children.length > 0) {
      deepSetParentStatus(router.children, parentId, newVal)
    }
  }
}

const handleExpandChange = (row: any, expanded: any) => {
  nextTick(() => {
    if (!expanded || !tableRef.value || !tableRef.value.store || !tableRef.value.store.states || !tableRef.value.store.states.treeData) {
      return
    }
    // 折叠所有行
    const treeData = tableRef.value.store.states.treeData.value
    if (row.parentId) {
      return
    }
    for (const key in treeData) {
      if (treeData[key].expanded && key != row.id) {
        treeData[key].expanded = false
      }
    }
  })
}
</script>
