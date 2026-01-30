package cn.v7soft.admin.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.EditIpBlacklistRequest;
import cn.v7soft.admin.controller.req.QueryIpBlacklistRequest;
import cn.v7soft.admin.controller.resp.IpBlacklistResponse;
import cn.v7soft.admin.service.IIpBlacklistService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.IpBlacklist;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/ip-blacklist")
@Tag(name = "系统安全-IP黑名单管理")
@Validated
@RequiredArgsConstructor
public class IpBlacklistController {
    private final IIpBlacklistService service;

    @PostMapping("/page")
    @Operation(summary = "分页查询")
    public Page<IpBlacklistResponse> page(@Valid @RequestBody QueryIpBlacklistRequest request) {
        String permission = getPermissionPrefix() + ".page";
        StpUtil.checkPermission(permission);
        // 调用远程cloak服务查询IP黑名单
        // 远程接口page从0开始，本地pageNo从1开始，需要转换
        int page = request.getPageNo() - 1;
        int size = request.getPageSize();
        String query = request.getTitle();
        return service.searchFromRemote(query, page, size);
    }

    @PostMapping("/doEdit")
    @Operation(summary = "更新或编辑")
    public IpBlacklistResponse doEdit(@Valid @RequestBody EditIpBlacklistRequest request) {
        String permission = getPermissionPrefix() + (request.getId() == null ? ".create" : ".update");
        StpUtil.checkPermission(permission);

        if (StrUtil.isBlank(request.getId())) {
            // 创建新记录
            return service.createRemote(request);
        } else {
            // 更新现有记录
            Long id = Long.parseLong(request.getId());
            return service.updateRemote(id, request);
        }
    }

    @PostMapping("/doDelete")
    @Operation(summary = "根据ID删除")
    public void doDelete(@Valid @RequestBody DeleteRequest request) {
        String permission = getPermissionPrefix() + ".delete";
        StpUtil.checkPermission(permission);

        List<Long> ids = null;
        try {
            ids = Arrays.stream(request.getIds().split(","))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            ClientResponseEnum.PARAMETER_ILLEGAL.throwException("IDS参数错: " + request.getIds());
        }
        ClientResponseEnum.PARAMETER_ILLEGAL.notEmpty(ids, "IDS参数为空");

        service.deleteRemote(ids);
    }

    protected String getPermissionPrefix() {
        return "ip-blacklist";
    }
}
