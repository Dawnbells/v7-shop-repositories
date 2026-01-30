package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.service.IProductCategoryService;
import cn.v7soft.common.controller.req.attributes.AccessDataRangeAttribute;
import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.dao.entities.primary.ProductCategory;
import cn.v7soft.dao.repositories.primary.ProductCategoryRepository;
import org.springframework.stereotype.Service;

@Service
public class ProductCategoryService extends BaseDataRangeService<ProductCategory, ProductCategoryRepository> implements IProductCategoryService {
    public ProductCategoryService(ProductCategoryRepository repository) {
        super(repository);
    }

    @Override
    protected void checkKeyConstraint(ProductCategory entity) {
//        ProductCategory existingCategory = repository.findBySameName(entity.getName(), entity.getId());
//        ClientResponseEnum.PARAMETER_ILLEGAL.isNull(existingCategory, "分类名称不允许重复");
    }

    @Override
    public QueryAttribute getAccessDataRangeQueryAttribute() {
        return new AccessDataRangeAttribute(AccessDataRangeLevel.DEPARTMENT);
    }
}
