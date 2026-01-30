<template>
  <el-dialog v-model="show" title="编辑证书" width="1200px" :loading="loading">
    <el-row :gutter="10">
      <el-col :span="12">
        <el-form :model="form" label-width="120px">
          <el-form-item label="域名">
            <el-input v-model="form.name" disabled />
          </el-form-item>
          <el-form-item label="证书链">
            <el-input v-model="form.fullChain" type="textarea" :rows="8" placeholder="请输入证书链" />
          </el-form-item>
          <el-form-item label="私钥">
            <el-input v-model="form.privateKey" type="textarea" :rows="8" placeholder="请输入私钥" />
          </el-form-item>
          <el-form-item label="到期时间">
            <el-tag :type="expiryDateType(form.expiredDateTime ?? '')">剩余{{ expiryDateFormat(form.expiredDateTime ?? '') }}</el-tag>
          </el-form-item>
        </el-form>
      </el-col>
      <el-col :span="11">
        <div class="cert-info">
          <div v-for="(cert, index) in certInfos" :key="index" class="cert-section">
            <el-divider v-if="index > 0" />
            <span class="cert-title">{{ cert.title }}</span>
            <ul style="padding-left: 10px !important">
              <li v-for="(item, idx) in cert.items" :key="idx" class="cert-item">
                <span class="item-name">{{ item.name }}:</span>
                <span class="item-value" :class="{ 'bg-light': item.key === 'cer_signature' || item.key === 'cert_extent_info' }">
                  {{ certData && certData[item.key] }}
                </span>
              </li>
            </ul>
          </div>
        </div>
      </el-col>
    </el-row>
    <template #footer>
      <el-button @click="show = false">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确认</el-button>
      <el-button type="primary" @click="handleRenewCertificate">申请证书</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { ElMessage } from 'element-plus'
import { getCertificate, parseCertificate, renewCertificate, updateCertificate } from '/@/api/topLevelDomain'
import { expiryDateFormat, expiryDateType } from '/@/utils/datetime'

const show = ref(false)
const loading = ref(false)
const certData = ref<any>({})
const form = reactive({
  id: '',
  name: '',
  fullChain: '',
  privateKey: '',
  expiredDateTime: null,
})

const certInfos = ref([
  {
    title: '主题信息',
    items: [
      {
        name: '通用名称(CN)',
        key: 'common_name',
      },
    ],
  },
  {
    title: '签发者信息',
    items: [
      {
        name: '通用名称(CN)',
        key: 'i_common_name',
      },
      {
        name: '国家(C)',
        key: 'i_country_name',
      },
      {
        name: '组织(O)',
        key: 'cert_ca_not',
      },
    ],
  },
  {
    title: '证书信息',
    items: [
      {
        name: '序列号',
        key: 'cert_serial_number',
      },
      {
        name: '密钥类型 ',
        key: 'public_key',
      },
      {
        name: '签名算法 ',
        key: 'sign_with',
      },
      {
        name: '颁发时间 ',
        key: 'cert_not_valid_before',
      },
      {
        name: '过期时间',
        key: 'cert_not_valid_after',
      },
      {
        name: '有效期',
        key: 'cert_valid_days',
      },
      {
        name: 'SHA1指纹',
        key: 'cert_hash_sha1',
      },
      {
        name: 'SHA2指纹',
        key: 'cert_hash_sha256',
      },
      {
        name: '签名信息',
        key: 'cer_signature',
      },
      {
        name: '备用名',
        key: 'cert_extent_info',
      },
    ],
  },
])

watch(
  () => form.fullChain,
  async (newValue) => {
    if (!newValue) {
      certData.value = {}
      return
    }

    try {
      const { data } = await parseCertificate({ fullChain: newValue })
      certData.value = data
    } catch (error) {
      ElMessage.error('解析证书失败')
      certData.value = {}
    }
  }
)

const emit = defineEmits(['fetch-data'])

const showEdit = async (row = { id: '', name: '' }) => {
  show.value = true
  form.id = row.id
  form.name = row.name
  form.fullChain = (row as any).fullChain || ''
  form.privateKey = (row as any).privateKey || ''
  form.expiredDateTime = (row as any).expiredDateTime || null

  if (!row.id) return

  loading.value = true
  try {
    const { data } = await getCertificate({ id: row.id })
    form.fullChain = data.fullChain || ''
    form.privateKey = data.privateKey || ''
    form.expiredDateTime = data.expiredDateTime
  } catch (error) {
    ElMessage.error('获取证书信息失败')
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  // Add your API call here
  await updateCertificate(form)
  show.value = false
  emit('fetch-data')
}

const handleRenewCertificate = async () => {
  // Add your API call here
  await renewCertificate(form)
  show.value = false
  emit('fetch-data')
}

defineExpose({
  showEdit,
})
</script>

<style scoped lang="scss">
.cert-info {
  padding-right: 20px;
  padding-left: 10px;
  border-left: 1px solid #e5e7eb;

  .cert-title {
    font-weight: bold;
  }

  .cert-section {
    margin-bottom: 20px;
  }

  .cert-item {
    display: flex;
    margin: 5px 0;
    list-style-type: none;
  }

  .item-name {
    display: inline-block;
    flex: 0 0 100px;
    font-weight: normal;
    text-align: right;
  }

  .item-value {
    display: inline-block;
    flex: 1;
    margin-left: 10px;
    text-align: left;
    word-break: break-all;
    white-space: normal;
  }
  .bg-light {
    padding: 10px;
    background-color: #f6f7fa !important;
  }
}
</style>
