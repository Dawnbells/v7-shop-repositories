package cn.v7soft.admin.controller.req;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "批量增删商品中文品名字段请求")
public class BatchEditMerchandiseRequest {

    public enum Scope {
        SELECTED,
        OWNED_ALL
    }

    public enum Operation {
        ADD,
        REMOVE
    }

    public enum EmptyResultPolicy {
        SKIP,
        KEEP_EMPTY
    }

    @NotNull(message = "作用范围不能为空")
    @Schema(title = "作用范围：SELECTED-选中SPU，OWNED_ALL-本人名下全部SPU")
    private Scope scope;

    @Schema(title = "选中的SPU ID列表，仅SELECTED模式使用", example = "[10, 11]")
    private List<@NotNull(message = "SPU ID不能为空") Long> spuIds;

    @NotNull(message = "操作类型不能为空")
    @Schema(title = "操作类型：ADD-增加，REMOVE-删减")
    private Operation operation;

    @NotBlank(message = "字段不能为空")
    @Size(max = 512, message = "字段不能超过512个字符")
    @Schema(title = "待增加或删减的单个字段", example = "万能工具套装")
    private String field;

    @NotBlank(message = "分隔符不能为空")
    @Schema(title = "单字符分隔符", example = "/", defaultValue = "/")
    private String delimiter = "/";

    @NotNull(message = "删空策略不能为空")
    @Schema(title = "删空策略：SKIP-跳过，KEEP_EMPTY-保留空", defaultValue = "SKIP")
    private EmptyResultPolicy emptyResultPolicy = EmptyResultPolicy.SKIP;

    @AssertTrue(message = "SELECTED模式必须选择至少一个SPU，OWNED_ALL模式不允许传SPU ID")
    @Schema(hidden = true)
    public boolean isScopeSelectionValid() {
        if (scope == null) {
            return true;
        }
        if (scope == Scope.SELECTED) {
            return spuIds != null && !spuIds.isEmpty();
        }
        return spuIds == null || spuIds.isEmpty();
    }

    @AssertTrue(message = "分隔符必须是一个非空白且非等号的字符")
    @Schema(hidden = true)
    public boolean isDelimiterValid() {
        if (delimiter == null || delimiter.isEmpty()) {
            return true;
        }
        return delimiter.codePointCount(0, delimiter.length()) == 1
               && delimiter.codePoints().noneMatch(Character::isWhitespace)
               && !"=".equals(delimiter);
    }

    @AssertTrue(message = "字段不能包含当前分隔符")
    @Schema(hidden = true)
    public boolean isFieldDelimiterValid() {
        if (field == null || delimiter == null || delimiter.isEmpty()) {
            return true;
        }
        return !field.trim().contains(delimiter);
    }
}
