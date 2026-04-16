package cn.v7soft.core.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
public final class PageWrapper<T> {
    @Schema(title = "返回数组", example = "[{}, {}]")
    private final List<T> list;
    @Schema(title = "总条数", example = "20")
    private final long total;
    @Schema(title = "页数", example = "1")
    private final int pageNo;
    @Schema(title = "每页条数", example = "10")
    private final int pageSize;
    @Schema(title = "总页数", example = "10")
    private final int totalPages;

    public PageWrapper(Page<T> page) {
        this.list = page.get().toList();
        this.total = page.getTotalElements();
        this.pageNo = page.getPageable().getPageNumber() + 1;
        this.pageSize = page.getPageable().getPageSize();
        this.totalPages = page.getTotalPages();
    }
}
