package cn.v7soft.admin.controller.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatisticsPageResponse<T> {
    private List<T> list;
    private long total;
    private int pageNo;
    private int pageSize;
    private int totalPages;
}