package com.skatehub.security;

import com.skatehub.dao.AdminAuditLogRepository;
import com.skatehub.pojo.AdminAuditLog;
import com.skatehub.service.AdminAuditLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminAuditLogServiceTest {

    @Test
    void recordsAdminHighRiskOperationResult() {
        AdminAuditLogRepository repository = mock(AdminAuditLogRepository.class);
        AdminAuditLogService service = new AdminAuditLogService(repository);

        service.record(1L, "POST", 9L, "DELETE", "SUCCESS");

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(repository).save(captor.capture());
        AdminAuditLog log = captor.getValue();
        assertThat(log.getAdminId()).isEqualTo(1L);
        assertThat(log.getTargetType()).isEqualTo("POST");
        assertThat(log.getTargetId()).isEqualTo(9L);
        assertThat(log.getAction()).isEqualTo("DELETE");
        assertThat(log.getResult()).isEqualTo("SUCCESS");
    }
}
