<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="600px"
    @close="close"
  >
    <div class="dialog-body">
      <el-form ref="formRef" label-width="100px" :model="form" :rules="rules">
        <el-form-item label="模版名称" prop="templateName">
          <el-input v-model.trim="form.templateName" clearable />
        </el-form-item>
        <VueDraggable
          v-model="form.columns"
          :animation="150"
          ghost-class="ghost"
          handle=".column-drag-handle"
          item-key="id"
        >
        <el-form-item
          v-for="(column, index) in form.columns"
          :key="column.id"
          :label="(form.downloadTemplate ? '下载' : '上传') + '字段' + (index + 1)"
          :prop="'columns.' + index + '.headerName'"
        >
          <div class="column-input-container">
            <el-button
              class="column-drag-handle"
              circle
              :icon="Rank"
              text
              type="info"
            />
            <el-select
              v-model="column.fieldKey"
              clearable
              filterable
              placeholder="选择字段"
              style="width: 180px; margin-right: 8px"
            >
              <el-option
                v-for="option in orderTemplateFieldOptions"
                :key="option.value"
                :disabled="
                  form.columns.some((col, idx) => col.fieldKey === option.value && idx !== index)
                "
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-input
              v-model="column.headerName"
              placeholder="自定义表头"
              style="margin-right: 8px"
            />
            <el-button circle :icon="Delete" type="danger" @click="removeColumn(column)" />
          </div>
        </el-form-item>
        </VueDraggable>
      </el-form>
    </div>
    <template #footer>
      <el-button type="primary" @click="addColumn">添加字段</el-button>
      <el-button :loading="saveLoading" type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { Delete, Rank } from '@element-plus/icons-vue'
import { random } from 'lodash-es'
import { VueDraggable } from 'vue-draggable-plus'
import { type OrderTemplateColumn, doEdit } from '/@/api/orderTemplate'

