package cn.v7soft.admin.controller.req;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class EditProductSpecificationAttribute {
    @NotBlank(message = "规格属性名不能为空")
    @Schema(title = "attribute name", example = "Color")
    private String name;
    @NotBlank(message = "规格属性值不能为空")
    @Schema(title = "attribute ", example = "Red")
    private String value;
    @Schema(title = "图片ID ", example = "图片")
    private IdRequest image;
}
