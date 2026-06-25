package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.service.IS3Service;
import cn.v7soft.admin.service.ITaskExecutorService;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.repositories.primary.AiAccountRepository;
import cn.v7soft.dao.repositories.primary.AiTokenUsageRecordRepository;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.CountryRepository;
import cn.v7soft.dao.repositories.primary.LanguageRepository;
import cn.v7soft.dao.repositories.primary.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncTaskStatisticsDownloadGuardIntegrationTest {

    @Test
    void validatesStatisticsExportBeforeDownloadingFromS3() {
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
        when(s3Service.download(task.getExportRelativePath()))
                .thenReturn(new ByteArrayInputStream(new byte[0]));
        AsyncTaskService service = new AsyncTaskService(
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

        service.download(55L);

        verify(guard).validate(task);
        verify(s3Service).download(task.getExportRelativePath());
    }
}
