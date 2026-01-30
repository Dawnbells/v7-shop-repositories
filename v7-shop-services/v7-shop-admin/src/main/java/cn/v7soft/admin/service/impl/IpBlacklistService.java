package cn.v7soft.admin.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.controller.req.EditIpBlacklistRequest;
import cn.v7soft.admin.controller.resp.IpBlacklistResponse;
import cn.v7soft.admin.service.IIpBlacklistService;
import cn.v7soft.common.controller.req.attributes.AccessDataRangeAttribute;
import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.IpBlacklist;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.properties.CloakCenterProperty;
import cn.v7soft.dao.repositories.primary.IpBlacklistRepository;
import cn.v7soft.dao.tenant.TenantContext;
import cn.v7soft.dao.utils.SaSessionUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class IpBlacklistService extends BaseDataRangeService<IpBlacklist, IpBlacklistRepository>
        implements IIpBlacklistService {

    private final CloakCenterProperty cloakCenterProperty;

    public IpBlacklistService(IpBlacklistRepository repository, CloakCenterProperty cloakCenterProperty) {
        super(repository);
        this.cloakCenterProperty = cloakCenterProperty;
    }

    @Override
    protected void checkKeyConstraint(IpBlacklist entity) {
        boolean exists = repository.existsByIpAddressAndFingerprint(entity.getIpAddress(), entity.getFingerprint());
        if (exists && (entity.getId() == null)) {
            throw new IllegalArgumentException("该IP和终端已存在黑名单中");
        }
    }

    @Override
    public QueryAttribute getAccessDataRangeQueryAttribute() {
        return new AccessDataRangeAttribute(AccessDataRangeLevel.COMPANY);
    }

    @Override
    public Page<IpBlacklistResponse> searchFromRemote(String query, int page, int size) {
        String token = obtainCloakToken();
        return searchIpBlacklist(token, query, page, size);
    }

    /**
     * 获取cloak服务的token
     */
    private String obtainCloakToken() {
        SystemUserDto currentUser = SaSessionUtil.getLoginUser();
        String accessKey = TenantContext.getCurrentTenantEntity().getAccessKey();
        String baseUrl = cloakCenterProperty.getBaseUrl();
        final String cloakTokenUrl = baseUrl + "/cloak/simple/token";

        Map<String, Object> params = new HashMap<>();
        SystemUserType userType = currentUser.getUserType();

        if (userType == SystemUserType.ADMIN || userType == SystemUserType.COMPANY_ADMIN) {
            params.put("accessKeys", accessKey);
        } else if (userType == SystemUserType.DEPARTMENT_MANAGER
                   || userType == SystemUserType.DEEP_DEPARTMENT_MANAGER
                   || userType == SystemUserType.DEPARTMENT_TREE) {
            params.put("accessKeys", accessKey);
            if (currentUser.getAccessDepartmentIds() != null && !currentUser.getAccessDepartmentIds().isEmpty()) {
                String deptIds = currentUser.getAccessDepartmentIds().stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));
                params.put("deptIds", deptIds);
            }
        } else {
            params.put("accessKeys", accessKey);
            params.put("userIds", currentUser.getId());
        }

        String response = HttpUtil.post(cloakTokenUrl, params);
        JSONObject result = JSONUtil.parseObj(response);

        if (!result.getBool("success", false)) {
            log.error("获取cloak token失败: {}", response);
            throw new RuntimeException("获取IP黑名单token失败");
        }

        return result.getStr("token");
    }

    /**
     * 调用远程接口搜索IP黑名单
     */
    private Page<IpBlacklistResponse> searchIpBlacklist(String token, String query, int page, int size) {
        String baseUrl = cloakCenterProperty.getBaseUrl();
        StringBuilder urlBuilder = new StringBuilder(baseUrl)
                .append("/ip-blacklist/search?token=").append(token)
                .append("&page=").append(page)
                .append("&size=").append(size);

        if (StrUtil.isNotBlank(query)) {
            urlBuilder.append("&query=").append(query);
        }

        String response = HttpUtil.get(urlBuilder.toString());
        JSONObject result = JSONUtil.parseObj(response);
        log.debug("searchIpBlacklist, url = {}, response = {}", urlBuilder.toString(), response);

        String code = result.getStr("code", "500");
        if (!"200".equals(code)) {
            String message = result.getStr("msg", "查询失败");
            log.error("查询IP黑名单失败: {}", response);
            throw new RuntimeException(message);
        }

        List<IpBlacklistResponse> content = new ArrayList<>();
        JSONObject data = result.getJSONObject("data");
        if (data != null) {
            JSONArray contentArray = data.getJSONArray("content");
            if (contentArray != null) {
                for (int i = 0; i < contentArray.size(); i++) {
                    JSONObject item = contentArray.getJSONObject(i);
                    content.add(parseIpBlacklistResponse(item));
                }
            }
        }

        long totalElements = data != null ? data.getLong("totalElements", 0L) : 0L;
        return new PageImpl<>(content, PageRequest.of(page, size), totalElements);
    }

    @Override
    public IpBlacklistResponse createRemote(EditIpBlacklistRequest request) {
        String token = obtainCloakToken();
        String baseUrl = cloakCenterProperty.getBaseUrl();
        String url = baseUrl + "/ip-blacklist?token=" + token;

        // 构建请求体
        JSONObject body = new JSONObject();
        body.set("ipAddress", request.getIpAddress());
        body.set("fingerprint", request.getFingerprint());
        body.set("remark", request.getRemark());

        // 添加用户和部门信息
        SystemUserDto currentUser = SaSessionUtil.getLoginUser();
        body.set("userId", currentUser.getLongId());
        body.set("deptId", currentUser.getDepartmentId());
        body.set("accessKey", TenantContext.getCurrentTenantEntity().getAccessKey());

        String response = HttpRequest.post(url)
                .body(body.toString())
                .contentType("application/json")
                .execute()
                .body();

        JSONObject result = JSONUtil.parseObj(response);
        String code = result.getStr("code", "500");
        if (!"200".equals(code)) {
            String message = result.getStr("message", "创建失败");
            log.error("创建IP黑名单失败: {}", response);
            throw new RuntimeException(message);
        }

        JSONObject data = result.getJSONObject("data");
        return parseIpBlacklistResponse(data);
    }

    @Override
    public IpBlacklistResponse updateRemote(Long id, EditIpBlacklistRequest request) {
        String token = obtainCloakToken();
        String baseUrl = cloakCenterProperty.getBaseUrl();
        String url = baseUrl + "/ip-blacklist/" + id + "?token=" + token;

        // 构建请求体
        JSONObject body = new JSONObject();
        body.set("ipAddress", request.getIpAddress());
        body.set("fingerprint", request.getFingerprint());
        body.set("remark", request.getRemark());

        String response = HttpRequest.put(url)
                .body(body.toString())
                .contentType("application/json")
                .execute()
                .body();

        JSONObject result = JSONUtil.parseObj(response);
        String code = result.getStr("code", "500");
        if (!"200".equals(code)) {
            String message = result.getStr("message", "更新失败");
            log.error("更新IP黑名单失败: {}", response);
            throw new RuntimeException(message);
        }

        JSONObject data = result.getJSONObject("data");
        return parseIpBlacklistResponse(data);
    }

    @Override
    public void deleteRemote(List<Long> ids) {
        String token = obtainCloakToken();
        String baseUrl = cloakCenterProperty.getBaseUrl();

        for (Long id : ids) {
            String url = baseUrl + "/ip-blacklist/" + id + "?token=" + token;
            String response = HttpRequest.delete(url)
                    .execute()
                    .body();

            JSONObject result = JSONUtil.parseObj(response);
            String code = result.getStr("code", "500");
            if (!"200".equals(code)) {
                String message = result.getStr("message", "删除失败");
                log.error("删除IP黑名单失败, id={}: {}", id, response);
                throw new RuntimeException(message);
            }
        }
    }

    /**
     * 解析远程返回的IP黑名单响应
     */
    private IpBlacklistResponse parseIpBlacklistResponse(JSONObject item) {
        if (item == null) {
            return null;
        }
        IpBlacklistResponse resp = IpBlacklistResponse.builder()
                .ipAddress(item.getStr("ipAddress"))
                .fingerprint(item.getStr("fingerprint"))
                .remark(item.getStr("remark"))
                .build();
        resp.setId(item.getStr("id"));
        return resp;
    }
}
