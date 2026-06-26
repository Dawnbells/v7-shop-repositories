package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.service.IS3Service;
import cn.v7soft.admin.service.ITaskExecutorService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.repositories.primary.AiAccountRepository;
import cn.v7soft.dao.repositories.primary.AiTokenUsageRecordRepository;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.CountryRepository;
import cn.v7soft.dao.repositories.primary.LanguageRepository;
import cn.v7soft.dao.repositories.primary.ProductRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationEventPublisher;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncTaskStatisticsDownloadGuardIntegrationTest {

    private AsyncTaskService service(
            AsyncTaskRepository repository,
            IS3Service s3Service,
            OrderStatisticsExportDownloadGuard guard
    ) {
        return new AsyncTaskService(
                repository,
                s3Service,
                mock(ITaskExecutorService.class),
                mock(AiCreditsService.class),
                mock(AiTokenUsageRecordRepository.class),
                mock(ProductRepository.class),
                mock(CountryRepository.class),
                mock(LanguageRepository.class),
                mock(AiAccountRepository.class),
                mock(ApplicationEventPublisher.class),
                guard
        );
    }

    @Test
    void passesCurrentUserAndOwnerToGuardThenDownloadsFromS3() {
        AsyncTaskRepository repository = mock(AsyncTaskRepository.class);
        IS3Service s3Service = mock(IS3Service.class);
        OrderStatisticsExportDownloadGuard guard =
                mock(OrderStatisticsExportDownloadGuard.class);
        AsyncTask task = AsyncTask.builder()
                .id(55L)
                .taskType(TaskType.ORDER_STATISTICS_EXPORT)
                .exportRelativePath("async-task/order-statistics/file.xlsx")
                .build();
        when(repository.findById(55L)).thenReturn(Optional.of(task));
        when(repository.findOwnerIdById(55L)).thenReturn(100L);
        when(s3Service.download(task.getExportRelativePath()))
                .thenReturn(new ByteArrayInputStream(new byte[0]));
        AsyncTaskService service = service(repository, s3Service, guard);

        try (MockedStatic<SaSessionUtil> saSession = mockStatic(SaSessionUtil.class)) {
            saSession.when(SaSessionUtil::getLoginUser)
                    .thenReturn(SystemUserDto.builder().id("100").build());

            service.download(55L);
        }

        verify(guard).validate(task, 100L, 100L);
        verify(s3Service).download(task.getExportRelativePath());
    }

    @Test
    void rejectsNonOwnerAndNeverHitsS3WithRealGuard() {
        AsyncTaskRepository repository = mock(AsyncTaskRepository.class);
        IS3Service s3Service = mock(IS3Service.class);
        OrderStatisticsExportDownloadGuard realGuard = new OrderStatisticsExportDownloadGuard();
        AsyncTask task = AsyncTask.builder()
                .id(55L)
                .taskType(TaskType.ORDER_STATISTICS_EXPORT)
                .exportRelativePath("async-task/order-statistics/file.xlsx")
                .createTime(LocalDateTime.now())
                .build();
        when(repository.findById(55L)).thenReturn(Optional.of(task));
        when(repository.findOwnerIdById(55L)).thenReturn(100L); // 文件归属用户 100
        AsyncTaskService service = service(repository, s3Service, realGuard);

        try (MockedStatic<SaSessionUtil> saSession = mockStatic(SaSessionUtil.class)) {
            // 当前登录用户 200 ≠ owner 100 → 拒绝，且绝不触发 S3 下载
            saSession.when(SaSessionUtil::getLoginUser)
                    .thenReturn(SystemUserDto.builder().id("200").build());

            assertThatThrownBy(() -> service.download(55L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("无权");
        }

        verify(s3Service, never()).download(any());
    }
}
