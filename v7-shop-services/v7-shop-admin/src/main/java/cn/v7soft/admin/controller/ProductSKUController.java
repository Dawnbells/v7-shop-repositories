package cn.v7soft.admin.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.v7soft.admin.controller.req.EditProductSKURequest;
import cn.v7soft.admin.controller.req.QueryProductSKURequest;
import cn.v7soft.admin.controller.resp.ProductSKUResponse;
import cn.v7soft.admin.service.IProductSKUService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.controller.request.attributes.LikeAttribute;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.ProductSKU;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/product-sku")
@Tag(name = "商品 SKU 管理")
@Validated
public class ProductSKUController extends BaseDataRangeController<ProductSKU, IProductSKUService, ProductSKUResponse, QueryProductSKURequest, EditProductSKURequest> {
    public ProductSKUController(IProductSKUService service) {
        super(service);
    }

    @Operation(summary = "远程搜索")
    @GetMapping("/remoteQuery")
    public List<ProductSKUResponse> remoteQuery(@RequestParam("query") String query) {
        QueryPageRequest<ProductSKU> request = QueryPageRequest.fromRequest(QueryProductSKURequest.builder().pageNo(1).build());
        //noinspection DuplicatedCode
        if (StringUtils.hasText(query)) {
            request
                    .or()
                    .add(
                            LikeAttribute.builder()
                                    .name("name")
                                    .value("%" + query.trim() + "%")
                                    .build()
                    )
                    .add(LikeAttribute.builder()
                            .name("skuCode")
                            .value("%" + query.trim() + "%")
                            .build())
                    .next()
                    .add(EqualsQueryAttribute.builder().name("status").value(StatusEnum.VALID).build());
        }
        return service.findPaginated(request.add(EqualsQueryAttribute.builder().name("status").value(StatusEnum.VALID).build()))
                .stream()
                .map(this::convertEntityCopyId)
                .collect(Collectors.toList());
    }

    @Override
    protected QueryPageRequest<ProductSKU> convertQueryPageRequest(QueryProductSKURequest request) {
        return super.convertQueryPageRequest(request)
                .add(
                        EqualsQueryAttribute.builder()
                                .name("isVirtual")
                                .value(request.isVirtual())
                                .build()
                );
    }

    @Override
    protected ProductSKUResponse convertEntity(ProductSKU entity) {
        return ProductSKUResponse.convertEntity(entity);
    }

    @Override
    protected ProductSKU doEditOperate(EditProductSKURequest request) {
        return service.doCreateOrUpdateOperate(request);
    }

    @Override
    protected ProductSKU convertRequest(@Nullable ProductSKU dbEntity, EditProductSKURequest request) {
        ProductSKU entity = Optional.ofNullable(dbEntity).orElse(ProductSKU.builder().build());
        BeanUtil.copyProperties(request, entity, "id");
        if (dbEntity == null) {
            entity.setTotalUnitsSold(0);
            entity.setTotalSalesRevenue(BigDecimal.ZERO);
        }
        return entity;
    }

    @Override
    protected String getPermissionPrefix() {
        return "product-sku";
    }
}
