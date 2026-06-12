package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.FrontAgentReport;

import java.util.Optional;

public interface FrontAgentReportRepository extends BaseRepository<FrontAgentReport> {

    /**
     * 按前端机标识查询回报记录（agent_name 全局唯一，upsert 用）
     */
    Optional<FrontAgentReport> findByAgentName(String agentName);
}
