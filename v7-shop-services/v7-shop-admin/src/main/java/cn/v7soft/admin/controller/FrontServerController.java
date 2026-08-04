package cn.v7soft.admin.controller;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.EditFrontServerRequest;
import cn.v7soft.admin.controller.req.QueryFrontServerRequest;
import cn.v7soft.admin.controller.req.QueryLanguageRequest;
import cn.v7soft.admin.service.IFrontServerService;
import cn.v7soft.common.controller.resp.FrontServerResponse;
import cn.v7soft.core.controller.BaseController;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.controller.request.attributes.LikeAttribute;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.DnsSwitchLog;
import cn.v7soft.dao.entities.primary.FrontServer;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.repositories.primary.DnsSwitchLogRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Validated
@RestController
@RequestMapping("/front-server")
@Tag(name = "前端服务器管理")
public class FrontServerController extends BaseController<FrontServer, IFrontServerService, FrontServerResponse, QueryFrontServerRequest, EditFrontServerRequest> {
    private static final String LEGACY_HEALTH_CHECK_VALUE = "/health";

    private final DnsSwitchLogRepository dnsSwitchLogRepository;

    protected FrontServerController(IFrontServerService service, DnsSwitchLogRepository dnsSwitchLogRepository) {
        super(service);
        this.dnsSwitchLogRepository = dnsSwitchLogRepository;
    }

    @Override
    protected FrontServerResponse convertEntity(FrontServer frontServer) {
        return FrontServerResponse.convertEntity(frontServer);
    }

    @Override
    protected FrontServer convertRequest(@Nullable FrontServer dbEntity, EditFrontServerRequest request) {
        FrontServer frontServer = Optional.ofNullable(dbEntity).orElse(FrontServer.builder().build());
        frontServer.setName(request.getName().trim());
        frontServer.setCnameRecord(request.getCnameRecord().trim());
        frontServer.setPrimaryIp(trimToEmpty(request.getPrimaryIp()));
        frontServer.setFailoverIp(trimToEmpty(request.getFailoverIp()));
        frontServer.setFallbackIp(trimToEmpty(request.getFallbackIp()));
        if (StrUtil.isBlank(frontServer.getHealthCheckUrl())) {
            frontServer.setHealthCheckUrl(LEGACY_HEALTH_CHECK_VALUE);
        }
        return frontServer;
    }

    @GetMapping("/by-name")
    public FrontServerResponse getFrontServersByName(@RequestParam String name) {
        return FrontServerResponse.convertEntity(service.getFrontServersByName(name));
    }

    @GetMapping("/by-resolution-count")
    public List<FrontServerResponse> getServersByActiveResolutionCount(@RequestParam int minCount) {
        return service.getServersByActiveResolutionCount(minCount).stream()
                .map(this::convertEntity)
                .toList();
    }

    @Override
    protected String getPermissionPrefix() {
        return "front-server";
    }

    @Operation(summary = "远程搜索")
    @GetMapping("/remoteQuery")
    public List<FrontServerResponse> remoteQuery(@RequestParam("query") String query) {
        QueryPageRequest<FrontServer> request = QueryPageRequest.fromRequest(
                QueryLanguageRequest.builder().pageNo(1).build());
        if (StringUtils.hasText(query)) {
            request.or()
                    .add(LikeAttribute.builder().name("cnameRecord").value("%" + query.trim() + "%").build())
                    .add(LikeAttribute.builder().name("primaryIp").value("%" + query.trim() + "%").build())
                    .add(LikeAttribute.builder().name("failoverIp").value("%" + query.trim() + "%").build())
                    .add(LikeAttribute.builder().name("fallbackIp").value("%" + query.trim() + "%").build())
                    .next()
                    .add(EqualsQueryAttribute.builder().name("status").value(StatusEnum.VALID).build());
        }

        return service.findPaginated(request).stream()
                .map(this::convertEntityCopyId)
                .collect(Collectors.toList());
    }

    @SaCheckLogin
    @Operation(summary = "查询未确认的DNS切换记录")
    @GetMapping("/unacknowledged-switches")
    public List<DnsSwitchLog> getUnacknowledgedSwitches() {
        SystemUserDto currentUser = SaSessionUtil.getLoginUser();
        if (currentUser.getUserType() != SystemUserType.ADMIN) {
            return Collections.emptyList();
        }
        return dnsSwitchLogRepository.findByAcknowledgedFalseOrderBySwitchedAtDesc();
    }

    @SaCheckLogin
    @Operation(summary = "确认DNS切换记录")
    @PostMapping("/acknowledge-switch/{id}")
    public void acknowledgeSwitch(@PathVariable Long id) {
        SystemUserDto currentUser = SaSessionUtil.getLoginUser();
        if (currentUser.getUserType() != SystemUserType.ADMIN) {
            return;
        }
        dnsSwitchLogRepository.findById(id).ifPresent(log -> {
            log.setAcknowledged(true);
            dnsSwitchLogRepository.save(log);
        });
    }

    private static String trimToEmpty(String value) {
        return StrUtil.isBlank(value) ? "" : value.trim();
    }
}
