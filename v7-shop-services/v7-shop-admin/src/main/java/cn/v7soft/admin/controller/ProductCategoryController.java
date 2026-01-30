package cn.v7soft.admin.controller;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.hutool.core.bean.BeanUtil;
import cn.v7soft.admin.controller.req.EditProductCategoryRequest;
import cn.v7soft.admin.controller.req.QueryProductCategoryRequest;
import cn.v7soft.admin.controller.resp.ProductCategoryResponse;
import cn.v7soft.admin.service.IProductCategoryService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.common.utils.ConvertUtils;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.controller.request.attributes.LikeAttribute;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.ProductCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/product-category")
@Tag(name = "商品分类管理")
@Validated
public class ProductCategoryController extends BaseDataRangeController<ProductCategory, IProductCategoryService, ProductCategoryResponse, QueryProductCategoryRequest, EditProductCategoryRequest> {

    public ProductCategoryController(IProductCategoryService service) {
        super(service);
    }

    @Operation(summary = "远程搜索")
    @GetMapping("/remoteQuery")
    public List<ProductCategoryResponse> remoteQuery(@RequestParam("query") String query) {
        log.debug("query = " + query);
        QueryPageRequest<ProductCategory> request = QueryPageRequest.fromRequest(QueryProductCategoryRequest.builder().pageNo(1).build());
        if (StringUtils.hasText(query)) {
            request.or()
                    .add(
                            LikeAttribute.builder()
                                    .name("name")
                                    .value("%" + query.trim() + "%")
                                    .build()
                    )
                    .addConstraint(ConvertUtils.isLong(query),
                                   EqualsQueryAttribute.builder()
                                           .name("id")
                                           .value(query.trim())
                                           .build()
                    )
                    .next()
                    .add(EqualsQueryAttribute.builder().name("status").value(StatusEnum.VALID).build());
        }
        return service.findPaginated(request)
                .stream()
                .map(this::convertEntityCopyId)
                .collect(Collectors.toList());
    }

    @Override
    protected ProductCategoryResponse convertEntity(ProductCategory entity) {
        return ProductCategoryResponse.convertEntity(entity);
    }

    @Override
    protected ProductCategory convertRequest(@Nullable ProductCategory dbEntity, EditProductCategoryRequest request) {
        ProductCategory entity = Optional.ofNullable(dbEntity).orElse(ProductCategory.builder().build());
        BeanUtil.copyProperties(request, entity, "id");
        return entity;
    }

    @Override
    protected String getPermissionPrefix() {
        return "product-category";
    }
}
