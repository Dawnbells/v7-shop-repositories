package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.service.IAiTokenUsageRecordService;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.dao.entities.primary.AiTokenUsageRecord;
import cn.v7soft.dao.repositories.primary.AiTokenUsageRecordRepository;
import org.springframework.stereotype.Service;

@Service
public class AiTokenUsageRecordService extends BaseDataRangeService<AiTokenUsageRecord, AiTokenUsageRecordRepository> implements IAiTokenUsageRecordService {

    public AiTokenUsageRecordService(AiTokenUsageRecordRepository repository) {
        super(repository);
    }
}
