<template>
  <div v-if="dialogFormVisible">
    <vab-dialog
      v-model="dialogFormVisible"
      append-to-body
      :draggable="false"
      :title="title"
      width="80%"
      @close="close"
    >
      <el-form ref="formRef" label-position="top" label-width="80px" :model="form" :rules="rules">
        <el-row :gutter="10">
          <el-col :span="18">
            <el-card header="商品信息">
              <el-form-item label="商品标题" prop="title">
                <ai-translate-wrapper
                  :language-id="form.languageId"
                  :source="form.title"
                  type="text"
                  @apply="(v) => (form.title = v)"
                >
                  <el-input v-model.trim="form.title" clearable maxlength="500" show-word-limit />
                </ai-translate-wrapper>
              </el-form-item>
              <el-form-item label="商品摘要" prop="summary">
                <ai-translate-wrapper
                  :language-id="form.languageId"
                  :source="form.summary"
                  type="text"
                  @apply="(v) => (form.summary = v)"
                >
                  <el-input v-model.trim="form.summary" clearable maxlength="250" show-word-limit />
                </ai-translate-wrapper>
              </el-form-item>
              <el-form-item label="面单品名" prop="waybillProductName">
                <ai-translate-wrapper
                  :language-id="form.languageId"
                  :source="form.waybillProductName"
                  type="text"
                  @apply="(v) => (form.waybillProductName = v)"
                >
                  <el-input
                    v-model.trim="form.waybillProductName"
                    clearable
                    maxlength="250"
                    show-word-limit
                  />
                </ai-translate-wrapper>
              </el-form-item>
              <el-form-item label="中文品名" prop="merchandise">
                <el-autocomplete
                  v-model="form.merchandise"
                  allow-create
                  :fetch-suggestions="remoteQueryMerchandise"
                  filterable
                  :loading="merchandiseLoading"
                  remote
                  style="width: 100%"
                  @change="onSelectMerchandise"
                />
              </el-form-item>
              <el-form-item label="商品描述" prop="introduction">
                <ai-translate-wrapper
                  :language-id="form.languageId"
                  :source="form.introduction"
                  type="html"
                  @apply="(v) => (form.introduction = v)"
                >
                  <product-wang-editor v-model="form.introduction" :language-id="form.languageId" />
                </ai-translate-wrapper>
              </el-form-item>
            </el-card>
            <el-card header="商品图片/视频">
              <el-form-item label="商品主图" prop="skuImages">
                <vue-draggable v-model="form.skuImages" :animation="200" ghost-class="ghost">
                  <div
                    v-for="{ image, originalIndex } in validSkuImages"
                    :key="image.id || image.name || image.absolutionPath || originalIndex"
                    class="image-wrapper image-item-card el-space__item"
                  >
                    <ai-translate-wrapper
                      :language-id="form.languageId"
                      :source="image"
                      type="image"
                      @apply="(v) => onApplySkuImage(originalIndex, v)"
                    >
                      <el-image
                        class="el-upload--picture-card"
                        fit="fill"
                        :src="image.absolutionPath || ''"
                        style="
                          width: 120px !important;
                          height: 120px !important;
                          cursor: pointer;
                          border: 1px solid #d9d9d9;
                        "
                        @click="chooseFormSkuImage(originalIndex)"
                        @mouseenter="showDeleteButton(originalIndex)"
                        @mouseleave="hideDeleteButton(originalIndex)"
                      />
                    </ai-translate-wrapper>
                    <div
                      v-if="image.showDelete"
                      class="delete-icon"
                      @click="deleteImage(originalIndex)"
                      @mouseenter="showDeleteButton(originalIndex)"
                      @mouseleave="hideDeleteButton(originalIndex)"
                    >
                      <el-icon>
                        <delete />
                      </el-icon>
                    </div>
                  </div>
                </vue-draggable>
                <el-icon
                  v-if="validSkuImages.length < MAX_SKU_IMAGE_COUNT"
                  class="el-upload--picture-card image-wrapper add-image-button image-item-card"
                  style="width: 120px !important; height: 120px !important"
                  @click="chooseFormSkuImage(100)"
                >
                  <plus />
                </el-icon>
              </el-form-item>
              <el-form-item label="商品视频" prop="skuVideo">
                <el-icon
                  v-if="!form.skuVideo"
                  class="el-upload--picture-card"
                  style="width: 120px !important; height: 120px !important"
                  @click="chooseFormSkuVideo()"
                >
                  <plus />
                </el-icon>
                <div v-else class="image-wrapper">
                  <el-image
                    class="el-upload--picture-card"
                    fit="fill"
                    :src="`${form.skuVideo.absolutionPath}`"
                    style="
                      width: 120px !important;
                      height: 120px !important;
                      cursor: pointer;
                      border: 1px solid #d9d9d9;
                    "
                    @click="chooseFormSkuVideo()"
                    @mouseenter="form.skuVideo.showDelete = true"
                    @mouseleave="form.skuVideo.showDelete = false"
                  />
                  <div
                    v-if="form.skuVideo.showDelete"
                    class="delete-icon"
                    @click="form.skuVideo = null"
                    @mouseenter="form.skuVideo.showDelete = true"
                    @mouseleave="form.skuVideo.showDelete = false"
                  >
                    <el-icon>
                      <delete />
                    </el-icon>
                  </div>
                </div>
              </el-form-item>
              <p class="uploadTipDesc">
                支持上传jpg、png、webp、SVG格式图片，最大限制为10M（4M为最佳店铺浏览体验）；支持上传GIF格式动图，最大限制8M
              </p>
            </el-card>
            <el-card header="价格/交易">
              <el-row v-if="!form.isMultiSpecs" :gutter="50">
                <el-col :span="12">
                  <el-form-item label="商品售价" prop="sellPrice">
                    <el-input
                      v-model.trim="form.sellPrice"
                      @input="(value) => (form.sellPriceCurrency = exchangeToCurrency(value))"
                    >
                      <template #prepend>{{ standardCurrency.symbol }}</template>
                    </el-input>
                    <el-input
                      v-model.trim="form.sellPriceCurrency"
                      style="margin-top: 10px"
                      @input="(value) => (form.sellPrice = exchangeToStandardCurrency(value))"
                    >
                      <template #prepend>{{ currency.symbol }}</template>
                    </el-input>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="商品原价" prop="originPrice">
                    <el-input
                      v-model.trim="form.originPrice"
                      @input="(value) => (form.originPriceCurrency = exchangeToCurrency(value))"
                    >
                      <template #prepend>{{ standardCurrency.symbol }}</template>
                    </el-input>
                    <el-input
                      v-model.trim="form.originPriceCurrency"
                      style="margin-top: 10px"
                      @input="(value) => (form.originPrice = exchangeToStandardCurrency(value))"
                    >
                      <template #prepend>{{ currency.symbol }}</template>
                    </el-input>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row v-if="!form.isMultiSpecs" :gutter="50">
                <el-col :span="12">
                  <el-form-item label="商品成本价" prop="costPrice">
                    <el-input
                      v-model.trim="form.costPrice"
                      @input="(value) => (form.costPriceCurrency = exchangeToCurrency(value))"
                    >
                      <template #prepend>{{ standardCurrency.symbol }}</template>
                    </el-input>

                    <el-input
                      v-model.trim="form.costPriceCurrency"
                      style="margin-top: 10px"
                      @input="(value) => (form.costPrice = exchangeToStandardCurrency(value))"
                    >
                      <template #prepend>{{ currency.symbol }}</template>
                    </el-input>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row v-if="!form.isMultiSpecs" :gutter="20">
                <el-col :span="6">
                  <el-form-item label="利润" prop="profit">
                    <el-input v-model="profit" disabled />
                  </el-form-item>
                </el-col>
                <el-col :span="6">
                  <el-form-item label="利润率" prop="profitMargin">
                    <el-input v-model="profitMargin" disabled />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item prop="isTaxable">
                <el-checkbox v-model="form.isTaxable">需要收取税费</el-checkbox>
              </el-form-item>
              <el-row :gutter="10">
                <el-col :span="6">
                  <el-form-item v-if="form.isTaxable" label="收取方式" prop="taxationMethod">
                    <el-select v-model="form.taxationMethod">
                      <el-option label="按固定税费金额" value="FIXED" />
                      <el-option label="按金额收取税费" value="AMOUNT_BASED" />
                      <el-option label="按购买量收取税费" value="QUANTITY_BASED" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col v-if="form.isTaxable && form.taxationMethod === 'FIXED'" :span="18">
                  <el-form-item label="收取金额" prop="fixedTaxAmount">
                    <el-input
                      v-model.trim="form.fixedTaxAmount"
                      @input="(value) => (form.fixedTaxAmountCurrency = exchangeToCurrency(value))"
                    >
                      <template #prepend>{{ standardCurrency.symbol }}</template>
                    </el-input>
                    <el-input
                      v-model.trim="form.fixedTaxAmountCurrency"
                      style="margin-top: 10px"
                      @input="(value) => (form.fixedTaxAmount = exchangeToStandardCurrency(value))"
                    >
                      <template #prepend>{{ currency.symbol }}</template>
                    </el-input>
                  </el-form-item>
                </el-col>

                <el-col v-if="form.isTaxable && form.taxationMethod === 'AMOUNT_BASED'" :span="8">
                  <el-form-item label="收取条件" prop="taxAmountThreshold">
                    <el-input
                      v-model.trim="form.taxAmountThreshold"
                      @input="
                        (value) => (form.taxAmountThresholdCurrency = exchangeToCurrency(value))
                      "
                    >
                      <template #prepend>每满</template>
                      <template #append>{{ standardCurrency.code }}</template>
                    </el-input>
                    <el-input
                      v-model.trim="form.taxAmountThresholdCurrency"
                      style="margin-top: 10px"
                      @input="
                        (value) => (form.taxAmountThreshold = exchangeToStandardCurrency(value))
                      "
                    >
                      <template #prepend>每满</template>
                      <template #append>{{ currency.code }}</template>
                    </el-input>
                  </el-form-item>
                </el-col>
                <el-col v-if="form.isTaxable && form.taxationMethod === 'QUANTITY_BASED'" :span="8">
                  <el-form-item label="收取条件" prop="taxQuantityThreshold">
                    <el-input v-model.trim="form.taxQuantityThreshold">
                      <template #prepend>每满</template>
                      <template #append>件</template>
                    </el-input>
                  </el-form-item>
                </el-col>
                <el-col
                  v-if="
                    form.isTaxable &&
                    (form.taxationMethod === 'QUANTITY_BASED' ||
                      form.taxationMethod === 'AMOUNT_BASED')
                  "
                  :span="10"
                >
                  <el-form-item label="收取金额" prop="taxPerBase">
                    <el-input
                      v-model.trim="form.taxPerBase"
                      @input="(value) => (form.taxPerBaseCurrency = exchangeToCurrency(value))"
                    >
                      <template #prepend>{{ standardCurrency.symbol }}</template>
                    </el-input>
                    <el-input
                      v-model.trim="form.taxPerBaseCurrency"
                      style="margin-top: 10px"
                      @input="(value) => (form.taxPerBase = exchangeToStandardCurrency(value))"
                    >
                      <template #prepend>{{ currency.symbol }}</template>
                    </el-input>
                  </el-form-item>
                </el-col>
              </el-row>
            </el-card>
            <el-card v-if="!form.isMultiSpecs" header="库存">
              <el-row :gutter="50">
                <el-col :span="12">
                  <el-form-item label="SKU" prop="skuCode">
                    <el-select
                      v-model="form.skuCode"
                      allow-create
                      filterable
                      :loading="skuLoading"
                      remote
                      :remote-method="remoteQuerySku"
                      style="width: 100%"
                      @change="onSelectSku"
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
                </el-col>
                <el-col :span="12">
                  <el-form-item label="品名" prop="skuName">
                    <el-input v-model.trim="form.skuName" clearable :disabled="form.skuId > 0" />
                  </el-form-item>
                </el-col>
              </el-row>

              <el-row :gutter="50">
                <el-col :span="12">
                  <el-form-item label="库存数量" prop="stockQuantity">
                    <el-input
                      v-model.trim="form.stockQuantity"
                      clearable
                      placeholder="填写负数时商城不显示数量选择控件"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="条码(ISBN、UPC、GTIN等)" prop="barcode">
                    <el-input v-model.trim="form.barcode" clearable />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item prop="linkStock">
                <el-checkbox v-model="form.linkStock">缺货后继续销售</el-checkbox>
              </el-form-item>
            </el-card>
            <el-card header="多款式">
              <el-form-item prop="isMultiSpecs">
                <el-checkbox v-model="form.isMultiSpecs">此商品有多个款式</el-checkbox>
              </el-form-item>
              <template v-if="form.isMultiSpecs">
                <el-row
                  v-for="(specification, index) in specifications"
                  :key="specification.name"
                  :gutter="10"
                >
                  <el-col :span="6">
                    <el-form-item :prop="`specifications.${index}.name`" size="large">
                      <el-select
                        v-model="specification.name"
                        allow-create
                        default-first-option
                        filterable
                        placeholder="请选择款式"
                        :reserve-keyword="false"
                        style="display: inline-block"
                        suffix-icon=""
                        @change="onChangeSpecificationName"
                      >
                        <el-option
                          v-for="item in specNameOptions"
                          :key="item"
                          :disabled="isSpecSelected(item)"
                          :label="item"
                          :value="item"
                        />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="15">
                    <el-form-item :prop="`specifications[${index}].values`">
                      <el-input-tag
                        v-model="specificationValues[specification.name]"
                        aria-label="请输入规格后按回车确认选项"
                        clearable
                        draggable
                        placeholder="请输入规格后按回车确认选项"
                        size="large"
                        @change="onChangeSpecificationValue"
                      >
                        <template #tag="{ value }">
                          <div style="display: flex; align-items: center; justify-content: center">
                            <el-image
                              v-if="
                                specifications[index].values.find((v) => v.value === value)?.image
                              "
                              :src="
                                specifications[index].values.find((v) => v.value === value)!.image
                                  ?.absolutionPath
                              "
                              style="width: 25px; height: 25px; margin-right: 10px"
                            />
                            <span>{{ value }}</span>
                          </div>
                        </template>
                      </el-input-tag>
                    </el-form-item>
                  </el-col>
                  <el-col :span="3">
                    <el-form-item style="margin-top: 5px">
                      <el-button
                        circle
                        :icon="Edit"
                        size="small"
                        type="primary"
                        @click="editSpec(specification)"
                      />
                      <el-button
                        v-if="index !== 0 || specifications.length > 1"
                        circle
                        :icon="Delete"
                        size="small"
                        type="danger"
                        @click="removeSpec(specification)"
                      />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-button v-if="specifications.length < 5" :icon="Plus" @click="addSpec">
                  添加商品规格
                </el-button>
              </template>
            </el-card>
            <el-card v-if="form.isMultiSpecs" header="款式列表">
              <el-row>
                <el-col>
                  <batch-edit-spec
                    :currency="currency"
                    :had-selected-spec="hadSelectedSpec"
                    :standard-currency="standardCurrency"
                    @on-batch-edit="onBatchEdit"
                  />
                </el-col>
              </el-row>
              <el-row style="margin-bottom: 10px">
                <el-select
                  v-for="specification in specifications"
                  :key="specification.name"
                  v-model="batchSelectSpec[specification.name]"
                  multiple
                  :placeholder="`批量选中${specification.name}`"
                  style="width: 300px; margin-right: 10px"
                  @change="onBatchSelectSpecChange"
                >
                  <el-option
                    v-for="item in specificationValues[specification.name]"
                    :key="item"
                    :label="item"
                    :value="item"
                  />
                </el-select>
              </el-row>
              <el-form-item>
                <el-table ref="tableRef" border :data="skuSpecifications" fit>
                  <el-table-column type="selection" width="38" />
                  <el-table-column
                    align="center"
                    fixed
                    label="图片"
                    prop="`specifications.${$index}.skuImage`"
                    width="88"
                  >
                    <template #default="{ row }">
                      <div>
                        <el-icon
                          v-if="!row.skuImage || row.skuImage.length === 0"
                          class="el-upload--picture-card"
                          style="width: 60px !important; height: 60px !important"
                          @click="chooseSkuImage(row)"
                        >
                          <plus />
                        </el-icon>
                        <ai-translate-wrapper
                          v-else
                          :language-id="form.languageId"
                          :source="row.skuImage"
                          type="image"
                          @apply="(v) => (row.skuImage = { ...row.skuImage, ...v })"
                        >
                          <el-image
                            :src="`${row.skuImage.absolutionPath}`"
                            @click="chooseSkuImage(row)"
                          />
                        </ai-translate-wrapper>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column align="center" fixed label="款式" :width="specWidth">
                    <template #default="{ row }">
                      <span>
                        {{
                          row.attributes &&
                          row.attributes.map((item: SpecType) => item.value).join('·')
                        }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column header-align="center" label="SKU" prop="skuCode" width="200">
                    <template #default="{ row, $index }">
                      <el-form-item
                        inline-message
                        :prop="`specifications.${$index}.skuCode`"
                        :rules="rules.skuCode"
                        trigger="change"
                      >
                        <el-select
                          v-model="row.skuCode"
                          allow-create
                          filterable
                          :loading="skuLoading"
                          remote
                          :remote-method="remoteQuerySku"
                          style="width: 100%"
                          @change="(skuCode) => onSelectRowSku(row, skuCode)"
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
                    </template>
                  </el-table-column>
                  <el-table-column header-align="center" label="品名" prop="skuName" width="200">
                    <template #default="{ row, $index }">
                      <el-tooltip :content="row.skuName" effect="light" placement="top">
                        <el-form-item
                          inline-message
                          :prop="`specifications.${$index}.skuName`"
                          :rules="rules.skuName"
                        >
                          <el-input
                            v-model.trim="row.skuName"
                            clearable
                            :disabled="row.skuId > 0"
                          />
                        </el-form-item>
                        <template #content>
                          <span v-if="row.skuName" style="font-size: 12px">{{ row.skuName }}</span>
                          <span v-else style="font-size: 12px; color: red">请输入商品品名</span>
                        </template>
                      </el-tooltip>
                    </template>
                  </el-table-column>
                  <el-table-column header-align="center" label="售价" prop="sellPrice" width="200">
                    <template #default="{ row, $index }">
                      <el-form-item
                        inline-message
                        :prop="`specifications.${$index}.sellPrice`"
                        :rules="rules.sellPrice"
                      >
                        <el-input
                          v-model.trim="row.sellPrice"
                          @input="(value) => (row.sellPriceCurrency = exchangeToCurrency(value))"
                        >
                          <template #prepend>{{ standardCurrency.symbol }}</template>
                        </el-input>
                        <el-input
                          v-model.trim="row.sellPriceCurrency"
                          style="margin-top: 10px"
                          @input="(value) => (row.sellPrice = exchangeToStandardCurrency(value))"
                        >
                          <template #prepend>{{ currency.symbol }}</template>
                        </el-input>
                      </el-form-item>
                    </template>
                  </el-table-column>

                  <el-table-column
                    header-align="center"
                    label="原价"
                    prop="originPrice"
                    width="200"
                  >
                    <template #default="{ row, $index }">
                      <el-form-item
                        inline-message
                        :prop="`specifications.${$index}.originPrice`"
                        :rules="rules.originPrice"
                      >
                        <el-input
                          v-model.trim="row.originPrice"
                          @input="(value) => (row.originPriceCurrency = exchangeToCurrency(value))"
                        >
                          <template #prepend>{{ standardCurrency.symbol }}</template>
                        </el-input>
                        <el-input
                          v-model.trim="row.originPriceCurrency"
                          style="margin-top: 10px"
                          @input="(value) => (row.originPrice = exchangeToStandardCurrency(value))"
                        >
                          <template #prepend>{{ currency.symbol }}</template>
                        </el-input>
                      </el-form-item>
                    </template>
                  </el-table-column>

                  <el-table-column
                    header-align="center"
                    label="成本价"
                    prop="costPrice"
                    width="200"
                  >
                    <template #default="{ row, $index }">
                      <el-form-item
                        inline-message
                        :prop="`specifications.${$index}.costPrice`"
                        :rules="rules.costPrice"
                      >
                        <el-input
                          v-model.trim="row.costPrice"
                          @input="(value) => (row.costPriceCurrency = exchangeToCurrency(value))"
                        >
                          <template #prepend>{{ standardCurrency.symbol }}</template>
                        </el-input>
                        <el-input
                          v-model.trim="row.costPriceCurrency"
                          style="margin-top: 10px"
                          @input="(value) => (row.costPrice = exchangeToStandardCurrency(value))"
                        >
                          <template #prepend>{{ currency.symbol }}</template>
                        </el-input>
                      </el-form-item>
                    </template>
                  </el-table-column>
                  <el-table-column
                    header-align="center"
                    label="库存策略"
                    prop="linkStock"
                    width="150"
                  >
                    <template #default="{ row }">
                      <el-checkbox v-model="row.linkStock">缺货后继续销售</el-checkbox>
                    </template>
                  </el-table-column>
                  <el-table-column
                    header-align="center"
                    label="库存数量"
                    prop="stockQuantity"
                    width="200"
                  >
                    <template #default="{ row, $index }">
                      <el-form-item
                        inline-message
                        :prop="`specifications.${$index}.stockQuantity`"
                        :rules="rules.stockQuantity"
                      >
                        <el-input v-model.trim="row.stockQuantity" clearable type="number" />
                      </el-form-item>
                    </template>
                  </el-table-column>

                  <el-table-column
                    header-align="center"
                    label="条码(ISBN、UPC、GTIN等)"
                    prop="barcode"
                    width="250"
                  >
                    <template #default="{ row }">
                      <el-input v-model.trim="row.barcode" clearable />
                    </template>
                  </el-table-column>
                  <el-table-column align="center" fixed="right" label="操作" width="80">
                    <template #default="{ row }">
                      <el-button text @click="() => (row.deleted = true)">
                        <el-icon color="red"><delete /></el-icon>
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </el-form-item>
            </el-card>
            <el-card header="备发货设置">
              <el-row>
                <el-col>
                  <el-form-item :prop="form.alternativeSkuCodes">
                    <el-select
                      v-model="form.alternativeSkuCodes"
                      filterable
                      :loading="skuLoading"
                      multiple
                      remote
                      :remote-method="remoteQuerySku"
                      style="width: 100%"
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
                      <!--
                      <template #tag>
                        <el-tag v-for="item in form.alternativeSkus" :key="item.skuCode" type="primary">
                          {{ item }}
                        </el-tag>
                      </template> -->
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
            </el-card>
            <el-card header="斗篷与屏蔽规则设置">
              <el-row>
                <el-col>
                  <!-- <el-form-item :prop="form.cloakInfos">
                    <el-tag
                      v-for="cloak in form.cloakInfos"
                      :key="cloak.name"
                      closable
                      :disable-transitions="false"
                      size="large"
                      style="margin-right: 10px; cursor: pointer"
                      @click="handleEditCloak(cloak)"
                      @close="handleDeleteCloak(cloak)"
                    >
                      {{ cloak.name }}
                    </el-tag>
                    <el-button class="button-new-cloak" @click="handleAddCloak">
                      + 添加斗篷规则
                    </el-button>
                  </el-form-item> -->
                  <el-form-item label="爬虫" prop="botShowSpuId">
                    <el-select
                      v-model="form.botShowSpuId"
                      clearable
                      filterable
                      :loading="spuLoading"
                      placeholder="未配置默认返回当前落地页"
                      remote
                      :remote-method="remoteQuerySpu"
                      style="width: 100%"
                    >
                      <el-option
                        v-for="item in spuOptions"
                        :key="item.id"
                        :label="item.name"
                        :value="item.id"
                      >
                        <span style="float: left">{{ item.name }}</span>
                        <span
                          style="
                            float: right;
                            font-size: 13px;
                            color: var(--el-text-color-secondary);
                          "
                        >
                          {{ item.code }}
                        </span>
                      </el-option>
                    </el-select>
                  </el-form-item>
                  <el-form-item label="风险用户" prop="riskUserShowSpuId">
                    <el-select
                      v-model="form.riskUserShowSpuId"
                      clearable
                      filterable
                      :loading="spuLoading"
                      placeholder="未配置默认显示爬虫落地页，未设置爬虫落地页则显示店铺已关闭"
                      remote
                      :remote-method="remoteQuerySpu"
                      style="width: 100%"
                    >
                      <el-option
                        v-for="item in spuOptions"
                        :key="item.id"
                        :label="item.name"
                        :value="item.id"
                      >
                        <span style="float: left">{{ item.name }}</span>
                        <span
                          style="
                            float: right;
                            font-size: 13px;
                            color: var(--el-text-color-secondary);
                          "
                        >
                          {{ item.code }}
                        </span>
                      </el-option>
                    </el-select>
                  </el-form-item>
                  <el-form-item label="黑名单用户" prop="blacklistedUserShowSpuId">
                    <el-select
                      v-model="form.blacklistedUserShowSpuId"
                      clearable
                      filterable
                      :loading="spuLoading"
                      placeholder="未配置默认显示店铺已关闭"
                      remote
                      :remote-method="remoteQuerySpu"
                      style="width: 100%"
                    >
                      <el-option
                        v-for="item in spuOptions"
                        :key="item.id"
                        :label="item.name"
                        :value="item.id"
                      >
                        <span style="float: left">{{ item.name }}</span>
                        <span
                          style="
                            float: right;
                            font-size: 13px;
                            color: var(--el-text-color-secondary);
                          "
                        >
                          {{ item.code }}
                        </span>
                      </el-option>
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
            </el-card>
          </el-col>
          <el-col class="affix-container" :span="6">
            <el-affix :offset="10" target=".affix-container">
              <el-card>
                <template #header>
                  <div class="card-header">
                    <span>商品设置</span>
                    <el-link style="float: right" underline="never">
                      <el-icon class="el-icon--right"><icon-view /></el-icon>
                    </el-link>
                  </div>
                </template>
                <el-form-item>
                  <div style="width: 100%">
                    商品上架
                    <el-switch v-model="form.isAvailable" style="float: right" />
                  </div>
                </el-form-item>
                <el-form-item label="国家" prop="countryId">
                  <el-select
                    v-model="form.countryId"
                    filterable
                    :loading="countryLoading"
                    remote
                    :remote-method="remoteQueryCountry"
                    style="width: 100%"
                    @change="onSelectCountry"
                  >
                    <el-option
                      v-for="item in countryOptions"
                      :key="item.id"
                      :disabled="item.disabled"
                      :label="item.name"
                      :value="item.id"
                    >
                      <span style="float: left">{{ item.name }}</span>
                      <span
                        style="float: right; font-size: 13px; color: var(--el-text-color-secondary)"
                      >
                        {{ item.code }}
                      </span>
                    </el-option>
                  </el-select>
                </el-form-item>
                <el-form-item label="语言" prop="languageId">
                  <el-select
                    v-model="form.languageId"
                    filterable
                    :loading="languageLoading"
                    style="width: 100%"
                    @change="onSelectLanguage"
                  >
                    <el-option
                      v-for="item in languageOptions"
                      :key="item.id"
                      :disabled="item.disabled"
                      :label="item.cname"
                      :value="item.id"
                    >
                      <span style="float: left">{{ item.cname }}({{ item.name }})</span>
                      <span
                        style="float: right; font-size: 13px; color: var(--el-text-color-secondary)"
                      >
                        {{ item.code }}
                      </span>
                    </el-option>
                  </el-select>
                </el-form-item>
                <el-form-item label="货币" prop="currency">
                  <el-select
                    v-model="form.currencyId"
                    disabled
                    filterable
                    :loading="currencyLoading"
                    remote
                    :remote-method="remoteQueryCurrency"
                    style="width: 100%"
                    @change="onSelectCurrency"
                  >
                    <el-option
                      v-for="item in currencyOptions"
                      :key="item.id"
                      :label="item.name"
                      :value="item.id"
                    >
                      <span style="float: left">{{ item.name }}</span>
                      <span
                        style="float: right; font-size: 13px; color: var(--el-text-color-secondary)"
                      >
                        {{ item.code }}
                      </span>
                    </el-option>
                  </el-select>
                </el-form-item>
                <el-form-item label="SPU">
                  <el-select
                    v-model="form.spuId"
                    clearable
                    disabled
                    filterable
                    :loading="spuLoading"
                    remote
                    :remote-method="remoteQuerySpu"
                    style="width: 100%"
                    @change="onSelectSpu"
                  >
                    <el-option
                      v-for="item in spuOptions"
                      :key="item.id"
                      :label="getSpuOptionLabel(item)"
                      :value="item.id"
                    >
                      <span style="float: left">{{ item.name }}</span>
                      <span
                        style="float: right; font-size: 13px; color: var(--el-text-color-secondary)"
                      >
                        {{ item.code }}
                      </span>
                    </el-option>
                  </el-select>
                </el-form-item>
                <el-form-item label="草稿">
                  <div class="draft-actions">
                    <el-button class="draft-action-button" @click="openDraftManager">草稿管理</el-button>
                    <el-button
                      v-if="hasCurrentDraft"
                      class="draft-action-button"
                      type="warning"
                      @click="clearCurrentDraft"
                    >
                      清除草稿
                    </el-button>
                  </div>
                </el-form-item>
              </el-card>
            </el-affix>

            <el-card header="主题模板">
              <el-form-item label="商城主题">
                <el-select v-model="form.themeId" placeholder="请选择主题">
                  <el-option
                    v-for="item in themeOptions"
                    :key="item.id"
                    :label="item.name"
                    :value="item.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="落地页模板">
                <el-select v-model="form.templateId" placeholder="请选择模板">
                  <el-option
                    v-for="item in templateOptions"
                    :key="item.id"
                    :label="item.name"
                    :value="item.id"
                  />
                </el-select>
              </el-form-item>
              <p class="uploadTipDesc">选择一个默认的店铺主题和模板文件来定义页面的样式</p>
            </el-card>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button
          type="primary"
          @click="cancelEdit"
        >
          取消
        </el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </vab-dialog>
    <file-chooser ref="fileChooserRef" :z-index="5000" />
    <spec-edit ref="specEditRef" :language-id="form.languageId" @update-specifications="updateSpecifications" />
    <cloak-info-edit ref="cloakInfoEditRef" @update-cloak-infos="handleUpdateCloakInfos" />
    <vab-dialog v-model="draftManagerVisible" title="产品草稿管理" width="760px">
      <el-table v-loading="draftListLoading" :data="draftList" empty-text="暂无草稿">
        <el-table-column label="商品" min-width="220">
          <template #default="{ row }">
            <div>{{ row.title || '未命名草稿' }}</div>
            <el-text size="small" type="info">SPU: {{ row.spuId || '-' }}</el-text>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            {{ getDraftModeLabel(row.mode) }}
          </template>
        </el-table-column>
        <el-table-column label="最近保存" width="180">
          <template #default="{ row }">
            {{ formatDraftTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column align="right" label="操作" width="150">
          <template #default="{ row }">
            <el-button text type="primary" @click="openDraft(row)">打开</el-button>
            <el-button text type="danger" @click="deleteDraft(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </vab-dialog>
  </div>
</template>

<script lang="ts" setup>
import { Delete, Edit, View as IconView, Plus } from '@element-plus/icons-vue'
import Decimal from 'decimal.js'
import { ElMessageBox } from 'element-plus'
import type { AutocompleteFetchSuggestionsCallback } from 'element-plus'
import { onBeforeUnmount, toRaw } from 'vue'
import { VueDraggable } from 'vue-draggable-plus'
import { getRecommendCurrencyByLanguage, getRemoteQueryCurrency } from '~/src/api/currency'
import { useUserStore } from '~/src/store/modules/user'
import {
  deleteProductEditDraft,
  getProductEditDraft,
  listProductEditDrafts,
  saveProductEditDraft,
} from '~/src/utils/productEditDraft'
import type { ProductEditDraftRecord } from '~/src/utils/productEditDraft'
import { patternAmount, patternPositive, patternTaxationMethod } from '~/src/utils/patterns'
import { getRemoteQuery as getCountryRemoteQuery } from '/@/api/country'
import { doEdit, getRemoteQueryMerchandise } from '/@/api/product'
import { getRemoteQuery } from '/@/api/sku'
import { getRemoteQuery as getSpuRemoteQuery } from '/@/api/spu'

export interface SpecificationValuesType {
  value: string
  image:
    | {
        id: string
        name: string
        suffix: string
        mediaType: string
        relativePath: string
        absolutionPath: string
      }
    | undefined
}
export interface SpecificationType {
  name: string
  values: SpecificationValuesType[]
}

export interface SpecType {
  name: string
  value: string
  image:
    | {
        id: string
        name: string
        suffix: string
        mediaType: string
        relativePath: string
        absolutionPath: string
      }
    | undefined
}

export interface SkuType {
  sid: number
  attributes: Array<SpecType>
  deleted: boolean
  skuImage: string
  sellPrice: string
  originPrice: string
  costPrice: string
  sellPriceCurrency: string
  originPriceCurrency: string
  costPriceCurrency: string
  isTaxable: boolean
  taxationMethod: string
  fixedTaxAmount: string
  taxAmountThreshold: string
  taxQuantityThreshold: string
  taxPerBase: string
  barcode: string
  stockQuantity: number
  skuCode: string
  skuName: string
  linkStock: boolean
}
const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const spu = ref<any>(null)
const formRef = ref<any>(null)
const tableRef = ref<any>(null)
const specEditRef = ref<any>(null)
const fileChooserRef = ref<any>(null)
const cloakInfoEditRef = ref<any>(null)
const specifications = ref<SpecificationType[]>([{ name: 'Color', values: [] }])
const isSpecEdit = ref<boolean>(false)
const title = ref<string>('')
const isEdit = ref<boolean>(false)
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const skuLoading = ref<boolean>(false)
const spuLoading = ref<boolean>(false)
const countryLoading = ref<boolean>(false)
const currencyLoading = ref<boolean>(false)
const languageLoading = ref<boolean>(false)
const merchandiseLoading = ref<boolean>(false)
const skuOptions = ref<any>([])
const merchandiseOptions = ref<any>([])
const languageOptions = ref<any>([])
const currencyOptions = ref<any>([])
const spuOptions = ref<any>([])
const countryOptions = ref<any>([])
const themeOptions = ref<any>([{ id: 0, name: '默认主题' }])
const templateOptions = ref<any>([{ id: 0, name: '默认模板' }])
const specNameOptions = ['Color', 'Size', 'Material', 'Style', 'Model']
const specificationValues = ref<{ [key: string]: string[] | undefined }>({})
const userStore = useUserStore()
const batchSelectSpec = ref<any>([])
const DRAFT_LIMIT = 10
const DRAFT_SAVE_DELAY = 800
const MAX_SKU_IMAGE_COUNT = 10
const currentDraftKey = ref<string>('')
const currentDraftMode = ref<'new' | 'edit' | 'copy'>('new')
const currentDraftSourceProductId = ref<string | number | undefined>()
const currentOriginalState = ref<any>(null)
const hasCurrentDraft = ref<boolean>(false)
const draftManagerVisible = ref<boolean>(false)
const draftListLoading = ref<boolean>(false)
const draftList = ref<ProductEditDraftRecord[]>([])
const draftSavePaused = ref<boolean>(true)
const draftSaveReady = ref<boolean>(false)
const draftSaveFailedNotified = ref<boolean>(false)
let draftSaveTimer: ReturnType<typeof setTimeout> | undefined

const specWidth = computed(() => {
  const widthList = form.specifications.map((sku: SkuType) => {
    const text = sku.attributes.map((item: SpecType) => item.value).join('·')
    return getTextWidth(text)
  })
  if (Math.max(...widthList) + 28 > 250) {
    return '250px'
  }
  return `${Math.max(...widthList) + 28}px`
})

const standardCurrency = ref<any>({
  name: '美元',
  symbol: '$',
  code: 'USD',
  exchangeRate: '1',
})
const currency = ref<any>({
  name: '美元',
  symbol: '$',
  code: 'USD',
  exchangeRate: '1',
})
const form = reactive<any>({
  title: '',
  summary: '',
  introduction: '',
  waybillProductName: '',
  skuImages: [],
  alternativeSkuCodes: [],
  sellPrice: '0.00',
  originPrice: '0.00',
  costPrice: '0.00',
  sellPriceCurrency: '0.00',
  originPriceCurrency: '0.00',
  costPriceCurrency: '0.00',
  isTaxable: false,
  taxationMethod: 'FIXED',
  fixedTaxAmount: '0.00',
  fixedTaxAmountCurrency: '0.00',
  taxAmountThreshold: '0.00',
  taxAmountThresholdCurrency: '0.00',
  taxQuantityThreshold: '1',
  taxPerBase: '0.00',
  taxPerBaseCurrency: '0.00',
  barcode: '',
  stockQuantity: -1,
  skuCode: '',
  skuName: '',
  linkStock: true,
  isMultiSpecs: false,
  isAvailable: false,
  specifications: [] as SkuType[],
  spuName: '',
  spuCode: '',
  spuId: undefined,
  themeId: 0,
  countryId: '',
  templateId: 0,
  languageId: '',
  skuVideoId: '',
  cloakInfos: [],
  blacklistedUserShowSpuId: '',
  riskUserShowSpuId: '',
  botShowSpuId: '',
})
const isValidSkuImage = (image: any) => {
  return (
    image !== null &&
    typeof image === 'object' &&
    (Boolean(image.id) || Boolean(image.absolutionPath))
  )
}

const normalizeSkuImages = (images: any) => {
  if (!Array.isArray(images)) {
    return []
  }
  return images.filter(isValidSkuImage).slice(0, MAX_SKU_IMAGE_COUNT)
}

const normalizeChosenFiles = (files: any) => {
  if (!Array.isArray(files) || files.length === 0) {
    return []
  }
  return normalizeSkuImages(files)
}

const validSkuImages = computed(() => {
  if (!Array.isArray(form.skuImages)) {
    return []
  }
  return form.skuImages
    .map((image: any, originalIndex: number) => ({ image, originalIndex }))
    .filter(({ image }: any) => isValidSkuImage(image))
    .slice(0, MAX_SKU_IMAGE_COUNT)
})

const rules = reactive<any>({
  title: [{ required: true, trigger: 'blur', message: '请输入商品标题' }],
  // summary: [{ required: true, trigger: 'blur', message: '请输入商品摘要' }],
  introduction: [
    { required: true, trigger: 'change', message: '请输入商品详情' },
    { required: true, trigger: 'blur', message: '请输入商品详情' },
  ],
  sellPrice: [
    { type: 'string', required: true, pattern: patternAmount, message: '请输入正确的商品售价' },
  ],
  originPrice: [
    { type: 'string', required: true, pattern: patternAmount, message: '请输入正确的商品原价' },
  ],
  // costPrice: [{ type: 'string', required: true, pattern: patternAmount, message: '请输入正确的商品成本价' }],
  taxationMethod: [
    {
      type: 'string',
      required: true,
      pattern: patternTaxationMethod,
      message: '请选择正确的税费收取方式',
    },
  ],
  fixedTaxAmount: [
    { type: 'string', required: true, pattern: patternAmount, message: '请选择正确的税费收取方式' },
  ],
  taxAmountThreshold: [
    {
      type: 'string',
      required: true,
      pattern: patternAmount,
      message: '请输入正确的税费收取条件(按金额收取)',
    },
  ],
  taxQuantityThreshold: [
    {
      type: 'string',
      required: true,
      pattern: patternPositive,
      message: '请输入正确的税费收取条件(按购买量收取)',
    },
  ],
  taxPerBase: [
    { type: 'string', required: true, pattern: patternAmount, message: '请输入正确的税费收取金额' },
  ],
  skuCode: [{ required: true, trigger: 'change', message: '请输入商品SKU' }],
  skuName: [{ required: true, trigger: 'change', message: '请输入商品品名' }],
  languageId: [{ required: true, trigger: 'change', message: '请选择语言' }],
})
const skuSpecifications = computed(() => {
  return form.specifications.filter((item: SkuType) => !item.deleted)
})
const profit = computed(() => {
  try {
    const sell = new Decimal(form.sellPrice)
    const cost = new Decimal(form.costPrice)

    if (sell.isNaN() || cost.isNaN()) {
      return '0.00'
    }
    return sell.minus(cost).toString()
  } catch {
    return '0.00'
  }
})

// 定义计算属性来计算利润率
const profitMargin = computed(() => {
  try {
    const sell = new Decimal(form.sellPrice)
    const cost = new Decimal(form.costPrice)
    if (sell.isNaN() || cost.isNaN() || cost.equals(0)) {
      return '0.00%'
    }
    return `${sell.minus(cost).dividedBy(cost).mul(100).toFixed(4)}%`
  } catch {
    return '0.00'
  }
})

const cloneDraftValue = (value: any) => JSON.parse(JSON.stringify(toRaw(value ?? null)))

const getDraftUserKey = () => userStore.getUsername || userStore.getDisplayName || 'guest'

const getDraftModeLabel = (mode: string) => {
  if (mode === 'edit') return '编辑'
  if (mode === 'copy') return '复制'
  return '新增'
}

const formatDraftTime = (timestamp: number) => {
  if (!timestamp) return '-'
  const date = new Date(timestamp)
  const pad = (value: number) => `${value}`.padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const buildDraftKey = (product: any, spuItem: any, sourceProductId?: string | number) => {
  const userKey = getDraftUserKey()
  const spuId = spuItem?.id || product?.spuId || product?.spu?.id || 'unknown'
  if (sourceProductId) {
    currentDraftMode.value = 'copy'
    currentDraftSourceProductId.value = sourceProductId
    return `product-edit:${userKey}:copy:${spuId}:${sourceProductId}`
  }
  if (product?.id) {
    currentDraftMode.value = 'edit'
    currentDraftSourceProductId.value = undefined
    return `product-edit:${userKey}:edit:${spuId}:${product.id}`
  }
  currentDraftMode.value = 'new'
  currentDraftSourceProductId.value = undefined
  return `product-edit:${userKey}:new:${spuId}`
}

const getSpuOptionCode = (item: any) => item?.code || item?.spuCode || ''

const getSpuOptionLabel = (item: any) => {
  const code = getSpuOptionCode(item)
  const name = item?.name || item?.spuName || ''
  const id = item?.id || ''
  return [code, name, id].filter((value) => value !== '').join(' - ')
}

const buildSpuOption = (item: any, fallback: any = {}) => ({
  id: item?.id || fallback?.id,
  name: item?.name || item?.spuName || fallback?.name || fallback?.spuName,
  code: item?.code || item?.spuCode || fallback?.code || fallback?.spuCode || '',
})

const appendSpuOption = (item: any) => {
  const option = buildSpuOption(item)
  if (!option.id) return
  const existing = spuOptions.value.find((spuOption: any) => String(spuOption.id) === String(option.id))
  if (existing) {
    existing.name = existing.name || option.name
    existing.code = existing.code || option.code
    return
  }
  spuOptions.value = [...spuOptions.value, option]
}

const createDraftPayload = () => {
  const draftForm = cloneDraftValue(form)
  draftForm.skuImages = normalizeSkuImages(draftForm.skuImages)
  return {
    spu: cloneDraftValue(spu.value),
    form: draftForm,
    specifications: cloneDraftValue(specifications.value),
    specificationValues: cloneDraftValue(specificationValues.value),
    skuOptions: cloneDraftValue(skuOptions.value),
    spuOptions: cloneDraftValue(spuOptions.value),
    countryOptions: cloneDraftValue(countryOptions.value),
    languageOptions: cloneDraftValue(languageOptions.value),
    currencyOptions: cloneDraftValue(currencyOptions.value),
    merchandiseOptions: cloneDraftValue(merchandiseOptions.value),
  }
}

const applyDraftPayload = (payload: any) => {
  if (!payload) return
  spu.value = payload.spu || null
  Object.assign(form, payload.form || {})
  form.skuImages = normalizeSkuImages(form.skuImages)
  specifications.value = payload.specifications || [{ name: 'Color', values: [] }]
  specificationValues.value = payload.specificationValues || {}
  skuOptions.value = payload.skuOptions || []
  spuOptions.value = payload.spuOptions || []
  countryOptions.value = payload.countryOptions || []
  languageOptions.value = payload.languageOptions || []
  currencyOptions.value = payload.currencyOptions || []
  merchandiseOptions.value = payload.merchandiseOptions || []
}

const saveDraftNow = async () => {
  if (!dialogFormVisible.value || draftSavePaused.value || !draftSaveReady.value || !currentDraftKey.value) {
    return
  }
  if (draftSaveTimer) {
    clearTimeout(draftSaveTimer)
    draftSaveTimer = undefined
  }
  try {
    await saveProductEditDraft(
      {
        draftKey: currentDraftKey.value,
        userKey: getDraftUserKey(),
        title: form.title || '',
        spuId: form.spuId,
        productId: currentDraftMode.value === 'edit' ? form.id : currentDraftSourceProductId.value,
        mode: currentDraftMode.value,
        updatedAt: Date.now(),
        payload: createDraftPayload(),
      },
      DRAFT_LIMIT
    )
    hasCurrentDraft.value = true
  } catch (error) {
    console.error('save product draft failed', error)
    if (!draftSaveFailedNotified.value) {
      draftSaveFailedNotified.value = true
      $baseMessage('草稿保存失败，请及时手动保存', 'warning', 'hey')
    }
  }
}

const scheduleDraftSave = () => {
  if (!dialogFormVisible.value || draftSavePaused.value || !draftSaveReady.value || !currentDraftKey.value) {
    return
  }
  if (draftSaveTimer) {
    clearTimeout(draftSaveTimer)
  }
  draftSaveTimer = setTimeout(() => {
    saveDraftNow()
  }, DRAFT_SAVE_DELAY)
}

const loadCurrentDraft = async () => {
  if (!currentDraftKey.value) return
  try {
    const draft = await getProductEditDraft(currentDraftKey.value)
    hasCurrentDraft.value = !!draft
    if (draft) {
      applyDraftPayload(draft.payload)
    }
  } catch (error) {
    console.error('load product draft failed', error)
  }
}

const clearCurrentDraft = async () => {
  if (!currentDraftKey.value) return
  draftSavePaused.value = true
  if (draftSaveTimer) {
    clearTimeout(draftSaveTimer)
    draftSaveTimer = undefined
  }
  try {
    await deleteProductEditDraft(currentDraftKey.value)
    hasCurrentDraft.value = false
    applyDraftPayload(currentOriginalState.value)
    await $baseMessage('草稿已清除', 'success', 'hey')
  } finally {
    nextTick(() => {
      draftSavePaused.value = false
    })
  }
}

const refreshDraftList = async () => {
  draftListLoading.value = true
  try {
    draftList.value = await listProductEditDrafts(getDraftUserKey())
  } finally {
    draftListLoading.value = false
  }
}

const openDraftManager = async () => {
  draftManagerVisible.value = true
  await refreshDraftList()
}

const openDraft = async (draft: ProductEditDraftRecord) => {
  if (dialogFormVisible.value && currentDraftKey.value && currentDraftKey.value !== draft.draftKey) {
    await ElMessageBox.confirm('当前编辑内容将切换为所选草稿，是否继续？', '提示', {
      type: 'warning',
    })
  }
  draftSavePaused.value = true
  currentDraftKey.value = draft.draftKey
  currentDraftMode.value = draft.mode
  currentDraftSourceProductId.value = draft.mode === 'copy' ? draft.productId : undefined
  title.value = getDraftModeLabel(draft.mode)
  isEdit.value = draft.mode === 'edit'
  applyDraftPayload(draft.payload)
  currentOriginalState.value = cloneDraftValue(draft.payload)
  hasCurrentDraft.value = true
  dialogFormVisible.value = true
  draftManagerVisible.value = false
  nextTick(() => {
    draftSaveReady.value = true
    draftSavePaused.value = false
  })
}

const deleteDraft = async (draft: ProductEditDraftRecord) => {
  await deleteProductEditDraft(draft.draftKey)
  if (currentDraftKey.value === draft.draftKey) {
    hasCurrentDraft.value = false
  }
  await refreshDraftList()
}

const showEdit = (product: any, spuItem: any = null, options: { sourceProductId?: string | number } = {}) => {
  draftSavePaused.value = true
  draftSaveReady.value = false
  hasCurrentDraft.value = false
  currentDraftKey.value = buildDraftKey(product, spuItem, options.sourceProductId)
  spu.value = spuItem
  dialogFormVisible.value = true
  if (product && product.language) {
    form.languageId = product.language.id
    spu.value.languageId = product.language.id
    languageOptions.value = [product.language]
  }
  if (product && product.country) {
    form.countryId = product.country.id
    spu.value.countryId = product.country.id
    countryOptions.value = [product.country]
  }
  if (product && product.sku) {
    skuOptions.value = [product.sku]
    form.skuId = product.sku.skuId
    form.skuCode = product.sku.skuCode
    form.skuName = product.sku.name
  }
  if (product && product.specifications) {
    specifications.value = convertToSpecificationType(product.specifications)
  }
  spuOptions.value = [
    buildSpuOption(spuItem, product?.spu || product?.botShowSpu || product?.riskUserShowSpu || product?.blacklistedShowSpu),
  ]
  if (product && product.botShowSpu) {
    form.botShowSpuId = product.botShowSpu.id
    appendSpuOption(product.botShowSpu)
  }
  if (product && product.riskUserShowSpu) {
    form.riskUserShowSpuId = product.riskUserShowSpu.id
    appendSpuOption(product.riskUserShowSpu)
  }
  if (product && product.blacklistedShowSpu) {
    form.blacklistedUserShowSpuId = product.blacklistedShowSpu.id
    appendSpuOption(product.blacklistedShowSpu)
  }
  form.spuId = spuItem.id

  const websiteCurrency = userStore.getCurrency
  if (websiteCurrency) {
    currency.value = websiteCurrency
    currencyOptions.value = [currency.value]
    form.currencyId = currency.value
  }

  nextTick(async () => {
    isEdit.value = false
    if (!product || !product.title) title.value = '添加'
    else {
      isEdit.value = true
      title.value = product.language ? '编辑' : '复制'
      Object.assign(form, product)
      form.skuImages = normalizeSkuImages(form.skuImages)
      form.introduction = product.introduction
      merchandiseOptions.value = [product.merchandise]
      form.merchandise = product.merchandise
      form.specifications.forEach((spec: SkuType, index: number) => {
        spec.deleted = false
        if (spec.sid === undefined) {
          spec.sid = index
        }
      })
      onSelectCurrency(-1)
      if (product.language) {
        onSelectLanguage(product.language.id)
      }
    }
    currentOriginalState.value = createDraftPayload()
    await loadCurrentDraft()
    draftSaveReady.value = true
    draftSavePaused.value = false
  })
}

const convertToSpecificationType = (specifications: any): SpecificationType[] => {
  const resultMap = new Map<string, SpecificationType>()
  let newSpecValues: { [key: string]: string[] | undefined } = {}
  for (const spec of specifications) {
    for (const attr of spec.attributes) {
      const { name, value, image } = attr

      if (resultMap.has(name)) {
        const specType = resultMap.get(name)!
        const existingValues = new Set(specType.values.map((v) => v.value))
        if (!existingValues.has(value)) {
          specType.values.push({ value, image })
        }

        newSpecValues[name] = [...new Set([...(newSpecValues[name] ?? []), value])]
      } else {
        resultMap.set(name, {
          name,
          values: [{ value, image }],
        })
        newSpecValues[name] = [value]
      }
    }
  }
  specificationValues.value = newSpecValues

  console.log('spec values', newSpecValues)
  return Array.from(resultMap.values())
}

defineExpose({
  showEdit,
})

const cancelEdit = async () => {
  await saveDraftNow()
  dialogFormVisible.value = false
  close()
}

const close = () => {
  draftSavePaused.value = true
  draftSaveReady.value = false
  if (draftSaveTimer) {
    clearTimeout(draftSaveTimer)
    draftSaveTimer = undefined
  }
  formRef.value.clearValidate()
  formRef.value.resetFields()
  Object.assign(form, {
    id: undefined,
    title: '',
    summary: '',
    introduction: '',
    skuImages: [],
    sellPrice: '0.00',
    originPrice: '0.00',
    costPrice: '0.00',
    sellPriceCurrency: '0.00',
    originPriceCurrency: '0.00',
    costPriceCurrency: '0.00',
    isTaxable: false,
    taxationMethod: 'FIXED',
    fixedTaxAmount: '0.00',
    fixedTaxAmountCurrency: '0.00',
    taxAmountThreshold: '0.00',
    taxAmountThresholdCurrency: '0.00',
    taxQuantityThreshold: '1',
    taxPerBase: '0.00',
    taxPerBaseCurrency: '0.00',
    barcode: '',
    stockQuantity: -1,
    skuCode: '',
    skuName: '',
    linkStock: true,
    isMultiSpecs: false,
    isAvailable: false,
    specifications: [] as SkuType[],
    spuName: '',
    spuCode: '',
    spuId: undefined,
    themeId: 0,
    templateId: 0,
    languageId: '',
    skuVideoId: '',
    cloakInfos: [],
    blacklistedUserShowSpuId: '',
    riskUserShowSpuId: '',
    botShowSpuId: '',
  })
  specifications.value = [{ name: 'Color', values: [] }]
  specificationValues.value = {}
  currentDraftKey.value = ''
  currentOriginalState.value = null
  hasCurrentDraft.value = false
  emit('fetch-data')
}

const chooseSkuImage = async (row: any) => {
  const images = normalizeChosenFiles(await fileChooserRef.value.choose())
  if (images.length === 0) {
    return
  }
  row.skuImage = images[0]
  console.log(row.skuImage)
}

const onBatchEdit = (modifiedProps: any) => {
  tableRef.value.getSelectionRows().forEach((row: any) => {
    if (modifiedProps.skuId !== undefined) row.skuId = modifiedProps.skuId
    if (modifiedProps.skuCode !== undefined) row.skuCode = modifiedProps.skuCode
    if (modifiedProps.skuName !== undefined) row.skuName = modifiedProps.skuName
    if (modifiedProps.skuImage !== undefined) row.skuImage = modifiedProps.skuImage
    if (modifiedProps.sellPrice !== undefined) row.sellPrice = modifiedProps.sellPrice
    if (modifiedProps.sellPriceCurrency !== undefined)
      row.sellPriceCurrency = modifiedProps.sellPriceCurrency
    if (modifiedProps.originPrice !== undefined) row.originPrice = modifiedProps.originPrice
    if (modifiedProps.originPriceCurrency !== undefined)
      row.originPriceCurrency = modifiedProps.originPriceCurrency
    if (modifiedProps.costPrice !== undefined) row.costPrice = modifiedProps.costPrice
    if (modifiedProps.costPriceCurrency !== undefined)
      row.costPriceCurrency = modifiedProps.costPriceCurrency
    if (modifiedProps.linkStock !== undefined) row.linkStock = modifiedProps.linkStock
    if (modifiedProps.stockQuantity !== undefined) row.stockQuantity = modifiedProps.stockQuantity
    if (modifiedProps.barcode !== undefined) row.barcode = modifiedProps.barcode
  })
}

const chooseFormSkuVideo = async () => {
  const videos = normalizeChosenFiles(await fileChooserRef.value.choose())
  if (videos.length === 0) {
    return
  }
  form.skuVideo = videos[0]
  form.skuVideoId = videos[0].id
  console.log(form.skuVideo)
}

const chooseFormSkuImage = async (index: number) => {
  const images = normalizeChosenFiles(await fileChooserRef.value.choose())
  if (images.length === 0) {
    return
  }
  console.log(`choose from sku image: ${index}, images = ${JSON.stringify(images)}`)
  if (index === 100) {
    form.skuImages = normalizeSkuImages([...normalizeSkuImages(form.skuImages), ...images])
  } else {
    form.skuImages[index] = images[0]
    form.skuImages = normalizeSkuImages(form.skuImages)
  }
}

const showDeleteButton = (index: number) => {
  if (isValidSkuImage(form.skuImages[index])) {
    form.skuImages[index].showDelete = true
  }
}

const hideDeleteButton = (index: number) => {
  if (isValidSkuImage(form.skuImages[index])) {
    form.skuImages[index].showDelete = false
  }
}

const deleteImage = (index: number) => {
  form.skuImages.splice(index, 1)
  form.skuImages = normalizeSkuImages(form.skuImages)
}

const onApplySkuImage = (index: number, translated: any) => {
  if (translated && translated.id && isValidSkuImage(form.skuImages[index])) {
    form.skuImages[index] = { ...form.skuImages[index], ...translated }
    form.skuImages = normalizeSkuImages(form.skuImages)
  }
}

const save = () => {
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      try {
        saveLoading.value = true
        form.skuImages = normalizeSkuImages(form.skuImages)
        form.skuImageIds = form.skuImages.map((skuImage: any) => skuImage.id).filter(Boolean)
        form.specifications = skuSpecifications.value
        form.specifications.forEach((spec: any) => {
          spec.specificationImageId = spec.skuImage ? spec.skuImage.id : null
        })
        const { msg }: any = await doEdit(form)
        if (currentDraftKey.value) {
          await deleteProductEditDraft(currentDraftKey.value)
          hasCurrentDraft.value = false
        }
        await $baseMessage(msg, 'success', 'hey')
        dialogFormVisible.value = false
        close()
      } finally {
        saveLoading.value = false
      }
    }
  })
}
const isSpecSelected = (name: string) => {
  const names = specifications.value.map((item: any) => item.name)
  return names.includes(name)
}
const addSpec = () => {
  const names = new Set(specifications.value.map((item: any) => item.name))
  const skuNames = specNameOptions.filter((item: string) => !names.has(item))
  console.log(skuNames)
  let skuName = ''
  if (skuNames && skuNames.length > 0) {
    skuName = skuNames[0]
  }
  specifications.value.push({
    name: skuName,
    values: [],
  })
}

const removeSpec = (spec: { name: string }) => {
  specifications.value = specifications.value.filter(
    (item: { name: string }) => item.name !== spec.name
  )
}

const editSpec = (spec: { name: string }) => {
  console.log(spec)
  console.log(specifications.value)
  isSpecEdit.value = true
  specEditRef.value.showEdit(spec)
}

const updateSpecifications = (spec: any) => {
  console.log(spec)
  console.log(specifications.value)
  for (const s of specifications.value) {
    if (s.name === spec.name) {
      s.values = spec.values
      specificationValues.value[s.name] = spec.values.map((v: any) => v.value)
      break
    }
  }
  console.log(specifications.value)

  isSpecEdit.value = false
}
const hadSelectedSpec = (): boolean => {
  console.log(tableRef.value.getSelectionRows())
  return tableRef.value.getSelectionRows().length > 0
}

const remoteQuerySku = async (query: string) => {
  skuLoading.value = true
  try {
    const { data } = await getRemoteQuery(query)
    skuOptions.value = data.list
  } finally {
    skuLoading.value = false
  }
}
const remoteQueryMerchandise = (queryString: string, cb: AutocompleteFetchSuggestionsCallback) => {
  merchandiseLoading.value = true
  getRemoteQueryMerchandise(queryString)
    .then((res) => {
      console.log(res.data.list)
      cb(
        res.data.list.map((item: string) => {
          return { value: item }
        })
      )
      merchandiseLoading.value = false
    })
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    .catch((error) => {
      cb([])
      merchandiseLoading.value = false
    })
}

const onSelectSku = (skuCode: string) => {
  const skuList = skuOptions.value.filter((c: any) => c.skuCode === skuCode)
  console.log(`id = ${skuCode} >> ${JSON.stringify(skuList)}`)
  if (skuList && skuList.length > 0) {
    const sku = skuList[0]
    form.skuId = sku.id
    form.skuName = sku.name
    form.skuCode = sku.skuCode
  } else {
    form.skuId = null
    form.skuName = ''
    form.skuCode = skuCode
  }
}

const onSelectRowSku = (row: any, skuCode: any) => {
  console.log(`${JSON.stringify(row)}pre`)
  console.log(JSON.stringify(skuCode))
  const skuList = skuOptions.value.filter((c: any) => c.skuCode === skuCode)
  console.log(`id = ${skuCode} >> ${JSON.stringify(skuList)}`)
  if (skuList && skuList.length > 0) {
    const sku = skuList[0]
    row.skuId = sku.id
    row.skuName = sku.name
    row.skuCode = sku.skuCode
  } else {
    row.skuId = null
    row.skuName = ''
    row.skuCode = skuCode
  }
}

watch(
  specifications,
  (newValue: SpecificationType[]) => {
    console.log(`specifications = ${JSON.stringify(newValue)}`)
    const specs = newValue.filter(
      (item: SpecificationType) => item.values && item.values.length > 0
    )
    if (specs.length === 0) {
      form.specifications = []
      return
    }
    let hasString = false
    let newSpecs: SpecificationType[] = []
    for (const spec of specs) {
      const { name, values } = spec
      const normalizedValues: SpecificationValuesType[] = []

      for (const val of values) {
        if (typeof val === 'string') {
          hasString = true
          const obj = { value: val, image: undefined }
          normalizedValues.push(obj)
          console.log(`Converted ${name} "${val}" to object`)
        } else {
          normalizedValues.push(val)
        }
      }
      newSpecs.push({ name, values: normalizedValues })
    }
    console.log(`hasString = ${hasString}`)
    if (hasString) {
      form.specifications = newSpecs
      return
    }
    console.log(`specs = ${JSON.stringify(specs)}`)
    const result: SkuType[] = []
    const oldFormSpecifications: SkuType[] = form.specifications
    let sid: number = 0
    const generateCombinations = (specIndex: number, currentCombination: SpecType[]) => {
      if (specIndex === specs.length) {
        const specTemp = oldFormSpecifications.filter((item: SkuType) => item.sid === sid)
        if (specTemp && specTemp.length > 0) {
          if (!specTemp[0].deleted) {
            specTemp[0].attributes = [...currentCombination]
            result.push(specTemp[0])
          }
        } else {
          result.push({
            attributes: [...currentCombination],
            deleted: false,
            sellPrice: '0.00',
            skuImage: '',
            originPrice: '0.00',
            costPrice: '0.00',
            sellPriceCurrency: '0.00',
            originPriceCurrency: '0.00',
            costPriceCurrency: '0.00',
            isTaxable: false,
            taxationMethod: 'FIXED',
            fixedTaxAmount: '0.00',
            taxAmountThreshold: '0.00',
            taxQuantityThreshold: '1',
            taxPerBase: '0.00',
            barcode: '',
            stockQuantity: -1,
            skuCode: '',
            skuName: '',
            linkStock: true,
            sid,
          })
        }
        sid++
        return
      }
      const spec: SpecificationType = specs[specIndex]
      for (let idx = 0; idx < spec.values.length; idx++) {
        const value: SpecificationValuesType = spec.values[idx]
        currentCombination.push({ name: spec.name, value: value.value, image: value.image })
        generateCombinations(specIndex + 1, currentCombination)
        currentCombination.pop()
      }
    }
    generateCombinations(0, [])
    form.specifications = result
  },
  { deep: true }
)

watch(
  [form, specifications, specificationValues],
  () => {
    scheduleDraftSave()
  },
  { deep: true }
)

onBeforeUnmount(() => {
  if (draftSaveTimer) {
    clearTimeout(draftSaveTimer)
  }
})

const getTextWidth = (text: string, font = '14px Arial') => {
  const canvas = document.createElement('canvas')
  const context = canvas.getContext('2d')
  if (context) {
    context.font = font
    return context.measureText(text).width
  }
  return 0
}

const remoteQueryCurrency = async (query: string) => {
  currencyLoading.value = true
  try {
    const { data } = await getRemoteQueryCurrency(query)
    currencyOptions.value = data.list
  } finally {
    currencyLoading.value = false
  }
}

const onSelectCurrency = (id: any) => {
  console.log(id)
  console.log(id >= 0)
  if (id >= 0) {
    currency.value = currencyOptions.value.find((item: any) => item.id === id)
  }
  form.sellPriceCurrency = exchangeToCurrency(form.sellPrice)
  form.originPriceCurrency = exchangeToCurrency(form.originPrice)
  form.costPriceCurrency = exchangeToCurrency(form.costPrice)
  form.fixedTaxAmountCurrency = exchangeToCurrency(form.fixedTaxAmount)
  form.taxAmountThresholdCurrency = exchangeToCurrency(form.taxAmountThreshold)
  form.taxPerBaseCurrency = exchangeToCurrency(form.taxPerBase)
  form.specifications.forEach((item: SkuType) => {
    item.sellPriceCurrency = exchangeToCurrency(item.sellPrice)
    item.originPriceCurrency = exchangeToCurrency(item.originPrice)
    item.costPriceCurrency = exchangeToCurrency(item.costPrice)
  })
}

const remoteQuerySpu = async (query: string) => {
  spuLoading.value = true
  try {
    const { data } = await getSpuRemoteQuery(query)
    spuOptions.value = data.list
  } finally {
    spuLoading.value = false
  }
}

const onSelectSpu = (id: any) => {
  const spuList = spuOptions.value.filter((c: any) => c.id === id)
  if (spuList && spuList.length > 0) {
    const spuItem = spuList[0]
    form.spuId = spuItem.id
  }
}

const onSelectMerchandise = () => {
  console.log('on select merchandise:')
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

const onBatchSelectSpecChange = () => {
  console.log('on batch select spec change:', batchSelectSpec.value)
  skuSpecifications.value.forEach((row: SkuType) => {
    tableRef.value.toggleRowSelection(row, shouldSelectRow(row))
  })
}

const shouldSelectRow = (row: SkuType) => {
  console.log('shouldSelectRow:', row, batchSelectSpec.value)
  let shouldSelect: boolean = true
  for (const attribute of row.attributes) {
    if (batchSelectSpec.value[attribute.name] && batchSelectSpec.value[attribute.name].length > 0) {
      shouldSelect = shouldSelect && batchSelectSpec.value[attribute.name].includes(attribute.value)
    }
  }
  return shouldSelect
}

const onChangeSpecificationName = () => {
  const specKeys = specifications.value.map((spec) => spec.name)
  const specValueKeys = Object.keys(specificationValues.value)

  // Find keys that are in specValueKeys but not in specKeys
  const keysToRemove = specValueKeys.filter((key) => !specKeys.includes(key))
  console.log('keysToRemove:', keysToRemove)
  let keysValue: string[] | undefined
  // Remove keys that are not present in specifications
  for (const key of keysToRemove) {
    keysValue = specificationValues.value[key]
    delete specificationValues.value[key]
  }

  // Add keys that are in specifications but not in specificationValues
  for (const key of specKeys) {
    if (!specValueKeys.includes(key)) {
      console.log('key:', key)
      console.log('keysValue:', keysValue)
      specificationValues.value[key] = keysValue
    }
  }
}

const onChangeSpecificationValue = () => {
  console.log('on change specification value:', specificationValues.value)
  console.log('specifications:', specifications.value)
  // Remove duplicate values in specificationValues
  for (const key in specificationValues.value) {
    const uniqueValues = [...new Set(specificationValues.value[key])]
    specificationValues.value[key] = uniqueValues
  }
  const newSpecifications: SpecificationType[] = []
  for (const [key, values] of Object.entries(specificationValues.value)) {
    if (!values) {
      continue
    }
    const newValues: SpecificationValuesType[] = []
    for (const value of values) {
      console.log(`key: ${key}, value: ${value}`)
      const spec = specifications.value.find((spec: any) => spec.name === key)
      if (spec) {
        const existingValue = spec.values.find((v: any) => v.value === value)
        if (existingValue) {
          newValues.push(existingValue)
        } else {
          newValues.push({ value, image: undefined })
        }
      }
    }
    newSpecifications.push({ name: key, values: newValues })
  }
  console.log(`newSpecifications: ${JSON.stringify(newSpecifications)}`)
  specifications.value = newSpecifications
}

const remoteQueryCountry = async (query: string) => {
  countryLoading.value = true
  try {
    const { data } = await getCountryRemoteQuery(query)
    countryOptions.value = data.list
    const ids = spu.value.countryIds
    countryOptions.value.forEach((item: any) => {
      if (ids.includes(item.id) && item.id !== spu.value.countryId) {
        item.disabled = true
      } else {
        item.disabled = false
      }
    })
    console.log('countryOptions:', countryOptions.value, spu.value)
  } finally {
    countryLoading.value = false
  }
}

const onSelectCountry = (id: any) => {
  const countries = countryOptions.value.filter((c: any) => c.id === id)
  console.log('countries:', countries)
  if (countries && countries.length > 0) {
    const country = countries[0]
    languageOptions.value = [...country.languages]
    if (languageOptions.value.length > 0) {
      form.languageId = languageOptions.value[0].id
    }
    currencyOptions.value = [country.currency]
    form.currencyId = country.currency.id
    onSelectCurrency(country.currency.id)
  }
}

/**
 * 选中语言，去获取推荐展示的货币
 */
const onSelectLanguage = async (id: string) => {
  let recommendCurrency = await getRecommendCurrencyByLanguage(id)
  currencyOptions.value = [recommendCurrency.data]
  form.currencyId = recommendCurrency.data.id
  onSelectCurrency(recommendCurrency.data.id)
}

// const handleAddCloak = () => {
//   cloakInfoEditRef.value.showEdit()
// }

// const handleEditCloak = (cloak: any) => {
//   cloakInfoEditRef.value.showEdit(cloak)
// }

// const handleDeleteCloak = (cloak: any) => {
//   form.cloakInfos = form.cloakInfos.filter((item: any) => item.name !== cloak.name)
// }

const handleUpdateCloakInfos = (cloakInfos: any) => {
  const index = form.cloakInfos.findIndex((item: any) => item.name === cloakInfos.name)
  if (index === -1) {
    form.cloakInfos.push(cloakInfos)
  } else {
    form.cloakInfos.splice(index, 1, cloakInfos)
  }
}
</script>

<style scoped>
/* 设置表格内容不换行，并根据内容自动调整宽度 */
/* .el-table th,
.el-table td {
  white-space: nowrap;
} */
.v7-shop-upload-size {
  width: 100px;
  height: 100px;
}
.uploadTipDesc {
  margin-top: 12px;
  margin-bottom: 0;
  color: #7a8499;
}

.draft-actions {
  display: flex;
  gap: 8px;
  width: 100%;
}

.draft-action-button {
  flex: 1;
  min-width: 0;
  margin-left: 0 !important;
}

.sku-code {
  float: left;
}

.sku-item-name {
  float: right;
  margin-left: 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
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

.add-image-button {
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border: 1px dashed #d9d9d9;
}

.add-image-button .el-icon {
  font-size: 28px;
  color: #999;
}
.image-item-card {
  /* margin-bottom: 10px; */
  margin-left: 8px;
}
.ghost {
  opacity: 0.5;
}
</style>
