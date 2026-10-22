package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.TemporaryOrderRiskRecordInfo;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

/**
 * 审单风险记录（t_temporary_risk_record_infos）的 IP 清空，配合订单脱敏使用
 */
public interface TemporaryOrderRiskRecordInfoPiiRepository extends BaseRepository<TemporaryOrderRiskRecordInfo> {

    /**
     * 清空远程 IP、真实 IP 及其归属地信息
     * @return 更新行数
     */
    @Modifying
    @Query("UPDATE TemporaryOrderRiskRecordInfo r SET r.remoteIp = NULL, r.remoteIpInfo = NULL, "
            + "r.realIp = NULL, r.realIpInfo = NULL WHERE r.id IN :ids")
    int clearIpByIds(@Param("ids") Collection<Long> ids);
}
