package cn.v7soft.admin.controller.resp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.v7soft.dao.entities.primary.AiTokenUsageRecord;
import org.junit.jupiter.api.Test;

class AiTokenUsageRecordResponsePolicyFallbackTest {

    @Test
    void limitedResponseShowsFriendlyPolicyMessageButHidesRawReason() {
        AiTokenUsageRecord record = AiTokenUsageRecord.builder()
                .policyFallbackReason("PUBLIC_ERROR_SEXUAL_UPLOAD")
                .build();

        AiTokenUsageRecordResponse limited = AiTokenUsageRecordResponse.convertEntityLimited(record, null);
        AiTokenUsageRecordResponse admin = AiTokenUsageRecordResponse.convertEntity(record, null);

        assertTrue(limited.getPolicyFallback());
        assertEquals("图片因内容政策限制保留原图", limited.getPolicyFallbackMessage());
        assertNull(limited.getPolicyFallbackReason());
        assertEquals("PUBLIC_ERROR_SEXUAL_UPLOAD", admin.getPolicyFallbackReason());
    }
}
