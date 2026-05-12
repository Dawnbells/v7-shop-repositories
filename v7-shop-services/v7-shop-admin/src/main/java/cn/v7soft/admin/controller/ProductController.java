package cn.v7soft.admin.controller;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import cn.v7soft.admin.controller.req.AiTranslateHtmlRequest;
import cn.v7soft.admin.controller.req.AiTranslateImageRequest;
import cn.v7soft.admin.controller.req.AiTranslateTextRequest;
import cn.v7soft.admin.controller.req.EditProductRequest;
import cn.v7soft.admin.controller.req.QueryProductRequest;
import cn.v7soft.admin.controller.req.TranslateByAIRequest;
import cn.v7soft.admin.controller.req.TranslateProductRequest;
import cn.v7soft.admin.controller.resp.AiTranslateImageResponse;
import cn.v7soft.admin.controller.resp.AsyncTaskResponse;
import cn.v7soft.admin.controller.resp.ProductResponse;
import cn.v7soft.admin.exception.InsufficientCreditsException;
import cn.v7soft.admin.service.IAiTranslateService;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.service.IProductService;
import cn.v7soft.admin.service.impl.AiCreditsService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.Product;
import cn.v7soft.dao.utils.SaSessionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/product")
@Tag(name = "商品管理")
public class ProductController extends BaseDataRangeController<Product, IProductService, ProductResponse, QueryProductRequest, EditProductRequest> {

    private final IMultimediaFileService multimediaFileService;
    private final IAiTranslateService aiTranslateService;
    private final AiCreditsService aiCreditsService;

    protected ProductController(IProductService service,
                                IMultimediaFileService multimediaFileService,
                                IAiTranslateService aiTranslateService,
                                AiCreditsService aiCreditsService) {
        super(service);
        this.multimediaFileService = multimediaFileService;
        this.aiTranslateService = aiTranslateService;
        this.aiCreditsService = aiCreditsService;
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

    @Operation(summary = "复制")
    @GetMapping("/translate")
    public ProductResponse translate(@Valid @RequestBody TranslateProductRequest request) {
        return service.translate(request);
    }

    @Operation(summary = "AI批量翻译（异步任务）")
    @PostMapping("/translateByAI")
    public AsyncTaskResponse translateByAI(@Valid @RequestBody TranslateByAIRequest request) {
        SystemUserDto loginUser = SaSessionUtil.getLoginUser();
        Long departmentId = loginUser.getDepartmentId();
        if (!loginUser.isAdmin() && !Objects.equals(departmentId, 1103627419648L)) {
            ClientResponseEnum.NO_PERMISSION.throwException("暂无权限");
        }
        return service.submitTranslateByAI(request);
    }

    @Operation(summary = "AI实时翻译文本（SSE流式）")
    @PostMapping(value = "/ai-translate/text-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter aiTranslateTextStream(@Valid @RequestBody AiTranslateTextRequest request) {
        checkAiTranslatePermission();
        requireAvailableCredits();
        cn.v7soft.dao.entities.primary.SystemUser owner = SaSessionUtil.getLoginOwner();
        SseEmitter emitter = new SseEmitter(300_000L);
        aiTranslateService.streamText(request, owner, emitter);
        return emitter;
    }

    @Operation(summary = "AI实时翻译HTML（SSE流式）")
    @PostMapping(value = "/ai-translate/html-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter aiTranslateHtmlStream(@Valid @RequestBody AiTranslateHtmlRequest request) {
        checkAiTranslatePermission();
        requireAvailableCredits();
        cn.v7soft.dao.entities.primary.SystemUser owner = SaSessionUtil.getLoginOwner();
        SseEmitter emitter = new SseEmitter(300_000L);
        aiTranslateService.streamHtml(request, owner, emitter);
        return emitter;
    }

    @Operation(summary = "AI实时翻译图片")
    @PostMapping("/ai-translate/image")
    public AiTranslateImageResponse aiTranslateImage(@Valid @RequestBody AiTranslateImageRequest request) throws Exception {
        checkAiTranslatePermission();
        requireAvailableCredits();
        cn.v7soft.dao.entities.primary.SystemUser owner = SaSessionUtil.getLoginOwner();
        return aiTranslateService.translateImage(request, owner);
    }

    private void checkAiTranslatePermission() {
        SystemUserDto loginUser = SaSessionUtil.getLoginUser();
        Long departmentId = loginUser.getDepartmentId();
        if (!loginUser.isAdmin() && !Objects.equals(departmentId, 1103627419648L)) {
            ClientResponseEnum.NO_PERMISSION.throwException("暂无权限");
        }
    }

    private void requireAvailableCredits() {
        Long userId = SaSessionUtil.getLoginUser().getLongId();
        if (!aiCreditsService.hasAvailableCredits(userId)) {
            throw new InsufficientCreditsException("AI额度不足，请充值后重试");
        }
    }

    @Override
    protected boolean cleanupBeforeDelete(DeleteRequest request) {
        return true;
    }
}
