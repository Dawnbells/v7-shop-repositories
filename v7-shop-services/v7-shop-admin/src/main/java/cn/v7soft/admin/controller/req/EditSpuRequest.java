package cn.v7soft.admin.controller.req;


import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 编辑SPU请求类。
 */
@Getter
@Setter
public class EditSpuRequest extends IdRequest {
    @NotBlank(message = "商品名称不能为空")
    @Schema(title = "商品名称", example = "商品名称")
    private String name;

    @Schema(title = "商品描述", example = "商品描述")
    private String description;

    @Schema(title = "每个用户的编码", example = "商品描编码")
    private int userCode;

    @Schema(title = "产品分类ID", example = "1")
    private Long productCategoryId;
}