package cn.v7soft.admin.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@Schema(description = "批量增删商品中文品名字段结果")
public class BatchEditMerchandiseResponse {

    @Schema(title = "目标SPU数")
    private long targetSpuCount;

    @Schema(title = "目标商品数")
    private long targetProductCount;

    @Schema(title = "原始中文名称精确匹配的商品数")
    private long matchedProductCount;

    @Schema(title = "原始中文名称不匹配而跳过的商品数")
    private long originalMismatchCount;

    @Schema(title = "实际更新商品数")
    private long updatedProductCount;

    @Schema(title = "增加时因字段已存在而跳过的商品数")
    private long alreadyExistsCount;

    @Schema(title = "删减时因未找到字段而跳过的商品数")
    private long notFoundCount;

    @Schema(title = "删减时因结果为空且选择跳过的商品数")
    private long emptySkippedCount;

    @Schema(title = "删减后实际保留为空的商品数")
    private long emptiedProductCount;
}
