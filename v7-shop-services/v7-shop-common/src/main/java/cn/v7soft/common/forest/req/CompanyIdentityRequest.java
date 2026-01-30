package cn.v7soft.common.forest.req;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CompanyIdentityRequest {
    private String domain;
}
