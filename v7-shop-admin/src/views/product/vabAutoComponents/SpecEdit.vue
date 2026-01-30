<template>
  <div>
    <el-dialog
      v-model="editSpecDialog"
      append-to-body
      title="规格编辑"
      width="800px"
      :z-index="3000"
    >
      <el-form ref="specFormRef" label-width="100px" :model="specifications">
        <el-form-item label="规格名称">
          <el-input v-model="specifications.name" disabled size="large" />
        </el-form-item>
        <el-form-item v-for="(value, index) in specifications.values" :key="value" label="规格值">
          <div style="display: flex; align-items: center; width: 100%">
            <div
              class="image-wrapper image-item-card el-space__item"
              @mouseenter="showDeleteButton(index)"
              @mouseleave="hideDeleteButton(index)"
            >
              <div style="width: 60px; margin-right: 10px; cursor: pointer">
                <el-icon
                  v-if="
                    !specifications.values[index].image ||
                    !specifications.values[index].image.absolutionPath
                  "
                  class="el-upload--picture-card sku-image"
                  @click="chooseSkuImage(specifications.values[index])"
                >
                  <plus />
                </el-icon>
                <el-image
                  v-else
                  class="sku-image"
                  :src="`${specifications.values[index].image.absolutionPath}`"
                  style="width: 60px; height: 60px"
                  @click="chooseSkuImage(specifications.values[index])"
                />
              </div>
              <div
                v-if="showDeletes[index]"
                class="delete-icon"
                @click="deleteImage(index)"
                @mouseenter="showDeleteButton(index)"
                @mouseleave="hideDeleteButton(index)"
              >
                <el-icon>
                  <delete />
                </el-icon>
              </div>
            </div>
            <el-input
              v-model="specifications.values[index].value"
              class="specification-input"
              placeholder="请输入规格值"
              :rules="[{ required: true, message: '规格值不能为空', trigger: 'blur' }]"
              size="large"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editSpecDialog = false">取消</el-button>
        <el-button type="primary" @click="saveSpecifications">保存</el-button>
      </template>
    </el-dialog>
    <file-chooser ref="fileChooserRef" append-to-body :z-index="5000" />
  </div>
</template>

<script setup lang="ts">
import { Delete, Plus } from '@element-plus/icons-vue'

const editSpecDialog = ref<boolean>(false)
const fileChooserRef = ref<any>(null)
const specifications = ref<any>({})
const showDeletes = ref<boolean[]>([])
const emit = defineEmits(['update-specifications'])

const showDeleteButton = (index: number) => {
  if (specifications.value.values[index].image) {
    showDeletes.value[index] = true
  }
}

const hideDeleteButton = (index: number) => {
  if (specifications.value.values[index].image) {
    showDeletes.value[index] = false
  }
}

const deleteImage = (index: number) => {
  specifications.value.values[index].image = undefined
  showDeletes.value[index] = false
}

const showEdit = (spec: { name: string }) => {
  editSpecDialog.value = true
  specifications.value = JSON.parse(JSON.stringify(spec))
  showDeletes.value = Array.from<boolean>({ length: specifications.value.values.length }).fill(
    false
  )
}

const saveSpecifications = () => {
  emit('update-specifications', specifications.value)
  editSpecDialog.value = false
}

const chooseSkuImage = async (row: any) => {
  const images = await fileChooserRef.value.choose()
  if (!images || images.length < 0) {
    return
  }
  console.log(images, row)
  row.image = images[0]
  console.log(row)
}

defineExpose({
  showEdit,
})
</script>

<style scoped>
.specification-input {
  float: left;
  width: 100%;
}
.image-wrapper {
  position: relative;
  display: inline-block;
}

.delete-icon {
  position: absolute;
  top: 5px;
  right: 5px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 5px;
  color: white;
  cursor: pointer;
  background-color: rgba(0, 0, 0, 0.5);
  border-radius: 50%;
}

.delete-icon:hover {
  background-color: rgba(70, 33, 33, 0.8);
}

.image-item-card {
  /* margin-bottom: 10px; */
  margin-left: 8px;
}
</style>
