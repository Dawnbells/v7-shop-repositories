package cn.v7soft.dao.entities.primary;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.core.entities.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 机器人审单信息
 */
@SuperBuilder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "t_order_bot_check_infos", indexes = {
        @Index(name = "idx_device_repeat_count", columnList = "device_repeat_count"),
        @Index(name = "idx_name_repeat_count", columnList = "name_repeat_count"),
        @Index(name = "idx_phone_repeat_count", columnList = "phone_repeat_count"),
        @Index(name = "idx_remote_ip_repeat_count", columnList = "remote_ip_repeat_count"),
        @Index(name = "idx_real_ip_repeat_count", columnList = "real_ip_repeat_count"),
})
public class OrderBotCheckInfo extends BaseEntity {

    /**
     * 是否偏远地区
     * 【偏远地区】
     */
    @Builder.Default
    @Column(name = "is_remote_area", nullable = false)
    private boolean isRemoteArea = false;
    /**
     * 偏远地区提示
     */
    @Builder.Default
    @Column(name = "remote_tip")
    private String remoteTip = "";
    /**
     * 是否测试单
     * 【测试单】
     */
    @Builder.Default
    @Column(name = "is_test_order", nullable = false)
    private boolean isTestOrder = false;
    /**
     * 是否斗篷单
     * 【斗篷单】
     */
    @Builder.Default
    @Column(name = "is_cloak_order", nullable = false)
    private boolean isCloakOrder = false;
    /**
     * 是否电话号码有误
     * 【电话号码有误】
     */
    @Builder.Default
    @Column(name = "is_invalid_phone", nullable = false)
    private boolean isInvalidPhone = false;

    /**
     * 是否地址为纯文字（地址不全）
     * 【地址不全-纯文字】
     */
    @Builder.Default
    @Column(name = "is_incomplete_plain_text_address", nullable = false)
    private boolean isIncompletePlainTextAddress = false;

    /**
     * 是否地址为纯数字（地址不全）
     * 【地址不全-纯数字】
     */
    @Builder.Default
    @Column(name = "is_incomplete_pure_numbers_address", nullable = false)
    private boolean isIncompletePureNumbersAddress = false;

    /**
     * 是否邮箱缺失
     * 【邮箱缺失】
     */
    @Builder.Default
    @Column(name = "is_email_missing", nullable = false)
    private boolean isEmailMissing = false;

    /**
     * 是否邮箱格式有误
     * 【邮箱有误】
     */
    @Builder.Default
    @Column(name = "is_invalid_email", nullable = false)
    private boolean isInvalidEmail = false;

    /**
     * 是否产品数量大于2
     * 【产品数量＞2】
     */
    @Builder.Default
    @Column(name = "is_more_than_two_products", nullable = false)
    private boolean isMoreThanTwoProducts = false;

    /**
     * 下单IP和风险监测IP不一致
     */
    @Builder.Default
    @Column(name = "is_ip_conflict", nullable = false)
    private boolean isIpConflict = false;

    @Builder.Default
    @Column(name = "conflict_ip", nullable = false)
    private String conflictIp = "";
    /**
     * 终端重复数
     */
    @Column(name = "device_repeat_count", columnDefinition = "int default 0")
    private int deviceRepeatCount;

    /**
     * 名字重复数
     */
    @Column(name = "name_repeat_count", columnDefinition = "int default 0")
    private int nameRepeatCount;

    /**
     * 电话重复订单ID
     */
    @Column(name = "phone_repeat_count", columnDefinition = "int default 0")
    private int phoneRepeatCount;

    /**
     * IP重复订单ID
     */
    @Column(name = "remote_ip_repeat_count", columnDefinition = "int default 0")
    private int remoteIpRepeatCount;
    /**
     * 真实IP重复订单ID， JSON数组格式，每个真实IP对应的重复订单ID
     */
    @Column(name = "real_ip_repeat_count", columnDefinition = "int default 0")
    private int realIpRepeatCount;

    @Column(name = "remark_risk")
    private Boolean hasRemarkRisk;

    @Column(name = "remark_risk_detail", length = 1024)
    private String remarkRiskDetail;
    /**
     * 上次同电话下单秒数（默认 -1 表示无记录）
     */
    @Column(name = "phone_seconds_between", columnDefinition = "bigint default -1")
    private Long phoneSecondsBetween; /**
     * 上次同IP下单秒数（默认 -1 表示无记录）
     */
    @Column(name = "ip_seconds_between", columnDefinition = "bigint default -1")
    private Long ipSecondsBetween;
    /**
     * 生成提示信息
     */
    public String toTip() {
        StringBuilder stringBuilder = new StringBuilder();
        if (isTestOrder) {
            stringBuilder.append("测试单").append(", ");
        }
        if (isCloakOrder) {
            stringBuilder.append("斗篷单").append(", ");
        }
        if (isInvalidPhone) {
            stringBuilder.append("电话号码有误").append(", ");
        }
        if (isIncompletePlainTextAddress) {
            stringBuilder.append("地址不全-纯文字").append(", ");
        }
        if (isIncompletePureNumbersAddress) {
            stringBuilder.append("地址不全-纯数字").append(", ");
        }
        if (isEmailMissing) {
            stringBuilder.append("邮箱缺失").append(", ");
        }
        if (isInvalidEmail) {
            stringBuilder.append("邮箱有误").append(", ");
        }
        if (isMoreThanTwoProducts) {
            stringBuilder.append("产品数量＞2").append(", ");
        }
        if (Boolean.TRUE.equals(hasRemarkRisk)) {
            stringBuilder.append("疑似客诉").append(", ");
        }
        if (isIpConflict) {
            stringBuilder.append("IP不一致").append(", ");
        }
        if (isRemoteArea) {
            stringBuilder.append("偏远地区");
            if (StrUtil.isNotBlank(remoteTip)) {
                stringBuilder.append("(").append(remoteTip).append(")");
            }
            stringBuilder.append(", ");
        }
        if (stringBuilder.isEmpty()) {
            return "";
        }
        return stringBuilder.substring(0, stringBuilder.length() - 2);
    }
}
