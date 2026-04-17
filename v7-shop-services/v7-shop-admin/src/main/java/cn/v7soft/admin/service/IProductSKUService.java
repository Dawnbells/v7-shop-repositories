package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.EditProductSKURequest;
import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.dao.entities.primary.ProductSKU;

import java.util.List;
import java.util.Optional;

public interface IProductSKUService extends IBaseDataRangeService<ProductSKU> {
    /**
     * 根据SKU代码获取SKU
     *
     * @param skuCode SKU代码
     * @return SKU
     */
    Optional<ProductSKU> findBySkuCode(String skuCode);

    /**
     * 根据SKU代码获取SKU
     * @param skuCode SKU代码
     * @return SKU
     */
    ProductSKU getBySkuCode(String skuCode);

    /**
     * 根据SKU代码获取或保存SKU
     * @param productSKU SKU
     * @return 持久化的SKU
     */
    ProductSKU getOrSaveBySkuCode(ProductSKU productSKU);

    /**
     * 新增或者更新或者
     * @param request 请求
     */
    ProductSKU doCreateOrUpdateOperate(EditProductSKURequest request);

    List<ProductSKU> listBySkuCodes(List<String> alternativeSkusIds);

    List<ProductSKU> listBySkuCodes(List<String> skuCodes, Long ownerId);
}
