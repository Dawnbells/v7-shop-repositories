package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.req.EditProductSKURequest;
import cn.v7soft.admin.service.IProductSKUService;
import cn.v7soft.common.controller.req.attributes.AccessDataRangeAttribute;
import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.ProductSKU;
//import cn.v7soft.dao.repositories.primary.OrderRepository;
import cn.v7soft.dao.repositories.primary.OrderRepository;
import cn.v7soft.dao.repositories.primary.ProductSKURepository;
import cn.v7soft.dao.tenant.TenantContext;
import cn.v7soft.dao.utils.SaSessionUtil;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ProductSKUService extends BaseDataRangeService<ProductSKU, ProductSKURepository> implements IProductSKUService {
    private final OrderRepository orderRepository;

    public ProductSKUService(ProductSKURepository repository, OrderRepository orderRepository) {
        super(repository);
        this.orderRepository = orderRepository;
    }

    @Override
    protected void checkKeyConstraint(ProductSKU entity) {
        boolean existingSKU = repository.existsByCodeInSameDepartment(entity.getId(), entity.getSkuCode(), entity.getOwner().getId());
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(!existingSKU, "SKU 编码组内不允许重复");
    }

    @Override
    public Optional<ProductSKU> findBySkuCode(String skuCode) {
        return repository.findBySkuCode(skuCode, SaSessionUtil.getLoginOwner().getId());
    }

    @Override
    public ProductSKU getBySkuCode(String skuCode) {
        return findBySkuCode(skuCode).orElseThrow(() -> ClientResponseEnum.PARAMETER_ILLEGAL.newException("SKU不存在：" + skuCode));
    }

    @Override
    @Transactional
    public ProductSKU getOrSaveBySkuCode(ProductSKU productSKU) {
        if (productSKU == null) {
            return null;
        }
        if (productSKU.getId() == null) {
            // ID不存在，根据skuCode查询，不存在则先保存
            Optional<ProductSKU> optional = findBySkuCode(productSKU.getSkuCode());
            return optional.orElseGet(() -> saveAndFlush(productSKU));
        }
        // ID存在，根据ID查询返回
        return getById(productSKU.getId());
    }

    @Override
    public QueryAttribute getAccessDataRangeQueryAttribute() {
        return new AccessDataRangeAttribute(AccessDataRangeLevel.DEEP_DEPARTMENT);
    }

    @Override
    @Transactional
    public ProductSKU doCreateOrUpdateOperate(EditProductSKURequest request) {
        ProductSKU productSku;
        if (request.hasId()) {
            productSku = getById(request.getIdLongValue());
            productSku.setSkuCode(request.getSkuCode());
            productSku.setName(request.getName());
            if (request.getSyncChangeOrder()
                    && (!Objects.equals(request.getSkuCode(), productSku.getSkuCode())
                    || !Objects.equals(request.getName(), productSku.getName()))) {
                orderRepository.syncChangeSkuInfo(productSku.getId(), productSku.getName(), productSku.getSkuCode());
            }
        } else {
            productSku = ProductSKU.builder()
                    .skuCode(request.getSkuCode())
                    .name(request.getName())
                    .totalUnitsSold(0)
                    .totalSalesRevenue(BigDecimal.ZERO)
                    .build()
                    .fillOwner();
        }
        checkKeyConstraint(productSku);
        return repository.save(productSku);
    }

    @Override
    public List<ProductSKU> listBySkuCodes(List<String> skuCodes) {
        return repository.listBySkuCodes(skuCodes, SaSessionUtil.getLoginOwner().getId());
    }

    @Override
    public List<ProductSKU> listBySkuCodes(List<String> skuCodes, Long ownerId) {
        return repository.listBySkuCodes(skuCodes, ownerId);
    }

    @Override
    public List<ProductSKU> listBySkuCodesAndOwnerId(List<String> skuCodes, Long ownerId) {
        return repository.listBySkuCodesAndOwnerId(skuCodes, ownerId);
    }
}
