package cn.v7soft.admin.controller.resp;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.utils.DateTimeHelper;
import cn.v7soft.dao.entities.primary.OrderBotCheckInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@Schema(description = "机审响应")
public class BotOrderCheckInfoResponse {

    /**
     * 是否测试单
     * 【测试单】
     */
    @Schema(title = "是否测试单", example = "true")
    private boolean isTestOrder;
    /**
     * 偏远提示信息，isTestOrder = true才有效
     *
     * @see #isTestOrder
     */
    @Schema(title = "偏远提示信息", example = "true")
    private String remoteTip;
    /**
     * 是否斗篷单
     * 【斗篷单】
     */
    @Schema(title = "是否斗篷单", example = "true")
    private boolean isCloakOrder;
    /**
     * 是否偏远地区
     * 【偏远地区】
     */
    @Schema(title = "是否偏远地区", example = "true")
    private boolean isRemoteArea;
    /*
     * 是否电话号码有误
     * 【电话号码有误】
     */
    @Schema(title = "是否电话号码有误", example = "true")
    private boolean isInvalidPhone;

    /*
     * 是否地址为纯文字（地址不全）
     * 【地址不全-纯文字】
     */
    @Schema(title = "是否地址为纯文字（地址不全）", example = "true")
    private boolean isIncompletePlainTextAddress;

    /*
     * 是否地址为纯数字（地址不全）
     * 【地址不全-纯数字】
     */
    @Schema(title = "是否地址为纯数字（地址不全）", example = "true")
    private boolean isIncompletePureNumbersAddress;

    /*
     * 是否邮箱缺失
     * 【邮箱缺失】
     */
    @Schema(title = "是否邮箱缺失", example = "true")
    private boolean isEmailMissing;

    /*
     * 是否邮箱格式有误
     * 【邮箱有误】
     */
    @Schema(title = "是否邮箱格式有误", example = "true")
    private boolean isInvalidEmail;

    /*
     * 是否产品数量大于2
     * 【产品数量＞2】
     */
    @Schema(title = "是否产品数量大于2", example = "true")
    private boolean isMoreThanTwoProducts;

    @Schema(title = "是否是客诉留言", example = "true")
    private boolean hasRemarkRisk;

    @Schema(title = "IP不一致", example = "true")
    private boolean isIpConflict;

    @Schema(title = "分险检测IP", example = "true")
    private String conflictIp;

    @Schema(title = "客诉留言详情", example = "true")
    private RemarkRiskDetailResponse remarkRiskDetail;

    @Schema(title = "终端重复数", example = "true")
    private int deviceRepeatCount;

    @Schema(title = "名字重复数", example = "true")
    private int nameRepeatCount;

    @Schema(title = "电话重复", example = "true")
    private int phoneRepeatCount;

    @Schema(title = "IP重复", example = "true")
    private int remoteIpRepeatCount;

    @Schema(title = "真实IP重复", example = "true")
    private int realIpRepeatCount;
    @Schema(title = "上次同电话下单秒数（默认 -1 表示无记录）", example = "true")
    private String phoneSecondsBetween;
    @Schema(title = "上次同IP下单秒数（默认 -1 表示无记录）", example = "true")
    private String ipSecondsBetween;

    public static BotOrderCheckInfoResponse convert(OrderBotCheckInfo botOrderCheckInfo) {
        if (botOrderCheckInfo == null) {
            return BotOrderCheckInfoResponse.builder().build();
        }
        RemarkRiskDetailResponse remarkRiskDetailResponse = RemarkRiskDetailResponse.builder().build();
        if (Boolean.TRUE.equals(botOrderCheckInfo.getHasRemarkRisk()) && StrUtil.isNotBlank(botOrderCheckInfo.getRemarkRiskDetail())) {
            remarkRiskDetailResponse = JSONUtil.toBean(botOrderCheckInfo.getRemarkRiskDetail(), RemarkRiskDetailResponse.class);
        }
        return BotOrderCheckInfoResponse.builder()
                .isRemoteArea(botOrderCheckInfo.isRemoteArea())
                .remoteTip(botOrderCheckInfo.getRemoteTip())
                .isTestOrder(botOrderCheckInfo.isTestOrder())
                .isCloakOrder(botOrderCheckInfo.isCloakOrder())
                .isInvalidPhone(botOrderCheckInfo.isInvalidPhone())
                .isIncompletePlainTextAddress(botOrderCheckInfo.isIncompletePlainTextAddress())
                .isIncompletePureNumbersAddress(botOrderCheckInfo.isIncompletePureNumbersAddress())
                .isEmailMissing(botOrderCheckInfo.isEmailMissing())
                .isInvalidEmail(botOrderCheckInfo.isInvalidEmail())
                .isIpConflict(botOrderCheckInfo.isIpConflict())
                .deviceRepeatCount(botOrderCheckInfo.getDeviceRepeatCount())
                .nameRepeatCount(botOrderCheckInfo.getNameRepeatCount())
                .phoneRepeatCount(botOrderCheckInfo.getPhoneRepeatCount())
                .remoteIpRepeatCount(botOrderCheckInfo.getRemoteIpRepeatCount())
                .realIpRepeatCount(botOrderCheckInfo.getRealIpRepeatCount())
                .conflictIp(botOrderCheckInfo.getConflictIp())
                .isMoreThanTwoProducts(botOrderCheckInfo.isMoreThanTwoProducts())
                .hasRemarkRisk(Boolean.TRUE.equals(botOrderCheckInfo.getHasRemarkRisk()))
                .remarkRiskDetail(remarkRiskDetailResponse)
                .ipSecondsBetween(DateTimeHelper.formatSecondsManual(botOrderCheckInfo.getIpSecondsBetween()))
                .phoneSecondsBetween(DateTimeHelper.formatSecondsManual(botOrderCheckInfo.getPhoneSecondsBetween()))
                .build();
    }
}
