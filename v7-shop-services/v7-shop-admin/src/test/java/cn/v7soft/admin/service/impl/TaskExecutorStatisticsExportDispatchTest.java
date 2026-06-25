package cn.v7soft.admin.service.impl;

import cn.hutool.core.lang.Pair;
import cn.v7soft.admin.service.IAddressService;
import cn.v7soft.admin.service.IAsyncTaskService;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.admin.service.IOrderService;
import cn.v7soft.admin.service.IOrderTemplateService;
import cn.v7soft.admin.service.IS3Service;
import cn.v7soft.admin.service.ISpuService;
import cn.v7soft.admin.service.ITaskExecutorService;
import cn.v7soft.admin.service.IThirdPartyWebsiteService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.entities.primary.Company;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskExecutorStatisticsExportDispatchTest {

    @Test
    void dispatchesStatisticsExportToDedicatedExecutor() {
        IAsyncTaskService asyncTaskService = mock(IAsyncTaskService.class);
        IAddressService addressService = mock(IAddressService.class);
        IOrderService orderService = mock(IOrderService.class);
        IS3Service s3Service = mock(IS3Service.class);
        IThirdPartyWebsiteService thirdPartyWebsiteService =
                mock(IThirdPartyWebsiteService.class);
        IOrderTemplateService orderTemplateService =
                mock(IOrderTemplateService.class);
        AsyncTaskRepository taskRepository = mock(AsyncTaskRepository.class);
        ITaskExecutorService self = mock(ITaskExecutorService.class);
        ICompanyService companyService = mock(ICompanyService.class);
        ISpuService spuService = mock(ISpuService.class);
        OrderStatisticsExportExecutionService statisticsExportService =
                mock(OrderStatisticsExportExecutionService.class);
        AsyncTask task = AsyncTask.builder()
                .id(55L)
                .companyId(9L)
                .taskType(TaskType.ORDER_STATISTICS_EXPORT)
                .state(TaskState.PENDING)
                .build();
        SystemUserDto owner = SystemUserDto.builder()
                .id("101")
                .companyId(9L)
                .build();
        when(asyncTaskService.getAndInitializeOwner(55L))
                .thenReturn(new Pair<>(task, owner));
        when(companyService.companyCached(9L))
                .thenReturn(Company.builder().id(9L).build());
        TaskExecutorService executor = new TaskExecutorService(
                asyncTaskService,
                addressService,
                orderService,
                s3Service,
                thirdPartyWebsiteService,
                orderTemplateService,
                taskRepository,
                self,
                companyService,
                spuService,
                statisticsExportService
        );

        executor.submitAsyncTask(55L);

        verify(statisticsExportService).execute(task, owner);
    }
}
