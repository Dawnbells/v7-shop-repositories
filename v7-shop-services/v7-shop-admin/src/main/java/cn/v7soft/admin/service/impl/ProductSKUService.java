package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.req.EditProductSKURequest;
import cn.v7soft.admin.controller.req.ReplaceSkuRequest;
import cn.v7soft.admin.controller.resp.SkuReplaceDistributionResponse;
import cn.v7soft.admin.controller.resp.SkuReplaceResultResponse;
import cn.v7soft.admin.service.IProductSKUService;
import cn.v7soft.common.controller.req.attributes.AccessDataRangeAttribute;
import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.Product;
import cn.v7soft.dao.entities.primary.ProductSKU;
import cn.v7soft.dao.entities.primary.ProductSpecification;
import cn.v7soft.dao.entities.primary.Spu;
import cn.v7soft.dao.enums.ViewMode;
//import cn.v7soft.dao.repositories.primary.OrderRepository;
import cn.v7soft.dao.repositories.primary.OrderRepository;
import cn.v7soft.dao.repositories.primary.ProductRepository;
import cn.v7soft.dao.repositories.primary.ProductSKURepository;
import cn.v7soft.dao.repositories.primary.SpuRepository;
import cn.v7soft.dao.tenant.TenantContext;
import cn.v7soft.dao.utils.SaSessionUtil;

