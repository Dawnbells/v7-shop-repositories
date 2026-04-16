package cn.v7soft.core.controller.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import cn.v7soft.core.enums.ClientResponseEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

@NoArgsConstructor
@SuperBuilder
@Getter
@Setter
@Slf4j
public class BasePageRequest {

    @Builder.Default
    @Min(value = 5, message = "每页条数至少5条")
    @Max(value = 100, message = "分页最多支持每页100条")
    @Schema(title = "每页条数", example = "20", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private int pageSize = 20;

    @Builder.Default
    @Min(value = 1, message = "页数从1开始")
    @Schema(title = "当前页数", example = "1", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private int pageNo = 1;

    @Builder.Default
    @Schema(title = "排序", example = "id asc", description = "允许多个排序方式，使用,分割, 排前面的优先级高", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String sortBy = "id desc";

    public void noneSortBy() {
        this.sortBy = "none";
    }

    /**
     * 转成分页信息
     *
     * @return Pageable
     */
    public Pageable toPageable() {
        if ("none".equals(sortBy)) {
            return org.springframework.data.domain.PageRequest.of(pageNo - 1, pageSize);
        }
        List<Sort.Order> orderList = new ArrayList<>();
        sortBy = Optional.ofNullable(sortBy).orElse("id asc");
        for (String sort : sortBy.split(",")) {
            String[] split = sort.trim().split(" ");
            ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(split.length == 2, "排序参数非法");
            ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(Objects.equals(split[1], "asc") || Objects.equals(split[1], "desc"), "排序参数只允许asc和desc");
            if (Objects.equals("asc", split[1].toLowerCase())) {
                orderList.add(Sort.Order.asc(split[0]));
            } else {
                orderList.add(Sort.Order.desc(split[0]));
            }
        }
        if (orderList.isEmpty()) {
            orderList.add(Sort.Order.desc("id"));
        }
        return org.springframework.data.domain.PageRequest.of(pageNo - 1, pageSize, Sort.by(orderList));
    }

}
