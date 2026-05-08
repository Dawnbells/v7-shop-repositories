package cn.v7soft.admin.service.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ShoplineOrderLoadResult {
    private String nextPageInfo;
    private int fetchedCount;
    private int successCount;
    private int failedCount;
    private int skippedCount;
    private int createdCount;

    /**
     * 本页用于推进 since_id 的游标。仅取已成功处理（created/skipped）且位于第一个失败单之前的订单的最大 id。
     * 若整页中第一个订单就失败，则为 null（保留旧游标）。
     */
    private String cursorOrderId;

    /**
     * 本页用于推进 created_at_min 的游标，配合 cursorOrderId 同步推进。
     */
    private LocalDateTime cursorOrderTime;

    public static ShoplineOrderLoadResult empty(String nextPageInfo) {
        return ShoplineOrderLoadResult.builder()
                .nextPageInfo(nextPageInfo)
                .build();
    }
}
