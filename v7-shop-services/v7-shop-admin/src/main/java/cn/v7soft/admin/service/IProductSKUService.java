package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.EditProductSKURequest;
import cn.v7soft.admin.controller.req.ReplaceSkuRequest;
import cn.v7soft.admin.controller.resp.SkuReplaceDistributionResponse;
import cn.v7soft.admin.controller.resp.SkuReplaceResultResponse;
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

    List<ProductSKU> listBySkuCodesAndOwnerId(List<String> skuCodes, Long ownerId);

    /**
     * 查询源 SKU 在当前操作者管理范围内实际用到的市场(国家)分布。
     * 命中口径与 {@link #replaceSku} 一致：主 SKU / 规格 SKU / 备用 SKU 任一处引用即算。
     *
     * @param sourceSkuId 源 SKU ID
     * @param spuIds      可选，限定归属 SPU（SPU 批量替换入口）；null/空则不限
     * @return 每个市场(国家)下受影响的商品数
     */
    List<SkuReplaceDistributionResponse> findReplaceDistribution(Long sourceSkuId, List<Long> spuIds);

    /**
     * 查询源 SKU 交集候选：在每个选中 SPU 下（管理范围内，主/规格/备用任一引用，不限市场）
     * 都出现的 SKU，供 SPU 批量替换弹框的源 SKU 下拉。源 SKU 状态不限（含 INVALID）。
     *
     * @param spuIds 选中的 SPU ID 列表
     * @return 交集 SKU（按编码排序）
     */
    List<ProductSKU> findReplaceSourceCandidates(List<Long> spuIds);

    /**
     * 搜索可作为替换目标的 SKU：固定管理范围内 + VALID，与 {@link #replaceSku} 的目标校验口径一致，
     * 避免下拉能选到提交时会被拒的 SKU。
     *
     * @param query 按品名/编码模糊搜索
     * @return 候选 SKU（最多 20 条）
     */
    List<ProductSKU> findReplaceTargets(String query);

    /**
     * 把选中市场(国家)下、管理范围内全部商品中的源 SKU 替换成目标 SKU。
     * 覆盖商品主 SKU、规格 SKU、备用 SKU 三处引用；不动源 SKU 实体、历史订单与销量统计。
     *
     * @param request 替换请求
     * @return 实际受影响的商品数（去重）
     */
    SkuReplaceResultResponse replaceSku(ReplaceSkuRequest request);
}
