package cn.v7soft.admin.controller;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.EditPixelAccountRequest;
import cn.v7soft.admin.controller.req.QueryPixelAccountRequest;
import cn.v7soft.admin.controller.resp.PixelAccountResponse;
import cn.v7soft.admin.service.IPixelAccountService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.controller.request.attributes.LikeAttribute;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.PixelAccount;
import cn.v7soft.dao.entities.primary.Spu;
import cn.v7soft.dao.enums.PixelAccountPlatform;
import cn.v7soft.dao.enums.PixelAccountState;
import cn.v7soft.dao.enums.PixelTrackingType;
import cn.v7soft.dao.tenant.WebsiteContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@Validated
@RestController
@RequestMapping("/pixel-account")
@Tag(name = "像素账号管理")
public class PixelAccountController extends BaseDataRangeController<PixelAccount, IPixelAccountService, PixelAccountResponse, QueryPixelAccountRequest, EditPixelAccountRequest> {

    protected PixelAccountController(IPixelAccountService service) {
        super(service);
    }

    @Override
    protected QueryPageRequest<PixelAccount> convertQueryPageRequest(QueryPixelAccountRequest request) {
        return super.convertQueryPageRequest(request).addConstraint(WebsiteContext.isWebsiteAdmin(), new QueryAttribute() {
            @Override
            public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
                return criteriaBuilder.equal(root.get("website").get("id"), WebsiteContext.getCurrentWebsiteId());
            }
        }).addConstraint(!WebsiteContext.isWebsiteAdmin(), new QueryAttribute() {
            @Override
            public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
                return criteriaBuilder.isNull(root.get("website"));
            }
        }).addConstraint(StrUtil.isNotBlank(request.getTitle()), LikeAttribute.builder().name("pixelName").value(request.getTitle()).build());
    }

    @Override
    protected PixelAccountResponse convertEntity(PixelAccount pixelAccount) {
        return PixelAccountResponse.convertEntity(pixelAccount);
    }

    @Override
    protected PixelAccount convertRequest(@Nullable PixelAccount dbEntity, EditPixelAccountRequest request) {
        PixelAccount pixelAccount = Optional.ofNullable(dbEntity).orElse(PixelAccount.builder().build());
        BeanUtil.copyProperties(request, pixelAccount);
        pixelAccount.setState(PixelAccountState.WAIT_VALID);
        if (WebsiteContext.isWebsiteAdmin()) {
            pixelAccount.setWebsite(WebsiteContext.getCurrentWebsite());
        } else {
            pixelAccount.setWebsite(null);
        }
        if (request.getTrackingType() == PixelTrackingType.GLOBAL || request.getProductIds() == null || request.getProductIds().isEmpty()) {
            pixelAccount.getSpuList().clear();
        } else {
            pixelAccount.setSpuList(request.getProductIds().stream().map((Function<String, Spu>) s -> Spu.builder().id(Long.valueOf(s)).build()).collect(Collectors.toList()));
        }
        return pixelAccount;
    }

    @Override
    protected boolean cleanupBeforeDelete(DeleteRequest request) {
        for (String s : request.getIds().split(",")) {
            service.findById(Long.valueOf(s)).ifPresent(pixelAccount -> {
                pixelAccount.getSpuList().clear();
            });
        }
        return true;
    }

    @Override
    protected String getPermissionPrefix() {
        return "pixelAccount";
    }

    @Operation(summary = "远程搜索")
    @GetMapping("/remoteQuery")
    public List<PixelAccountResponse> remoteQuery(
            @RequestParam("query") String query,
            @RequestParam(value = "platform", required = false) String platform) {
        QueryPageRequest<PixelAccount> request = QueryPageRequest.fromRequest(QueryPixelAccountRequest.builder().pageNo(1).build());
        if (StringUtils.hasText(query)) {
            request.add(LikeAttribute.builder().name("pixelName").value(query.trim()).build())
                    .add(EqualsQueryAttribute.builder().name("status").value(StatusEnum.VALID).build());
        }
        request.add(new QueryAttribute() {
            @Override
            public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
                return criteriaBuilder.isNull(root.get("website"));
            }
        });
        if (StringUtils.hasText(platform)) {
            request.add(EqualsQueryAttribute.builder().name("platform").value(PixelAccountPlatform.valueOf(platform)).build());
        }
        return service.findPaginated(request)
                .stream()
                .map(this::convertEntityCopyId)
                .collect(Collectors.toList());
    }
}