import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProductSKUService extends BaseDataRangeService<ProductSKU, ProductSKURepository> implements IProductSKUService {
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final SpuRepository spuRepository;

    public ProductSKUService(ProductSKURepository repository, OrderRepository orderRepository,
                             ProductRepository productRepository, SpuRepository spuRepository) {
        super(repository);
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.spuRepository = spuRepository;
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

    @Override
    @Transactional(readOnly = true)
    public List<SkuReplaceDistributionResponse> findReplaceDistribution(Long sourceSkuId, List<Long> spuIds) {
        // 数据库侧按国家分组计数，避免物化全部命中商品实体；
        // 国家名走查询内 JOIN 取得（而非懒代理 getName），软删除国家不会触发 EntityNotFoundException。
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Product> root = cq.from(Product.class);
        Join<Object, Object> countryJoin = root.join("country");
        cq.multiselect(countryJoin.get("id"), countryJoin.get("name"), cb.countDistinct(root.get("id")))
                .where(buildAffectedPredicate(root, cq, cb, sourceSkuId, null, spuIds))
                .groupBy(countryJoin.get("id"), countryJoin.get("name"));
        return entityManager.createQuery(cq).getResultList().stream()
                .map(tuple -> SkuReplaceDistributionResponse.builder()
                        .countryId(tuple.get(0, Long.class))
                        .countryName(tuple.get(1, String.class))
                        .productCount(tuple.get(2, Long.class))
                        .build())
                .sorted(Comparator.comparing(SkuReplaceDistributionResponse::getCountryName))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SkuReplaceResultResponse replaceSku(ReplaceSkuRequest request) {
        Long sourceId = request.getSourceSkuId();
        Long targetId = request.getTargetSkuId();
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(!Objects.equals(sourceId, targetId), "源和目标 SKU 不能相同");
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(repository.existsById(sourceId), "源 SKU 不存在");
        // 目标 SKU 必须存在、启用(VALID)、且在当前操作者管理范围内
        ProductSKU target = repository.findOne(targetSkuScopeSpec(targetId))
                .orElseThrow(() -> ClientResponseEnum.PARAMETER_ILLEGAL.newException("目标 SKU 不存在、未启用或不在管理范围内"));

        List<Long> spuIds = request.getSpuIds();
        if (spuIds != null && !spuIds.isEmpty()) {
            // SPU 批量入口的资格检查：源 SKU 必须在每个选中 SPU 下存在（管理范围内、不限市场），缺失则整体拒绝
            Set<Long> covered = collectSkuSpuCoverage(spuIds, sourceId).getOrDefault(sourceId, Set.of());
            List<Long> missing = spuIds.stream().distinct().filter(id -> !covered.contains(id)).toList();
            if (!missing.isEmpty()) {
                // 逐 id 回退拼装：解析到的显示 code(name)，解析不到（如并发被删）的直接列 id，保证拒绝清单完整
                Map<Long, Spu> resolvedSpus = spuRepository.findAllById(missing).stream()
                        .collect(Collectors.toMap(Spu::getId, spu -> spu));
                String missingNames = missing.stream()
                        .map(id -> {
                            Spu spu = resolvedSpus.get(id);
                            return spu != null ? spu.getCode() + "(" + spu.getName() + ")" : String.valueOf(id);
                        })
                        .collect(Collectors.joining("、"));
                throw ClientResponseEnum.PARAMETER_ILLEGAL.newException("以下SPU下不存在源SKU，已取消替换：" + missingNames);
            }
        }

        List<Product> affected = productRepository.findAll(affectedProductSpec(sourceId, request.getCountryIds(), spuIds));
        for (Product product : affected) {
            // 主 SKU
            if (product.getSku() != null && Objects.equals(product.getSku().getId(), sourceId)) {
                product.setSku(target);
            }
            // 备用 SKU：移除源，若目标尚不存在再加入（静默去重，绝不产生重复引用）
            List<ProductSKU> alternativeSkus = product.getAlternativeSkus();
            if (alternativeSkus != null) {
                boolean removed = alternativeSkus.removeIf(sku -> Objects.equals(sku.getId(), sourceId));
                if (removed && alternativeSkus.stream().noneMatch(sku -> Objects.equals(sku.getId(), targetId))) {
                    alternativeSkus.add(target);
                }
            }
            // 规格 SKU（多规格商品每个规格各自的 SKU）：直接遍历，避免 isMultiSpecs 标记与实际规格不一致时漏改
            if (product.getSpecificationList() != null) {
                for (ProductSpecification specification : product.getSpecificationList()) {
                    if (specification.getSku() != null && Objects.equals(specification.getSku().getId(), sourceId)) {
                        specification.setSku(target);
                    }
                }
            }
        }
        // 无需显式 saveAll：affected 在事务内均为受管实体，改动随事务提交自动 flush。

        log.info("[SKU替换] operator={} source={} target={} countryIds={} spuIds={} affectedProducts={}",
                SaSessionUtil.getLoginUser().getId(), sourceId, targetId, request.getCountryIds(), spuIds, affected.size());
        return SkuReplaceResultResponse.builder().affectedProductCount(affected.size()).build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductSKU> findReplaceTargets(String query) {
        // 与 replaceSku 的目标校验(targetSkuScopeSpec)同口径：固定管理范围 + VALID，
        // 保证下拉候选提交时不会被后端拒绝
        Specification<ProductSKU> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(managementScopeAttribute().toPredicate(root, cq, cb));
            predicates.add(cb.equal(root.get("status"), StatusEnum.VALID));
            if (StringUtils.hasText(query)) {
                String like = "%" + query.trim() + "%";
                predicates.add(cb.or(cb.like(root.get("name"), like), cb.like(root.get("skuCode"), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(spec, PageRequest.of(0, 20)).getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductSKU> findReplaceSourceCandidates(List<Long> spuIds) {
        Set<Long> distinctSpuIds = new HashSet<>(spuIds);
        // 交集：仅保留覆盖了全部选中 SPU 的 SKU（资格检查不限市场）
        Map<Long, Set<Long>> coverage = collectSkuSpuCoverage(spuIds, null);
        List<Long> intersection = coverage.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= distinctSpuIds.size())
                .map(Map.Entry::getKey)
                .toList();
        if (intersection.isEmpty()) {
            return List.of();
        }
        // 源 SKU 状态不限（含 INVALID），@SQLRestriction 已排除 DELETED
        return repository.findAllById(intersection).stream()
                .sorted(Comparator.comparing(ProductSKU::getSkuCode))
                .collect(Collectors.toList());
    }

    /**
     * 收集选中 SPU 下（管理范围内、不限市场）出现的 SKU→SPU 覆盖关系，覆盖主/规格/备用三处引用。
     * 归属仅算 product.spu_id，斗篷 show_spu 引用不算。
     *
     * @param onlySkuId 可选，仅统计该 SKU（用于 replaceSku 的资格复验）
     * @return skuId → 覆盖到的 spuId 集合
     */
    private Map<Long, Set<Long>> collectSkuSpuCoverage(List<Long> spuIds, Long onlySkuId) {
        Map<Long, Set<Long>> coverage = new HashMap<>();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        // 主 SKU
        {
            CriteriaQuery<Tuple> cq = cb.createTupleQuery();
            Root<Product> root = cq.from(Product.class);
            List<Predicate> predicates = coverageBasePredicates(root, cq, cb, spuIds);
            predicates.add(root.get("sku").get("id").isNotNull());
            if (onlySkuId != null) {
                predicates.add(cb.equal(root.get("sku").get("id"), onlySkuId));
            }
            cq.multiselect(root.get("sku").get("id"), root.get("spu").get("id")).distinct(true)
                    .where(cb.and(predicates.toArray(new Predicate[0])));
            mergeCoverage(coverage, cq);
        }
        // 规格 SKU
        {
            CriteriaQuery<Tuple> cq = cb.createTupleQuery();
            Root<Product> root = cq.from(Product.class);
            Join<Object, Object> specJoin = root.join("specificationList");
            List<Predicate> predicates = coverageBasePredicates(root, cq, cb, spuIds);
            predicates.add(specJoin.get("sku").get("id").isNotNull());
            if (onlySkuId != null) {
                predicates.add(cb.equal(specJoin.get("sku").get("id"), onlySkuId));
            }
            cq.multiselect(specJoin.get("sku").get("id"), root.get("spu").get("id")).distinct(true)
                    .where(cb.and(predicates.toArray(new Predicate[0])));
            mergeCoverage(coverage, cq);
        }
        // 备用 SKU
        {
            CriteriaQuery<Tuple> cq = cb.createTupleQuery();
            Root<Product> root = cq.from(Product.class);
            Join<Object, Object> altJoin = root.join("alternativeSkus");
            List<Predicate> predicates = coverageBasePredicates(root, cq, cb, spuIds);
            if (onlySkuId != null) {
                predicates.add(cb.equal(altJoin.get("id"), onlySkuId));
            }
            cq.multiselect(altJoin.get("id"), root.get("spu").get("id")).distinct(true)
                    .where(cb.and(predicates.toArray(new Predicate[0])));
            mergeCoverage(coverage, cq);
        }
        return coverage;
    }

    private List<Predicate> coverageBasePredicates(Root<Product> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                                   List<Long> spuIds) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(managementScopeAttribute().toPredicate(root, query, cb));
        predicates.add(root.get("spu").get("id").in(spuIds));
        return predicates;
    }

    private void mergeCoverage(Map<Long, Set<Long>> coverage, CriteriaQuery<Tuple> cq) {
        entityManager.createQuery(cq).getResultList().forEach(tuple ->
                coverage.computeIfAbsent(tuple.get(0, Long.class), key -> new HashSet<>())
                        .add(tuple.get(1, Long.class)));
    }

    /**
     * 目标 SKU 校验谓词：在管理范围内 + 指定 id + 状态 VALID。
     */
    private Specification<ProductSKU> targetSkuScopeSpec(Long targetSkuId) {
        return (root, query, cb) -> cb.and(
                managementScopeAttribute().toPredicate(root, query, cb),
                cb.equal(root.get("id"), targetSkuId),
                cb.equal(root.get("status"), StatusEnum.VALID)
        );
    }

    /**
     * 受影响商品的 Specification（供 replaceSku 的 findAll 使用，去重返回商品实体）。
     */
    private Specification<Product> affectedProductSpec(Long sourceSkuId, List<Long> countryIds, List<Long> spuIds) {
        return (root, query, cb) -> {
            query.distinct(true);
            return buildAffectedPredicate(root, query, cb, sourceSkuId, countryIds, spuIds);
        };
    }

    /**
     * 受影响商品谓词：管理范围 + 市场(国家) + 归属SPU(可选) + 源 SKU 命中主/规格/备用任一处引用。
     * 含失效(INVALID)商品，实体自带 @SQLRestriction 已排除 DELETED。分布统计与实际替换共用同一套谓词。
     *
     * @param countryIds 为空表示不限国家（用于分布统计）
     * @param spuIds     为空表示不限归属SPU（SKU 表格行级入口）；仅算 product.spu_id 归属，斗篷 show_spu 引用不算
     */
    private Predicate buildAffectedPredicate(Root<Product> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                             Long sourceSkuId, List<Long> countryIds, List<Long> spuIds) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(managementScopeAttribute().toPredicate(root, query, cb));
        if (countryIds != null && !countryIds.isEmpty()) {
            predicates.add(root.get("country").get("id").in(countryIds));
        }
        if (spuIds != null && !spuIds.isEmpty()) {
            predicates.add(root.get("spu").get("id").in(spuIds));
        }
        // 规格命中：存在该商品的规格引用了源 SKU
        Subquery<Long> specSub = query.subquery(Long.class);
        Root<ProductSpecification> specRoot = specSub.from(ProductSpecification.class);
        specSub.select(specRoot.get("product").get("id").as(Long.class))
                .where(cb.equal(specRoot.get("sku").get("id"), sourceSkuId));
        Predicate mainMatch = cb.equal(root.get("sku").get("id"), sourceSkuId);
        Predicate specMatch = root.get("id").in(specSub);
        Predicate altMatch = cb.equal(root.join("alternativeSkus", JoinType.LEFT).get("id"), sourceSkuId);
        predicates.add(cb.or(mainMatch, specMatch, altMatch));
        return cb.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * 固定管理范围谓词：强制 ViewMode.TEAM（不随会话个人/团队切换），且不套"公开品"放行钩子，
     * 从而"别人的公开品改不到"。镜像 BaseDataRangeService 的跨部门处理。
     */
    private AccessDataRangeAttribute managementScopeAttribute() {
        SystemUserDto user = SaSessionUtil.getLoginUser();
        if (Boolean.TRUE.equals(user.getIsCrossDepartment())) {
            return new AccessDataRangeAttribute(AccessDataRangeLevel.SPECIFIED_DEPARTMENTS,
                    user.getManageDepartmentIds(), Boolean.TRUE.equals(user.getIsExcludeDepartment()))
                    .setOwner(user).setViewMode(ViewMode.TEAM);
        }
        return new AccessDataRangeAttribute().setOwner(user).setViewMode(ViewMode.TEAM);
    }
}
