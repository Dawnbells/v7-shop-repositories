package cn.v7soft.admin.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.CheckSpuTicketRequest;
import cn.v7soft.admin.controller.req.EditSpuRequest;
import cn.v7soft.admin.controller.req.GenerateSharedUrlRequest;
import cn.v7soft.admin.controller.req.QuerySpuRequest;
import cn.v7soft.admin.controller.req.ShareSpuRequest;
import cn.v7soft.admin.controller.req.SwitchOpenRequest;
import cn.v7soft.admin.controller.resp.CheckSpuTicketResponse;
import cn.v7soft.admin.controller.resp.SharedSpuResponse;
import cn.v7soft.admin.controller.resp.SpuResponse;
import cn.v7soft.admin.controller.resp.SpuSimpleResponse;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.service.ISpuService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.common.utils.ConvertUtils;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.controller.request.attributes.LikeAttribute;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.ProductCategory;
import cn.v7soft.dao.entities.primary.Spu;
import cn.v7soft.dao.entities.primary.Website;
import cn.v7soft.dao.tenant.WebsiteContext;
import cn.v7soft.dao.utils.SaSessionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/spu")
@Tag(name = "SPU管理")
@Slf4j
public class SpuController extends
                           BaseDataRangeController<Spu, ISpuService, SpuResponse, QuerySpuRequest, EditSpuRequest> {

    private final IMultimediaFileService multimediaFileService;

    protected SpuController(ISpuService service, IMultimediaFileService multimediaFileService) {
        super(service);
        this.multimediaFileService = multimediaFileService;
    }

    @Operation(summary = "绑定SPU到当前商城")
    @PostMapping("/bind-spu/{spuId}")
    public void bindSpuToWebsite(@PathVariable Long spuId) {
        service.bindSpuToWebsite(spuId);
    }

    @PostMapping("/switchOpen")
    @Operation(summary = "切换是否共享")
    public void switchOpen(@Valid @RequestBody SwitchOpenRequest request) {
        String permission = getPermissionPrefix() + ".switch.open";
        StpUtil.checkPermission(permission);
        ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(request.getId(), "ID参数为空");
        assert request.getId() != null;
        service.switchOpen(Long.parseLong(request.getId()), request.isOpen());
    }

    @SuppressWarnings("DuplicatedCode")
    @PostMapping("/unbind-spus")
    @Operation(summary = "根据ID删除")
    public void bindSpusFromWebsite(@Valid @RequestBody DeleteRequest request) {
        List<Long> spuIds = null;
        try {
            spuIds = Arrays.stream(request.getIds().split(",")).map(Long::parseLong)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            ClientResponseEnum.PARAMETER_ILLEGAL.throwException("IDS参数错: " + request.getIds());
        }
        ClientResponseEnum.PARAMETER_ILLEGAL.notEmpty(spuIds, "IDS参数为空");
        service.unbindSpuToWebsite(spuIds);
    }

    @Override
    protected AccessDataRangeLevel getPageAccessDataRangeLevel(QuerySpuRequest request) {
        if (!Boolean.TRUE.equals(request.getOnlyWebsite())) {
            return null;
        }
        if (SaSessionUtil.getLoginUser().isDepartmentManager()) {
            return AccessDataRangeLevel.DEEP_DEPARTMENT;
        }
        return AccessDataRangeLevel.DEPARTMENT;
    }

    @Override
    protected QueryPageRequest<Spu> convertQueryPageRequest(QuerySpuRequest request) {
        log.debug("spu convert query page request = {}", request);
        if (StrUtil.isBlank(request.getSortBy())) {
            request.setSortBy("updateTime desc, id desc");
        }
        return super.convertQueryPageRequest(request)
                .addConstraint(Boolean.TRUE.equals(request.getOnlyWebsite()), new QueryAttribute() {
                    @Override
                    public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query,
                                                     CriteriaBuilder criteriaBuilder) {
                        Root<Website> websiteRoot = query.from(Website.class);
                        Predicate websitePredicate = criteriaBuilder.equal(websiteRoot.get("id"),
                                                                           WebsiteContext.getCurrentWebsiteId());
                        Predicate spuPredicate = criteriaBuilder.isMember(websiteRoot,
                                                                          root.<List<Website>>get("websiteList"));
                        return criteriaBuilder.and(websitePredicate, spuPredicate);
                    }
                })
                .or()
                .addConstraint(StrUtil.isNotBlank(request.getTitle()), new QueryAttribute() {
                    @Override
                    public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query,
                                                     CriteriaBuilder criteriaBuilder) {
                        Predicate namePredicate = LikeAttribute.builder().name("name")
                                .value("%" + request.getTitle() + "%").build()
                                .toPredicate(root, query, criteriaBuilder);
                        Predicate ownerNamePredicate = EqualsQueryAttribute.builder().name("owner.name")
                                .value("%" + request.getTitle() + "%").build()
                                .toPredicate(root, query, criteriaBuilder);
                        Predicate codePredicate = LikeAttribute.builder().name("code")
                                .value(request.getTitle()).build()
                                .toPredicate(root, query, criteriaBuilder);
                        return criteriaBuilder.or(namePredicate, ownerNamePredicate, codePredicate);
                    }
                })
                .addConstraint(ConvertUtils.isLong(request.getTitle()), (title) -> EqualsQueryAttribute.<Long>builder().name("id").value(ConvertUtils.parseLong(request.getTitle())).build())
                .next();
    }

    @Operation(summary = "远程搜索")
    @GetMapping("/remoteQuery")
    public List<SpuResponse> remoteQuery(@RequestParam("query") String query) {
        QueryPageRequest<Spu> request = QueryPageRequest.fromRequest(
                QuerySpuRequest.builder().pageNo(1).build());
        //noinspection DuplicatedCode
        if (StringUtils.hasText(query)) {
            request.or()
                    .add(LikeAttribute.builder().name("name").value(query.trim()).build())
                    .addConstraint(ConvertUtils.isLong(query), EqualsQueryAttribute.builder().name("id").value(ConvertUtils.parseLongOrNull(query)).build())
                    .addConstraint(ConvertUtils.isLong(query), LikeAttribute.builder().name("code").value(query).build())
                    .next()
                    .add(EqualsQueryAttribute.builder().name("status").value(StatusEnum.VALID).build());
        }
        return service.findPaginated(request).stream().map(this::convertEntityCopyId)
                .collect(Collectors.toList());
    }

    @Operation(summary = "远程简易信息搜索")
    @GetMapping("/remoteQuerySimple")
    public List<SpuSimpleResponse> remoteQuerySimple(@RequestParam("query") String query,
                                                     @RequestParam(value = "inside", required = false, defaultValue =
                                                             "false") Boolean inside) {
        QueryPageRequest<Spu> request = QueryPageRequest.fromRequest(
                QuerySpuRequest.builder().pageSize(10).pageNo(1).build());
        request.addConstraint(WebsiteContext.isWebsiteAdmin(), new QueryAttribute() {
            @Override
            public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query,
                                             CriteriaBuilder criteriaBuilder) {
                // 创建子查询
                Subquery<Long> subquery = query.subquery(Long.class);
                var spuRoot = subquery.from(Spu.class);

                // 关联 t_website_spus
                var websiteJoin = spuRoot.join("websiteList"); // 使用 @ManyToMany 关系定义中的属性名

                // 子查询条件：w.website_id = ?
                subquery.select(spuRoot.get("id"))
                        .where(criteriaBuilder.equal(websiteJoin.get("id"),
                                                     WebsiteContext.getCurrentWebsiteId()));

                if (Boolean.TRUE.equals(inside)) {
                    return root.get("id").in(subquery);
                }
                // 主查询：s1.id NOT IN (子查询)
                return criteriaBuilder.not(root.get("id").in(subquery));
            }
        });
        //noinspection DuplicatedCode
        if (StringUtils.hasText(query)) {
            request.or().add(LikeAttribute.builder().name("name").value("%" + query.trim() + "%")
                                     .build())
                    .add(LikeAttribute.builder().name("code").value("%" + query.trim() + "%")
                                 .build()).next();
        }
        request.add(EqualsQueryAttribute.builder().name("status").value(StatusEnum.VALID).build());
        log.debug("pageable is >> " + request.toPageable());
        return service.findPaginated(request).stream().map(SpuSimpleResponse::convertEntity)
                .collect(Collectors.toList());
    }

    @Override
    protected SpuResponse convertEntity(Spu spu) {
        return SpuResponse.convertEntity(multimediaFileService, spu);
    }

    @Override
    protected Spu convertRequest(@Nullable Spu dbEntity, EditSpuRequest request) {
        Spu spu = Optional.ofNullable(dbEntity).orElse(Spu.builder().build());
        BeanUtil.copyProperties(request, spu);
        spu.setProductCategory(
                ProductCategory.builder().id(request.getProductCategoryId()).build());
        if (dbEntity == null) {
            Integer nextSpuUserCode = service.getNextSpuUserCode();
            spu.setCode(nextSpuUserCode);
        }
        return spu;
    }

    @Operation(summary = "分享SPU")
    @PostMapping("/shareSpu")
    public SharedSpuResponse shareSpu(@Valid @RequestBody ShareSpuRequest request) {
        return service.shareSpu(request);
    }

    @Operation(summary = "检查预览SPU的Ticket是否有效")
    @PostMapping("/checkSpuTicket")
    public CheckSpuTicketResponse checkSpuTicket(
            @Valid @RequestBody CheckSpuTicketRequest request) {
        log.debug(
                "check spu ticket: " + request.getId() + ", ticket = " + request.getTicket() + " >> ");
        boolean valid = service.checkSpuTicket(request);
        log.debug(
                "check spu ticket: " + request.getId() + ", ticket = " + request.getTicket() + " >> " + valid);
        return CheckSpuTicketResponse.builder().ticket(request.getTicket()).id(request.getId())
                .isValid(valid).build();
    }

    @Operation(summary = "检查预览SPU的Ticket是否有效")
    @PostMapping("/generateSharedUrl")
    public String generateSharedUrl( @Valid @RequestBody GenerateSharedUrlRequest request) {
        return service.generateSharedUrl(request);
    }

    @Override
    protected String getPermissionPrefix() {
        return "spu";
    }

    @Override
    protected boolean cleanupBeforeDelete(DeleteRequest request) {
        if (request.getIds() == null || request.getIds().isEmpty()) {
            return false;
        }
        // 在删除网站之前，检查是否有子域名或其他依赖
        for (String id : request.getIds().split(",")) {
            Optional<Spu> spuOptional = service.findById(Long.valueOf(id));
            if (spuOptional.isEmpty()) {
                continue; // 如果网站不存在，跳过
            }
            service.deleteAllSpuRelatedData(spuOptional.get().getId());
        }
        return true;
    }
}
