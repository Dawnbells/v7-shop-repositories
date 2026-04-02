package cn.v7soft.admin.controller;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.v7soft.admin.controller.req.EditProductRequest;
import cn.v7soft.admin.controller.req.QueryProductRequest;
import cn.v7soft.admin.controller.req.TranslateByAIRequest;
import cn.v7soft.admin.controller.req.TranslateProductRequest;
import cn.v7soft.admin.controller.resp.AsyncTaskResponse;
import cn.v7soft.admin.controller.resp.ProductResponse;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.service.IProductService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.Product;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/product")
@Tag(name = "商品管理")
public class ProductController extends BaseDataRangeController<Product, IProductService, ProductResponse, QueryProductRequest, EditProductRequest> {

    private final IMultimediaFileService multimediaFileService;

    protected ProductController(IProductService service,
                                IMultimediaFileService multimediaFileService) {
        super(service);
        this.multimediaFileService = multimediaFileService;
    }

    @Override
    protected void validRequest(EditProductRequest request) {
        if (request.getIsMultiSpecs()) {
            ClientResponseEnum.PARAMETER_ILLEGAL.notEmpty(request.getSpecifications(), "商品规格不能为空");
            request.getSpecifications().forEach(editProductSpecification -> ClientResponseEnum.PARAMETER_ILLEGAL.notEmpty(editProductSpecification.getAttributes(), "商品规格属性不能为空"));
            return;
        }
        // 非多规格
        if (!StringUtils.hasText(request.getSkuId())) {
            ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(request.getSkuCode(), "SKU不允许为空");
            ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(request.getSkuCode(), "SKU品名不允许为空");
        } else {
            ClientResponseEnum.PARAMETER_ILLEGAL.isLong(request.getSkuId(), "SKU格式不正确");
        }
    }

    @Override
    protected ProductResponse convertEntity(Product product) {
        return ProductResponse.convertEntity(multimediaFileService, product);
    }

    @Override
    public Product doEditOperate(EditProductRequest request) {
        return service.createOrUpdateProduct(request);
    }

    @Override
    protected Product convertRequest(@Nullable Product dbEntity, EditProductRequest request) {
        return dbEntity;
    }

    @Override
    protected String getPermissionPrefix() {
        return "product";
    }

    @Operation(summary = "远程搜索中文品名")
    @GetMapping("/remoteQueryMerchandise")
    public List<String> remoteQueryMerchandise(@RequestParam("query") String query) {
        return service.remoteQueryMerchandise(query);
    }

    @Operation(summary = "翻译")
    @GetMapping("/translate")
    public ProductResponse translate(@Valid @RequestBody TranslateProductRequest request) {
        return service.translate(request);
    }

    @Operation(summary = "AI批量翻译（异步任务）")
    @PostMapping("/translateByAI")
    public AsyncTaskResponse translateByAI(@Valid @RequestBody TranslateByAIRequest request) {
        return service.submitTranslateByAI(request);
    }

    @Operation(summary = "AI即时翻译（异步任务）")
    @PostMapping("/translateByAIDirect")
    public AsyncTaskResponse translateByAIDirect(@Valid @RequestBody TranslateByAIRequest request) {
        return service.submitTranslateByAIDirect(request);
    }

    @Override
    protected boolean cleanupBeforeDelete(DeleteRequest request) {
        return true;
    }
}
