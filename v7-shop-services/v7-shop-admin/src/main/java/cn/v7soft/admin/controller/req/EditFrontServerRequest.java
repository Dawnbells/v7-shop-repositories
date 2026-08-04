package cn.v7soft.admin.controller.req;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditFrontServerRequest extends IdRequest {

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)$");

    @NotBlank(message = "服务器名称不能为空")
    @Schema(title = "服务器名称", example = "Server-1", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "CNAME记录不能为空")
    @Schema(title = "CNAME记录", example = "eu.dwd-cname.com", description = "绑定的CNAME域名",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @jakarta.validation.constraints.Pattern(
            regexp = "^(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\\.[A-Za-z0-9-]{1,63})*\\.[A-Za-z]{2,63}$",
            message = "绑定的CNAME域名不正确")
    private String cnameRecord;

    @Schema(title = "主IP地址", example = "192.168.1.1")
    private String primaryIp;

    @Schema(title = "备用IP地址", example = "192.168.1.2")
    private String failoverIp;

    @Schema(title = "兜底IP地址", example = "192.168.1.3")
    private String fallbackIp;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @AssertTrue(message = "主IP、备用IP、兜底IP至少填写一个")
    public boolean isAtLeastOneIpConfigured() {
        return configuredIps().size() >= 1;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    @AssertTrue(message = "主IP、备用IP、兜底IP只允许填写IPv4地址")
    public boolean isEveryConfiguredIpValid() {
        return configuredIps().stream().allMatch(ip -> IPV4_PATTERN.matcher(ip).matches());
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    @AssertTrue(message = "主IP、备用IP、兜底IP不能重复")
    public boolean isEveryConfiguredIpDistinct() {
        List<String> ips = configuredIps();
        return ips.stream().distinct().count() == ips.size();
    }

    private List<String> configuredIps() {
        return Stream.of(primaryIp, failoverIp, fallbackIp)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .toList();
    }
}
