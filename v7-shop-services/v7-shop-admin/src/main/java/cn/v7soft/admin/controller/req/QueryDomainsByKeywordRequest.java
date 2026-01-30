package cn.v7soft.admin.controller.req;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueryDomainsByKeywordRequest {
    /**
     * 关键字
     */
    private String keyword;
}
