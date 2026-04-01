package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.EditProductRequest;
import cn.v7soft.admin.controller.req.TranslateByAIRequest;
import cn.v7soft.admin.controller.req.TranslateProductRequest;
import cn.v7soft.admin.controller.resp.AsyncTaskResponse;
import cn.v7soft.admin.controller.resp.ProductResponse;
import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.Product;
import cn.v7soft.dao.entities.primary.SystemUser;

import java.util.List;
import java.util.Map;

public interface IProductService extends IBaseDataRangeService<Product> {
    Product createOrUpdateProduct(EditProductRequest request);

    List<String> remoteQueryMerchandise(String query);

    ProductResponse translate(TranslateProductRequest request);

    ProductResponse translateByAI(TranslateByAIRequest request);

    ProductResponse translateByAI(TranslateByAIRequest request, SystemUser owner);

    AsyncTaskResponse submitTranslateByAI(TranslateByAIRequest request);

    /**
     * 获取 Product 并在事务内预加载 specificationList 及其 attributes，
     * 避免在事务外访问懒加载集合时抛出 LazyInitializationException。
     */
    Product getByIdWithSpecifications(Long id);

    /**
     * 使用已翻译好的文本/HTML/图片组装并保存新 Product。
     * 由 TaskService 在 Batch 翻译完成后调用。
     *
     * @param translatedTexts  翻译后的短文本列表（与原文本顺序对应）
     * @param translatedIntroduction 翻译后的 HTML
     * @param translatedImageMap     图片ID -> 翻译后图片字节 (null 表示使用原图)
     */
    ProductResponse assembleTranslatedProduct(
            Product product, Language language, SystemUser owner,
            List<String> translatedTexts, String translatedIntroduction,
            Map<String, byte[]> translatedImageMap) throws Exception;
}
