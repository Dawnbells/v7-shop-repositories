package cn.v7soft.admin.service.impl;

import cn.hutool.core.lang.Pair;
import cn.v7soft.admin.service.*;
import cn.v7soft.admin.service.dto.ShoplineOrderLoadResult;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.entities.primary.Company;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShopifyManualSyncTaskTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void completesAllPagesAndReportsAnyFailures(int failedCount) {
        IAsyncTaskService tasks = mock(IAsyncTaskService.class);
        IThirdPartyWebsiteService websites = mock(IThirdPartyWebsiteService.class);
        ICompanyService companies = mock(ICompanyService.class);
        AsyncTask task = AsyncTask.builder().id(55L).companyId(9L)
                .taskType(TaskType.THIRD_PARTY_ORDER_SYNC).state(TaskState.PENDING)
                .parameters("{\"id\":\"1\"}").build();
        SystemUserDto owner = SystemUserDto.builder().id("101").companyId(9L).build();
        when(tasks.getAndInitializeOwner(55L)).thenReturn(new Pair<>(task, owner));
        when(companies.companyCached(9L)).thenReturn(Company.builder().id(9L).build());
        when(websites.loadOrders(any(), eq(""), eq(SyncMode.MANUAL))).thenReturn(
                ShoplineOrderLoadResult.builder().nextPageInfo("next").fetchedCount(3)
                        .successCount(3 - failedCount).failedCount(failedCount).build());
        when(websites.loadOrders(any(), eq("next"), eq(SyncMode.MANUAL))).thenReturn(
                ShoplineOrderLoadResult.builder().fetchedCount(2).successCount(2).build());
        TaskExecutorService executor = new TaskExecutorService(tasks, mock(IAddressService.class),
                mock(IOrderService.class), mock(IS3Service.class), websites, mock(IOrderTemplateService.class),
                mock(AsyncTaskRepository.class), mock(ITaskExecutorService.class), companies,
                mock(ISpuService.class), mock(OrderStatisticsExportExecutionService.class));

        executor.submitAsyncTask(55L);

        verify(websites).loadOrders(any(), eq("next"), eq(SyncMode.MANUAL));
        verify(tasks).updateAsyncTask(task, failedCount > 0 ? TaskState.FAILED : TaskState.COMPLETED, 100);
        verify(tasks, never()).updateAsyncTask(task, failedCount > 0 ? TaskState.COMPLETED : TaskState.FAILED, 100);
        assertTrue(task.getMessage().contains("成功: " + (5 - failedCount)));
        assertTrue(task.getMessage().contains("失败: " + failedCount));
        assertTrue(task.getMessage().contains("共拉取 5 条"));
    }
}
