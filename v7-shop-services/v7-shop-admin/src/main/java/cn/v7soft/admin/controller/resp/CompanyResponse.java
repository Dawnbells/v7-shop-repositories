package cn.v7soft.admin.controller.resp;

import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.Company;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public class CompanyResponse extends IdResponse {
    public static CompanyResponse convertEntity(Company company) {
        return filling(company, CompanyResponse.builder().build());
    }
}
