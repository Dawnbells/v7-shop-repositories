<template>
  <div class="employee-container auto-height-container">
    <vab-query-form>
      <vab-query-form-top-panel>
        <el-form inline label-width="49px" :model="queryForm" @submit.prevent>
          <el-form-item label="标题">
            <el-input v-model="queryForm.title" clearable placeholder="请输入标题" />
          </el-form-item>
          <el-form-item label="部门">
            <el-tree-select
              v-model="queryForm.departmentId"
              :data="allDepartmentTree"
              node-key="id"
              :props="defaultProps"
              check-strictly
              clearable
              placeholder="请选择部门"
            />
          </el-form-item>
          <el-form-item v-show="!fold" label="标题">
            <el-input v-model="queryForm.title" clearable placeholder="请输入标题" />
          </el-form-item>
          <el-form-item>
            <el-button
              :icon="Search"
              :loading="listLoading"
              native-type="submit"
              type="primary"
              @click="queryData"
            >
              查询
            </el-button>
            <el-button class="hidden-xs-only" text type="primary" @click="handleFold">
              <span v-if="fold">展开</span>
              <span v-else>合并</span>
              <vab-icon
                class="vab-dropdown"
                :class="{ 'vab-dropdown-active': fold }"
                icon="arrow-up-s-line"
              />
            </el-button>
          </el-form-item>
        </el-form>
      </vab-query-form-top-panel>
      <vab-query-form-left-panel :span="24">
        <el-button :icon="Plus" type="primary" @click="handleAdd">添加</el-button>
        <el-button :icon="Delete" type="danger" @click="handleDelete">删除</el-button>
      </vab-query-form-left-panel>
    </vab-query-form>

    <el-table
      ref="tableRef"
      v-loading="listLoading"
      border
      :data="list"
      @selection-change="setSelectRows"
    >
      <el-table-column type="selection" width="38" />
      <el-table-column align="center" label="姓名" prop="name" />
      <el-table-column align="center" label="性别" prop="gender">
        <template #default="scope">
          <el-text>{{ scope.row.gender == 'MALE' ? '男' : '女' }}</el-text>
        </template>
      </el-table-column>
      <el-table-column align="center" label="手机号" prop="telephone" />
      <el-table-column align="center" label="密码" prop="password" />
      <el-table-column align="center" label="部门" prop="department">
        <template #default="{ row }">
          <el-tag :type="'primary'" v-if="row.department">{{ row.department.name }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column align="center" label="角色" prop="role">
        <template #default="{ row }">
          <el-space wrap>
            <el-tag v-for="role in row.roles" :key="role.id" disable-transitions :type="'success'">
              {{ role.name }}
            </el-tag>
          </el-space>
        </template>
      </el-table-column>
      <el-table-column align="center" label="AI额度" width="150">
        <template #default="{ row }">
          <template v-if="row.monthlyAiCredits === -1">
            <el-tag type="info">不限</el-tag>
          </template>
          <template v-else-if="row.monthlyAiCredits > 0">
            <el-text>
              {{ (row.usedAiCredits || 0) + (row.frozenAiCredits || 0) }} /
              {{ row.monthlyAiCredits }}
            </el-text>
          </template>
          <template v-else>
            <el-tag type="danger">已禁用</el-tag>
          </template>
        </template>
      </el-table-column>
      <el-table-column align="center" label="状态" prop="status">
        <template #default="{ row }">
          <el-switch
            v-model="row.status"
            active-value="VALID"
            inactive-value="INVALID"
            :loading="row.statusLoading"
            @change="($event) => handleSwitchValidity($event, row)"
          />
        </template>
      </el-table-column>
      <el-table-column align="center" label="操作" width="460">
        <template #default="{ row }">
          <el-button text type="primary" @click="handleEdit(row)">编辑</el-button>
          <el-button text type="primary" @click="handleDispatchDepartments(row)">部门</el-button>
          <el-button text type="primary" @click="handleGrantRole(row)">角色</el-button>
          <el-button text type="primary" @click="handleAiCredits(row)">AI额度</el-button>
          <el-button text type="success" @click="handleCopy(row)">复制</el-button>
          <el-button text type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty class="vab-data-empty" description="暂无数据" />
      </template>
    </el-table>
    <vab-pagination
      :current-page="queryForm.pageNo"
      :page-size="queryForm.pageSize"
      size="small"
      :total="total"
      @current-change="handleCurrentChange"
      @size-change="handleSizeChange"
    />
    <employee-edit ref="editRef" @fetch-data="fetchData" />
    <employee-grant-role ref="grantRoleRef" @fetch-data="fetchData" />
    <employee-dispatch-departments ref="dispatchDepartmentsRef" @fetch-data="fetchData" />

    <el-dialog v-model="aiCreditsDialogVisible" title="设置AI额度" width="420px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="员工">
          <el-text>{{ aiCreditsForm.name }}</el-text>
        </el-form-item>
        <el-form-item label="额度模式">
          <el-radio-group v-model="aiCreditsForm.mode">
            <el-radio value="unlimited">不限制</el-radio>
            <el-radio value="limited">限额</el-radio>
            <el-radio value="disabled">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="aiCreditsForm.mode === 'limited'" label="月度额度">
          <el-input-number v-model="aiCreditsForm.credits" :min="1" :step="100" />
          <el-text type="info" style="margin-left: 8px">Credits</el-text>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="aiCreditsDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="aiCreditsSaving" @click="saveAiCredits">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="copyDialogVisible"
      append-to-body
      title="复制商品到指定员工"
      width="480px"
      @closed="onCopyDialogClosed"
    >
      <el-form label-width="90px">
        <el-form-item label="源员工">
          <el-text>{{ copyForm.sourceName }}</el-text>
        </el-form-item>
        <el-form-item label="可复制商品">
          <el-text v-if="copyForm.countLoading" type="info">统计中...</el-text>
          <el-text v-else :type="copyForm.count > 0 ? 'primary' : 'warning'">
            {{ copyForm.count }} 个{{ copyForm.count > 0 ? '' : '（该员工名下暂无可复制商品）' }}
          </el-text>
        </el-form-item>
        <el-form-item label="目标员工" required>
          <el-select
            v-model="copyForm.targetUserId"
            filterable
            :loading="copyForm.userLoading"
            placeholder="搜索姓名/手机号选择目标员工"
            remote
            reserve-keyword
            :remote-method="remoteQueryTarget"
            style="width: 100%"
          >
            <el-option
              v-for="item in copyTargetOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            >
              <span style="float: left">{{ item.name }}</span>
              <span style="float: right; font-size: 13px; color: var(--el-text-color-secondary)">
                {{ item.department && item.department.name }}
              </span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-alert :closable="false" show-icon type="info">
            <template #title>
              将把 <b>{{ copyForm.sourceName }}</b> 名下全部商品（含失效）复制一份给目标员工；副本归属目标员工、未上架到任何网站；重复执行只补未复制过的，不会产生重复副本。
            </template>
          </el-alert>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="copyDialogVisible = false">取消</el-button>
        <el-button
          :disabled="!copyForm.targetUserId || copyForm.count <= 0"
          :loading="copySubmitting"
          type="primary"
          @click="submitCopy"
        >
          开始复制
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="copyProgressVisible"
      append-to-body
      :close-on-click-modal="false"
      :title="copyProgress.title"
      width="460px"
      @closed="onProgressClosed"
    >
      <div style="padding: 4px 0">
        <el-progress
          :percentage="copyProgress.percent"
          :status="copyProgress.status"
          :stroke-width="16"
          text-inside
        />
        <div
          style="
            margin-top: 10px;
            color: var(--el-text-color-secondary);
            font-size: 13px;
            white-space: pre-wrap;
          "
        >
          {{ copyProgress.message }}
        </div>
      </div>
      <template #footer>
        <el-button @click="copyProgressVisible = false">
          {{ copyProgress.finished ? '关闭' : '后台运行' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import { Delete, Plus, Search } from '@element-plus/icons-vue'
import type { Department } from '/@/api/department'
import { getTree } from '/@/api/department'
import {
  copyEmployeeSpus,
  countOwnerSpu,
  doDelete,
  getRemoteQuery,
  page,
  setAiCredits,
  switchValidity,
} from '/@/api/employee'
import type { Role } from '/@/api/role'
import { getList } from '/@/api/role'
import { status as getTaskStatus } from '/@/api/taskManagement'
import { useTasksStore } from '/@/store/modules/tasks'

defineOptions({
  name: 'Employee',
})

const $baseConfirm = inject<any>('$baseConfirm')
const $baseMessage = inject<any>('$baseMessage')
const editRef = ref<any>(null)
const grantRoleRef = ref<any>(null)
const dispatchDepartmentsRef = ref<any>(null)
const tableRef = ref<any>(null)
const fold = ref<boolean>(true)
const list = ref<any>([])
const allRoleList = ref<Role[]>([])
const allDepartmentTree = ref<Department[]>([])
const listLoading = ref<boolean>(true)
const total = ref<any>(0)
const selectRows = ref<any>([])
const defaultProps = {
  label: 'name',
  children: 'children',
}
const queryForm = reactive<any>({
  pageNo: 1,
  pageSize: 20,
})

const fetchData = async () => {
  listLoading.value = true
  const { data } = await page(queryForm)
  list.value = data.list
  total.value = data.total
  listLoading.value = false
}

const handleSizeChange = (value: number) => {
  queryForm.pageNo = 1
  queryForm.pageSize = value
  fetchData()
}

const handleCurrentChange = (value: number) => {
  queryForm.pageNo = value
  fetchData()
}

const queryData = () => {
  queryForm.pageNo = 1
  fetchData()
}

const handleFold = () => {
  fold.value = !fold.value
}

const setSelectRows = (value: string) => {
  selectRows.value = value
}

const handleAdd = () => {
  editRef.value.showEdit()
}

const handleEdit = (row = {}) => {
  editRef.value.showEdit(row)
}

const handleGrantRole = (row = { roles: [] }) => {
  row.roles = row.roles || []
  grantRoleRef.value.showGrant(allRoleList.value, row)
}

const handleDispatchDepartments = (row = { departments: [] }) => {
  row.departments = row.departments || []
  dispatchDepartmentsRef.value.dispatch(allDepartmentTree.value, row)
}

const handleDelete = (row: any) => {
  if (row.id) {
    $baseConfirm('您确定要删除当前项吗', null, async () => {
      const { msg }: any = await doDelete({ ids: row.id })
      $baseMessage(msg, 'success', 'hey')
      await fetchData()
    })
  } else {
    if (selectRows.value.length > 0) {
      const ids = selectRows.value.map((item: { id: any }) => item.id).join()
      $baseConfirm('您确定要删除选中项吗', null, async () => {
        const { msg }: any = await doDelete({ ids })
        $baseMessage(msg, 'success', 'hey')
        await fetchData()
      })
    } else {
      $baseMessage('您未选中任何行', 'warning', 'hey')
    }
  }
}
const handleSwitchValidity = (
  newVal: string | number | boolean,
  row: { id: number; status: string; statusLoading: boolean }
) => {
  row.statusLoading = true
  switchValidity({ id: row.id, status: row.status })
    .then(() => {
      row.statusLoading = false
    })
    .catch(() => {
      row.statusLoading = false
      row.status = newVal == 'VALID' ? 'INVALID' : 'VALID'
    })
}

const aiCreditsDialogVisible = ref(false)
const aiCreditsSaving = ref(false)
const aiCreditsForm = reactive({
  id: 0,
  name: '',
  mode: 'unlimited' as 'unlimited' | 'limited' | 'disabled',
  credits: 1000,
})

const handleAiCredits = (row: any) => {
  aiCreditsForm.id = row.id
  aiCreditsForm.name = row.name
  if (row.monthlyAiCredits === -1) {
    aiCreditsForm.mode = 'unlimited'
    aiCreditsForm.credits = 1000
  } else if (row.monthlyAiCredits > 0) {
    aiCreditsForm.mode = 'limited'
    aiCreditsForm.credits = row.monthlyAiCredits
  } else {
    aiCreditsForm.mode = 'disabled'
    aiCreditsForm.credits = 1000
  }
  aiCreditsDialogVisible.value = true
}

const saveAiCredits = async () => {
  aiCreditsSaving.value = true
  try {
    let monthlyAiCredits: number
    if (aiCreditsForm.mode === 'unlimited') {
      monthlyAiCredits = -1
    } else if (aiCreditsForm.mode === 'limited') {
      monthlyAiCredits = aiCreditsForm.credits
    } else {
      monthlyAiCredits = 0
    }
    await setAiCredits({ id: aiCreditsForm.id, monthlyAiCredits })
    $baseMessage('AI额度设置成功', 'success', 'hey')
    aiCreditsDialogVisible.value = false
    await fetchData()
  } catch (e: any) {
    $baseMessage(e?.response?.data?.msg || '设置失败', 'error', 'hey')
  } finally {
    aiCreditsSaving.value = false
  }
}

const tasksStore = useTasksStore()

const copyDialogVisible = ref(false)
const copySubmitting = ref(false)
const copyTargetOptions = ref<any[]>([])
const copyForm = reactive({
  sourceUserId: 0 as number,
  sourceName: '',
  count: 0,
  countLoading: false,
  userLoading: false,
  targetUserId: '' as string | number,
})

const copyProgressVisible = ref(false)
const copyProgress = reactive({
  title: '复制进度',
  percent: 0,
  status: '' as '' | 'success' | 'exception',
  message: '',
  finished: false,
})
let copyPollTimer: ReturnType<typeof setInterval> | null = null

const handleCopy = (row: any) => {
  copyForm.sourceUserId = row.id
  copyForm.sourceName = row.name
  copyForm.targetUserId = ''
  copyForm.count = 0
  copyTargetOptions.value = []
  copyDialogVisible.value = true
  copyForm.countLoading = true
  countOwnerSpu(row.id)
    .then((res: any) => {
      copyForm.count = res?.data?.count ?? res?.count ?? 0
    })
    .catch(() => {
      copyForm.count = 0
    })
    .finally(() => {
      copyForm.countLoading = false
    })
}

const remoteQueryTarget = async (query: string) => {
  copyForm.userLoading = true
  try {
    const res: any = await getRemoteQuery(query || '')
    const list = res?.data?.list ?? res?.data ?? []
    // 排除源员工自己
    copyTargetOptions.value = (Array.isArray(list) ? list : []).filter(
      (item: any) => String(item.id) !== String(copyForm.sourceUserId)
    )
  } finally {
    copyForm.userLoading = false
  }
}

const onCopyDialogClosed = () => {
  copyTargetOptions.value = []
}

const stopCopyPolling = () => {
  if (copyPollTimer) {
    clearInterval(copyPollTimer)
    copyPollTimer = null
  }
}

const startCopyPolling = (taskId: string) => {
  stopCopyPolling()
  copyPollTimer = setInterval(async () => {
    try {
      const res: any = await getTaskStatus(taskId)
      const task = res?.data ?? res
      const state: string = task?.state || ''
      copyProgress.percent = task?.progress ?? 0
      copyProgress.message = task?.message || ''
      if (state === 'COMPLETED') {
        copyProgress.percent = 100
        copyProgress.status = 'success'
        copyProgress.finished = true
        stopCopyPolling()
      } else if (state === 'FAILED') {
        copyProgress.status = 'exception'
        copyProgress.finished = true
        stopCopyPolling()
      } else if (state === 'CANCELLED') {
        copyProgress.status = 'exception'
        copyProgress.message = '任务已取消'
        copyProgress.finished = true
        stopCopyPolling()
      }
    } catch {
      // 网络抖动不停止轮询
    }
  }, 1000)
}

// 关闭进度弹窗不影响后台任务，仅停止本地轮询（任务中心继续轮询）
const onProgressClosed = () => {
  stopCopyPolling()
}

const submitCopy = async () => {
  if (!copyForm.targetUserId) {
    $baseMessage('请选择目标员工', 'warning', 'hey')
    return
  }
  copySubmitting.value = true
  try {
    const res: any = await copyEmployeeSpus(copyForm.sourceUserId, copyForm.targetUserId)
    const data = res?.data ?? res
    const taskId = String(data?.taskId ?? '')
    const targetName =
      copyTargetOptions.value.find((i: any) => String(i.id) === String(copyForm.targetUserId))?.name || ''
    const label = `复制商品：${copyForm.sourceName} → ${targetName}`
    copyDialogVisible.value = false
    if (taskId) {
      // 进入任务中心
      tasksStore.addTask({
        taskId,
        taskType: 'EMPLOYEE_SPU_COPY',
        name: label,
        label,
        state: 'PENDING',
        progress: 0,
        message: '任务已提交，正在处理...',
      })
      // 弹出可关闭的独立进度弹窗
      copyProgress.title = label
      copyProgress.percent = 0
      copyProgress.status = ''
      copyProgress.message = '任务已提交，正在处理...'
      copyProgress.finished = false
      copyProgressVisible.value = true
      startCopyPolling(taskId)
    } else {
      $baseMessage('复制任务已提交', 'success', 'hey')
    }
  } catch (e: any) {
    $baseMessage(e?.response?.data?.msg || '提交失败', 'error', 'hey')
  } finally {
    copySubmitting.value = false
  }
}

const fetchAllRoles = () => {
  getList().then((data) => {
    allRoleList.value = data.data.list
  })
}

const fetchAllDepartments = () => {
  getTree({ status: 'VALID', forEmployeeManagement: true }).then((data) => {
    allDepartmentTree.value = data.data.list
  })
}
onActivated(() => {
  fetchAllRoles()
  fetchAllDepartments()
  fetchData()
  tableRef.value.doLayout()
})

onBeforeUnmount(() => {
  stopCopyPolling()
})
</script>
