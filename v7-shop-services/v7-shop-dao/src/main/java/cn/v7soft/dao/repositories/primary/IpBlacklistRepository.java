package cn.v7soft.dao.repositories.primary;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.IpBlacklist;

public interface IpBlacklistRepository extends BaseRepository<IpBlacklist> {

    boolean existsByIpAddressAndFingerprint(String ipAddress, String fingerprint);
    List<IpBlacklist> findByIpAddressOrFingerprint(String ipAddress, String fingerprint);
}
