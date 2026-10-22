package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.OrderRiskRecordInfo;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

/**
 * 正式订单风险记录（t_order_risk_record_infos）的 IP 清空，配合订单脱敏使用。
 * 原生 SQL：软删除的记录同样清空；ids 来自已按租户限定的订单查询。
 */
public interface OrderRiskRecordInfoPiiRepository extends BaseRepository<OrderRiskRecordInfo> {

    /**
     * 清空远程 IP、真实 IP 及其归属地信息
     * @return 更新行数
     */
    @Modifying
    @Query(value = "UPDATE t_order_risk_record_infos r SET r.remote_ip = NULL, r.remote_ip_info = NULL, "
            + "r.real_ip = NULL, r.real_ip_info = NULL WHERE r.id IN (:ids)", nativeQuery = true)
    int clearIpByIds(@Param("ids") Collection<Long> ids);
}
