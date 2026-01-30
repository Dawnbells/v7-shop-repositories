<template>
  <div>
    <el-button class="mb-2" type="primary" @click="batchEditSpecs">批量编辑规格</el-button>

    <vab-dialog
      v-model="batchEditDialogVisible"
      append-to-body
      :draggable="false"
      title="批量编辑规格"
      width="700px"
      @close="close"
    >
      <el-form ref="batchEditFormRef" label-width="100px" :model="form">
        <el-form-item label="商品图片" prop="skuImage">
          <el-checkbox v-model="isModify.skuImage" />
          <div style="width: 500px; margin-left: 10px">
            <el-icon
              v-if="!form.skuImage || !form.skuImage.absolutionPath"
              class="el-upload--picture-card sku-image"
              @click="chooseSkuImage()"
            >
              <plus />
            </el-icon>
            <el-image
              v-else
              class="sku-image"
              :src="`${form.skuImage.absolutionPath}`"
              @click="chooseSkuImage()"
            />
          </div>
        </el-form-item>
        <el-form-item label="SKU代码" prop="skuCode">
          <el-checkbox v-model="isModify.skuCode" />
          <el-select
            v-model="form.skuCode"
            allow-create
            clearable
            filterable
            :loading="skuLoading"
            remote
            :remote-method="remoteQuerySku"
            style="width: 500px; margin-left: 10px"
            @change="(skuCode) => onSelectSku(skuCode)"
          >
            <el-option
              v-for="item in skuOptions"
              :key="item.skuCode"
              :label="item.skuCode"
              :value="item.skuCode"
            >
              <span class="sku-code">{{ item.skuCode }}</span>
              <span class="sku-item-name">{{ item.name }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-tooltip :content="form.skuName" effect="light" placement="top">
          <el-form-item label="SKU品名" prop="skuName">
            <el-checkbox v-model="isModify.skuCode" />
            <el-input
              v-model.trim="form.skuName"
              clearable
              :disabled="form.skuId !== '' && parseInt(form.skuId) > 0"
              style="width: 500px; margin-left: 10px"
              @input="
                (value) => {
                  form.skuName = value
                  isModify.skuCode = true
                }
              "
            />
          </el-form-item>
          <template #content>
            <span v-if="form.skuName" style="font-size: 12px">{{ form.skuName }}</span>
            <span v-else style="font-size: 12px; color: red">请输入商品品名</span>
          </template>
        </el-tooltip>
        <el-form-item label="商品售价" prop="sellPrice">
          <el-checkbox v-model="isModify.sellPrice" />
          <el-space style="width: 500px; margin-left: 10px">
            <el-input
              v-model.trim="form.sellPrice"
              placeholder="请输入商品售价"
              style="width: 245px"
              @input="
                (value) => {
                  form.sellPriceCurrency = exchangeToCurrency(value)
                  isModify.sellPrice = true
                }
              "
            >
              <template #prepend>{{ standardCurrency.symbol }}</template>
            </el-input>
            <el-input
              v-model.trim="form.sellPriceCurrency"
              placeholder="请输入商品售价"
              style="width: 245px"
              @input="
                (value) => {
                  form.sellPrice = exchangeToStandardCurrency(value)
                  isModify.sellPrice = true
                }
              "
            >
              <template #prepend>{{ currency.symbol }}</template>
            </el-input>
          </el-space>
        </el-form-item>
        <el-form-item label="商品原价" prop="originPrice">
          <el-checkbox v-model="isModify.originPrice" />
          <el-space style="width: 500px; margin-left: 10px">
            <el-input
              v-model="form.originPrice"
              placeholder="请输入商品原价"
              style="width: 245px"
              @input="
                (value) => {
                  form.originPriceCurrency = exchangeToCurrency(value)
                  isModify.originPrice = true
                }
              "
            >
              <template #prepend>{{ standardCurrency.symbol }}</template>
            </el-input>
            <el-input
              v-model.trim="form.originPriceCurrency"
              placeholder="请输入商品原价"
              style="width: 245px"
              @input="
                (value) => {
                  form.originPrice = exchangeToStandardCurrency(value)
                  isModify.originPrice = true
                }
              "
            >
              <template #prepend>{{ currency.symbol }}</template>
            </el-input>
          </el-space>
        </el-form-item>
        <el-form-item label="商品成本价" prop="costPrice">
          <el-checkbox v-model="isModify.costPrice" />
          <el-space style="width: 500px; margin-left: 10px">
            <el-input
              v-model="form.costPrice"
              placeholder="请输入商品成本价"
              style="width: 245px"
              @input="
                (value) => {
                  form.costPriceCurrency = exchangeToCurrency(value)
                  isModify.costPrice = true
                }
              "
            >
              <template #prepend>{{ standardCurrency.symbol }}</template>
            </el-input>
            <el-input
              v-model.trim="form.costPriceCurrency"
              placeholder="请输入商品成本价"
              style="width: 245px"
              @input="
                (value) => {
                  form.costPrice = exchangeToStandardCurrency(value)
                  isModify.costPrice = true
                }
              "
            >
              <template #prepend>{{ currency.symbol }}</template>
            </el-input>
          </el-space>
        </el-form-item>
        <el-form-item label="库存策略" prop="linkStock">
          <el-checkbox v-model="isModify.linkStock" />
          <el-checkbox
            v-model="form.linkStock"
            style="margin-left: 10px"
            @change="() => (isModify.linkStock = true)"
          >
            缺货后继续销售
          </el-checkbox>
        </el-form-item>
        <el-form-item label="库存数量" prop="stockQuantity">
          <el-checkbox v-model="isModify.stockQuantity" />
          <el-input
            v-model="form.stockQuantity"
            placeholder="请输入库存数量"
            style="width: 500px; margin-left: 10px"
            type="number"
            @input="
              (value) => {
                form.stockQuantity = value
                isModify.stockQuantity = true
              }
            "
          />
        </el-form-item>
        <el-form-item label="商品条码" prop="barcode">
          <el-checkbox v-model="isModify.barcode" />
          <el-input
            v-model="form.barcode"
            placeholder="请输入商品条码"
            style="width: 500px; margin-left: 10px"
            @input="
              (value) => {
                form.barcode = value
                isModify.barcode = true
              }
            "
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button
          @click="
            () => {
              batchEditDialogVisible = false
              close()
            }
          "
        >
          取 消
        </el-button>
        <el-button type="primary" @click="confirmBatchEdit">确 定</el-button>
      </template>
    </vab-dialog>
    <file-chooser ref="fileChooserRef" />
  </div>
</template>

<script lang="ts" setup>
import { Plus } from '@element-plus/icons-vue'
import { ref } from 'vue'

import Decimal from 'decimal.js'
import { ElMessage } from 'element-plus'
import { getRemoteQuery } from '/@/api/sku'

const batchEditDialogVisible = ref(false)
const fileChooserRef = ref<any>(null)
const batchEditFormRef = ref<any>(null)
const emit = defineEmits(['onBatchEdit'])
const props = defineProps({
  hadSelectedSpec: {
    type: Function as PropType<() => boolean>,
    default: () => false,
    required: true,
  },
  standardCurrency: {
    type: Object,
    default: () => ({
      name: '美元',
      symbol: '$',
      code: 'USD',
      exchangeRate: '1',
    }),
  },
  currency: {
    type: Object,
    default: () => ({
      name: '美元',
      symbol: '$',
      code: 'USD',
      exchangeRate: '1',
    }),
  },
})

const { standardCurrency, currency } = toRefs(props)

const form = ref<any>({
  skuId: '',
  skuName: '',
  skuCode: '',
  skuImage: {
    absolutionPath: '',
  },
  sellPrice: '',
  sellPriceCurrency: '',
  originPrice: '',
  originPriceCurrency: '',
  costPrice: '',
  costPriceCurrency: '',
  linkStock: true,
  stockQuantity: -1,
  barcode: '',
})

const isModify = ref({
  skuId: false,
  skuCode: false,
  skuImage: false,
  sellPrice: false,
  originPrice: false,
  costPrice: false,
  linkStock: false,
  stockQuantity: false,
  barcode: false,
})
const skuOptions = ref<any[]>([])
const skuLoading = ref(false)

const onSelectSku = (skuCode: any) => {
  console.log(`${JSON.stringify(skuCode)}pre`)
  const skuList = skuOptions.value.filter((c: any) => c.skuCode === skuCode)
  console.log(`id = ${skuCode} >> ${JSON.stringify(skuList)}`)
  isModify.value.skuCode = true
  if (skuList && skuList.length > 0) {
    const sku = skuList[0] as { id: string; name: string; skuCode: string }
    form.value.skuId = sku.id
    form.value.skuName = sku.name
    form.value.skuCode = sku.skuCode || skuCode // Use skuCode as a fallback if sku.skuCode is empty
    form.value.sku = sku
  } else {
    form.value.skuId = null
    form.value.skuName = ''
    form.value.skuCode = skuCode
    form.value.sku = null
  }
}
const batchEditSpecs = () => {
  if (!props.hadSelectedSpec()) {
    ElMessage.error('请先选中规格')
    return
  }
  batchEditDialogVisible.value = true
  form.value = {
    skuId: '',
    skuImage: {
      absolutionPath: '',
    },
    sku: null,
    skuCode: '',
    skuName: '',
    sellPrice: '',
    sellPriceCurrency: '',
    originPrice: '',
    originPriceCurrency: '',
    costPrice: '',
    costPriceCurrency: '',
    linkStock: true,
    stockQuantity: -1,
    barcode: '',
  }
  isModify.value = {
    skuId: false,
    skuCode: false,
    skuImage: false,
    sellPrice: false,
    originPrice: false,
    costPrice: false,
    linkStock: false,
    stockQuantity: false,
    barcode: false,
  }
}

const confirmBatchEdit = () => {
  const modifiedProps: any = {}
  if (isModify.value.skuId) {
    modifiedProps.skuId = form.value.skuId
  }
  if (isModify.value.skuCode) {
    modifiedProps.skuName = form.value.skuName
    modifiedProps.skuCode = form.value.skuCode
    modifiedProps.skuId = form.value.skuId
    modifiedProps.sku = form.value.sku
  }
  if (isModify.value.skuImage) {
    modifiedProps.skuImage = form.value.skuImage
  }
  if (isModify.value.sellPrice) {
    modifiedProps.sellPrice = form.value.sellPrice
    modifiedProps.sellPriceCurrency = form.value.sellPriceCurrency
  }
  if (isModify.value.originPrice) {
    modifiedProps.originPrice = form.value.originPrice
    modifiedProps.originPriceCurrency = form.value.originPriceCurrency
  }
  if (isModify.value.costPrice) {
    modifiedProps.costPrice = form.value.costPrice
    modifiedProps.costPriceCurrency = form.value.costPriceCurrency
  }
  if (isModify.value.linkStock) {
    modifiedProps.linkStock = form.value.linkStock
  }
  if (isModify.value.stockQuantity) {
    modifiedProps.stockQuantity = form.value.stockQuantity
  }
  if (isModify.value.barcode) {
    modifiedProps.barcode = form.value.barcode
  }

  console.log(Object.keys(modifiedProps).length)
  if (Object.keys(modifiedProps).length === 0) {
    ElMessage.error('请至少修改一个字段')
    return
  }

  emit('onBatchEdit', modifiedProps)

  batchEditDialogVisible.value = false
}
const close = () => {
  batchEditFormRef?.value?.clearValidate()
  batchEditFormRef?.value?.resetFields()
}
const remoteQuerySku = async (query: any) => {
  skuLoading.value = true
  try {
    const { data } = await getRemoteQuery(query)
    skuOptions.value = data.list
  } finally {
    skuLoading.value = false
  }
}
const chooseSkuImage = async () => {
  const images = await fileChooserRef.value?.choose()
  if (!images || images.length < 0) {
    return
  }
  form.value.skuImage = images[0]
  console.log(form.value.skuImage)
  isModify.value.skuImage = true
}

const exchangeToCurrency = (amount: string | number) => {
  try {
    if (!amount) {
      return '0.00'
    }
    const standard = new Decimal(amount)
    const exchangeRate = new Decimal(currency.value.exchangeRate)
    if (
      !exchangeRate ||
      exchangeRate.isNaN() ||
      !standard ||
      standard.isNaN() ||
      exchangeRate.equals(0)
    ) {
      return '0.00'
    }
    return standard.mul(exchangeRate).toFixed(4, Decimal.ROUND_HALF_UP)
  } catch {
    return '0.00'
  }
}

const exchangeToStandardCurrency = (amount: string) => {
  try {
    if (!amount) {
      return '0.00'
    }
    const standard = new Decimal(amount)
    const exchangeRate = new Decimal(currency.value.exchangeRate)
    if (
      !exchangeRate ||
      exchangeRate.isNaN() ||
      !standard ||
      standard.isNaN() ||
      exchangeRate.equals(0)
    ) {
      return '0.00'
    }
    return standard.dividedBy(exchangeRate).toFixed(8, Decimal.ROUND_HALF_UP)
  } catch {
    return '0.00'
  }
}
</script>

<style lang="scss">
.modify-form-item {
  .el-form-item__label {
    color: rgb(197, 104, 104) !important;
  }
}

.mb-2 {
  margin-bottom: 10px;
}
.sku-image {
  width: 60px !important;
  height: 60px !important;
  cursor: pointer;
  border: 1px solid #ccc;
}
</style>
