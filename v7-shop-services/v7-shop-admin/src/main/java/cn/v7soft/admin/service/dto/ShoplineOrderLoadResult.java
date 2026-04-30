package cn.v7soft.admin.service.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ShoplineOrderLoadResult {
    private String nextPageInfo;
    private int fetchedCount;
    private int successCount;
    private int failedCount;
    private int skippedCount;
    private int createdCount;

    public static ShoplineOrderLoadResult empty(String nextPageInfo) {
        return ShoplineOrderLoadResult.builder()
                .nextPageInfo(nextPageInfo)
                .build();
    }
}
