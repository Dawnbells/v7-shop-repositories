package cn.v7soft.admin.service.dto;

import java.time.LocalDateTime;

/** 取消检查的时间边界与续页游标，必须一起持久化。 */
public record ShopifyCancelCheckResult(LocalDateTime cursor, String nextPageInfo) {
}