defineOptions({
  name: 'OrderTemplateEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const orderTemplateFieldOptions = ref<any>([])
const form = reactive<{
  templateName: string
  downloadTemplate: boolean
  columns: OrderTemplateColumn[]
}>({
  templateName: '',
  downloadTemplate: true,
  columns: [],
})
const rules = reactive<any>({
  templateName: [{ required: true, trigger: 'blur', message: '请输入模版名称' }],
  columns: [{ required: true, trigger: 'blur', message: '请至少添加一列字段' }],
})
const uploadOrderTemplateFieldOptions = [
  { label: '订单编号', value: 'orderId' },
  { label: '原单号ID', value: 'originOrderId' },
  { label: 'SKU 代码', value: 'skuCodes' },
  { label: 'SKU 名称', value: 'skuNames' },
  { label: '中文名称', value: 'chineseName' },
  { label: '面单品名', value: 'waybillItemName' },
  { label: '产品名称', value: 'productName' },
  { label: '邮箱', value: 'email' },
  { label: '产品套餐', value: 'productPackage' },
  { label: '数量', value: 'quantity' },
  { label: '价格', value: 'price' },
  { label: '客户姓名', value: 'customerName' },
  { label: '客户手机', value: 'customerPhone' },
  { label: '省份/州', value: 'provinceOrState' },
  { label: '城市', value: 'city' },
  { label: '地区', value: 'district' },
  { label: '详细地址', value: 'detailedAddress' },
  { label: '邮编', value: 'postalCode' },
  { label: '备注', value: 'remarks' },
  { label: '下单时间', value: 'orderTime' },
  { label: '产品归属人', value: 'productOwner' },
  { label: '部门', value: 'department' },
  { label: '导单日期', value: 'orderDate' },
  { label: '订单状态', value: 'orderStatus' },
  { label: '审单备注', value: 'orderCheckRemark' },
]
const downloadOrderTemplateFieldOptions = [
  { label: '订单ID', value: 'id' },
  { label: '原单号ID', value: 'originOrderId' },
  { label: '是否COD', value: 'cod' },
  { label: '总价', value: 'totalAmount' },
  { label: '订单编号', value: 'orderNo' },
  { label: '商品ID', value: 'productId' },
  { label: '品名1', value: 'skuName' },
  { label: 'sku代码', value: 'skuCode' },
  { label: '件数', value: 'quantity' },
  { label: '赠品名称', value: 'freebiesName' },
  { label: '赠品SKU', value: 'freebiesSkuCode' },
  { label: '物流单号', value: 'trackingNumber' },
  { label: '订单状态', value: 'orderStatus' },
  { label: '审单状态', value: 'checkOrderStatus' },
  { label: '审单备注', value: 'checkOrderRemark' },
  { label: '审单提示', value: 'checkOrderReminder' },
  { label: '订单来源平台', value: 'fromPlatform' },
  { label: 'SKU', value: 'sku' },
  { label: '中文名称', value: 'merchandise' },
  { label: '面单品名', value: 'waybillProductName' },
  { label: '出货渠道', value: 'deliveryChannel' },
  { label: '产品名称', value: 'productName' },
  { label: '订单重复数', value: 'orderDuplicationCount' },
  { label: 'ip重复数', value: 'ipDuplicationCount' },
  { label: 'IP地址', value: 'remoteIp' },
  { label: '用户邮箱', value: 'email' },
  { label: '重量', value: 'weight' },
  { label: '尺寸', value: 'dimensions' },
  { label: '产品套餐', value: 'specTitle' },
  { label: '产品数量', value: 'itemQuantity' },
  { label: '产品单价', value: 'itemPrice' },
  { label: '姓名', value: 'name' },
  { label: '手机号', value: 'phone' },
  { label: '省', value: 'province' },
  { label: '市', value: 'city' },
  { label: '区', value: 'district' },
  { label: '邮编', value: 'postalCode' },
  { label: '是否偏远地区', value: 'isRemoteArea' },
  { label: '地址', value: 'address' },
  { label: '详细地址,包含省市区', value: 'fullAddress' },
  { label: '是否已配送', value: 'isDelivered' },
  { label: '邮编1', value: 'postalCode1' },
  { label: '邮编1', value: 'postalCode2' },
  { label: '物流1', value: 'logistics1' },
  { label: '物流2', value: 'logistics2' },
  { label: '物流编号', value: 'logisticsNumber' },
  { label: '备注', value: 'remark' },
  { label: '下单时间', value: 'createTime' },
  { label: '产品归属人', value: 'sellerName' },
  { label: '币种', value: 'currencyName' },
  { label: '币种代码', value: 'currencyCode' },
  { label: '产品归属部门', value: 'department' },
  { label: '国家代码', value: 'countryCode' },
  { label: '国家', value: 'country' },
  { label: '导单日期', value: 'importDate' },
  { label: '导单人员', value: 'importEmployee' },
  { label: '转寄单号', value: 'forwardingTrackingNumber' },
  { label: '退件单号', value: 'returnTrackingNumber' },
  { label: '普/特货', value: 'cargoType' },
  { label: '仓库', value: 'storehouse' },
  { label: '物流名称', value: 'logisticsName' },
  { label: '订单域名', value: 'domain' },
]

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  orderTemplateFieldOptions.value = row.downloadTemplate
    ? downloadOrderTemplateFieldOptions
    : uploadOrderTemplateFieldOptions
  nextTick(() => {
    if (row) {
      Object.assign(form, row)
      if (row.id) {
        title.value = `编辑${row.downloadTemplate ? '下载' : '上传'}模板`
      }
    } else {
      title.value = `添加${row.downloadTemplate ? '下载' : '上传'}模板`
    }
  })
}

defineExpose({
  showEdit,
})

const close = () => {
  formRef.value.clearValidate()
  formRef.value.resetFields()
  Object.assign(form, {
    id: undefined,
    templateName: '',
    columns: [],
  })
  dialogFormVisible.value = false
  emit('fetch-data')
}

const save = () => {
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      try {
        saveLoading.value = true
        const { msg }: any = await doEdit(form)
        await $baseMessage(msg, 'success', 'hey')
        dialogFormVisible.value = false
        close()
      } finally {
        saveLoading.value = false
      }
    }
  })
}

const removeColumn = (item: OrderTemplateColumn) => {
  const index = form.columns.indexOf(item)
  if (index !== -1) {
    form.columns.splice(index, 1)
  }
}

const addColumn = () => {
  form.columns.push({
    id: random(1000000000, 9999999999),
    headerName: '',
    fieldKey: '',
  })
  // 滚动条滚动到最底下
  nextTick(() => {
    const dialogBody = document.querySelector('.dialog-body')
    if (dialogBody) {
      dialogBody.scrollTop = dialogBody.scrollHeight
    }
  })
}
</script>

<style lang="scss" scoped>
.dialog-body {
  max-height: 60vh;
  padding-right: 5px;
  overflow-y: auto;

  // 自定义滚动条样式
  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-track {
    background: #f1f1f1;
    border-radius: 3px;
  }

  &::-webkit-scrollbar-thumb {
    background: #c0c4cc;
    border-radius: 3px;

    &:hover {
      background: #909399;
    }
  }
}

.column-input-container {
  display: flex;
  gap: 10px;
  align-items: center;

  .remove-btn {
    flex-shrink: 0;
  }

  :deep(.el-input) {
    flex: 1;
  }
}

// 确保表单项目有适当的间距
.el-form-item {
  margin-bottom: 18px;
}
.column-drag-handle {
  flex-shrink: 0;
  cursor: grab;

  &:active {
    cursor: grabbing;
  }
}

.ghost {
  opacity: 0.6;
}
</style>
