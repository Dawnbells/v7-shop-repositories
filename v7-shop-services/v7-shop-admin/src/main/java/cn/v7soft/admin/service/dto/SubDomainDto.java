package cn.v7soft.admin.service.dto;

import cn.v7soft.dao.entities.primary.CloudPlatformAccount;
import cn.v7soft.dao.entities.primary.SubDomain;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class SubDomainDto {
    private SubDomain subDomain;
    private TopLevelDomain topLevelDomain;
    private CloudPlatformAccount cloudPlatformAccount;
}
