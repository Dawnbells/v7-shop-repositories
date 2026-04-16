package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;

import java.util.List;
import java.util.Optional;

public interface ThirdPartyWebsiteRepository extends BaseRepository<ThirdPartyWebsite> {

    Optional<ThirdPartyWebsite> findByToken(String token);

    List<ThirdPartyWebsite> findByStatusAndAuthStatus(StatusEnum status, ThirdPartyAuthStatusEnum authStatus);
}
