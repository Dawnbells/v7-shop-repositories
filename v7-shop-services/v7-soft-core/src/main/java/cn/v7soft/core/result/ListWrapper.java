package cn.v7soft.core.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
public final class ListWrapper<T> {
    @Schema(title = "返回数组", example = "[{}, {}]")
    private final List<T> list;
    @Schema(title = "总条数", example = "2")
    private final int total;

    public ListWrapper(List<T> data) {
        this.list = Optional.ofNullable(data).orElse(new ArrayList<>());
        this.total = this.list.size();
    }
}
