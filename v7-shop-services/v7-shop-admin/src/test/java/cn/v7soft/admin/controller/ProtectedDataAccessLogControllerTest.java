package cn.v7soft.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import cn.dev33.satoken.stp.StpUtil;
import cn.v7soft.admin.controller.req.DownloadOrderRequest;
import cn.v7soft.admin.controller.req.QueryOrderRequest;
import cn.v7soft.admin.controller.resp.OrderResponse;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.admin.service.IOrderService;
import cn.v7soft.admin.service.IProtectedDataAccessLogService;
import cn.v7soft.admin.service.ITemporaryOrderService;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.meta.OrderDeliveryInfo;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.entities.primary.OrderContextInfo;
import cn.v7soft.dao.entities.primary.OrderRiskRecordInfo;
import cn.v7soft.dao.entities.primary.TemporaryOrder;
import cn.v7soft.dao.enums.ViewMode;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import cn.v7soft.dao.repositories.primary.DepartmentRepository;
import cn.v7soft.dao.utils.SaSessionUtil;

/**
 * 订单列表/导出接口写受保护客户数据访问日志（服务 mock 验证调用）。
 */
@ExtendWith(MockitoExtension.class)
class ProtectedDataAccessLogControllerTest {

    @Mock
    private IOrderService orderService;
    @Mock
    private ITemporaryOrderService temporaryOrderService;
    @Mock
    private ICompanyService companyService;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private IProtectedDataAccessLogService accessLogService;

    private MockedStatic<StpUtil> stp;
    private MockedStatic<SaSessionUtil> session;
    private SystemUserDto loginUser;

    private OrderController orderController;
    private TemporaryOrderController temporaryOrderController;

    @BeforeEach
    void setUp() {
        stp = Mockito.mockStatic(StpUtil.class);
        session = Mockito.mockStatic(SaSessionUtil.class);
        loginUser = new SystemUserDto();
        loginUser.setId("7");
        orderController = new OrderController(orderService, departmentRepository, accessLogService);
        temporaryOrderController = new TemporaryOrderController(temporaryOrderService, companyService, accessLogService);
    }

    @AfterEach
    void tearDown() {
        stp.close();
        session.close();
    }

    private static Order order(long id, WebsiteTypeEnum platform) {
        Order order = Order.builder()
                .platform(platform)
                .fromUrl("https://example.com/p/1")
                .itemInfos(new ArrayList<>())
                .deliveryInfo(new OrderDeliveryInfo())
                .riskInfo(new OrderRiskRecordInfo())
                .contextInfo(new OrderContextInfo())
                .build();
        order.setId(id);
        return order;
    }

    private static TemporaryOrder temporaryOrder(long id, WebsiteTypeEnum platform) {
        TemporaryOrder order = TemporaryOrder.builder().platform(platform).build();
        order.setId(id);
        return order;
    }

    private static QueryOrderRequest pageRequest() {
        QueryOrderRequest request = new QueryOrderRequest();
        request.setPageNo(1);
        request.setPageSize(20);
        return request;
    }

    private static DownloadOrderRequest downloadRequest(WebsiteTypeEnum platform) {
        DownloadOrderRequest request = new DownloadOrderRequest();
        request.setTemplateId("tpl-1");
        request.setPlatform(platform);
        return request;
    }

    @Test
    @SuppressWarnings("unchecked")
    void orderPageRecordsOnlyShopifyIds() {
        when(orderService.findPaginated(any(QueryPageRequest.class))).thenReturn(new PageImpl<>(List.of(
                order(1L, WebsiteTypeEnum.SHOPIFY), order(2L, WebsiteTypeEnum.V7_SHOP), order(3L, WebsiteTypeEnum.SHOPIFY))));

        Page<OrderResponse> page = orderController.page(pageRequest());

        assertEquals(3, page.getContent().size());
        verify(accessLogService).record("LIST", "ORDER", List.of(1L, 3L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void orderPageWithoutShopifyPassesEmptyIds() {
        when(orderService.findPaginated(any(QueryPageRequest.class))).thenReturn(new PageImpl<>(List.of(
                order(2L, WebsiteTypeEnum.V7_SHOP))));

        orderController.page(pageRequest());

        verify(accessLogService).record("LIST", "ORDER", List.of());
    }

    @Test
    @SuppressWarnings("unchecked")
    void orderPageResponseUnaffectedByLogFailure() {
        when(orderService.findPaginated(any(QueryPageRequest.class))).thenReturn(new PageImpl<>(List.of(
                order(1L, WebsiteTypeEnum.SHOPIFY))));
        doThrow(new RuntimeException("log down")).when(accessLogService).record(anyString(), anyString(), anyList());

        Page<OrderResponse> page = orderController.page(pageRequest());

        assertEquals(1, page.getContent().size());
        assertEquals("1", page.getContent().get(0).getId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void temporaryOrderPageRecordsOnlyShopifyIds() {
        when(temporaryOrderService.findPaginated(any(QueryPageRequest.class))).thenReturn(new PageImpl<>(List.of(
                temporaryOrder(5L, WebsiteTypeEnum.SHOPLINE), temporaryOrder(6L, WebsiteTypeEnum.SHOPIFY))));

        Page<OrderResponse> page = temporaryOrderController.page(pageRequest());

        assertEquals(2, page.getContent().size());
        verify(accessLogService).record("LIST", "TEMPORARY", List.of(6L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void temporaryOrderPageResponseUnaffectedByLogFailure() {
        when(temporaryOrderService.findPaginated(any(QueryPageRequest.class))).thenReturn(new PageImpl<>(List.of(
                temporaryOrder(6L, WebsiteTypeEnum.SHOPIFY))));
        doThrow(new RuntimeException("log down")).when(accessLogService).record(anyString(), anyString(), anyList());

        Page<OrderResponse> page = temporaryOrderController.page(pageRequest());

        assertEquals("6", page.getContent().get(0).getId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadRecordsExportWithShopifyIdsAndTotal() {
        session.when(SaSessionUtil::getLoginUser).thenReturn(loginUser);
        session.when(SaSessionUtil::getViewMode).thenReturn(ViewMode.TEAM);
        DownloadOrderRequest request = downloadRequest(null);
        when(orderService.download(request)).thenReturn(99L);
        when(orderService.findPaginated(any(QueryPageRequest.class), any(SystemUserDto.class), any(ViewMode.class)))
                .thenReturn(new PageImpl<>(List.of(order(1L, WebsiteTypeEnum.SHOPIFY), order(3L, WebsiteTypeEnum.SHOPIFY)),
                        PageRequest.of(0, 500), 1200));

        Long taskId = orderController.download(request);

        assertEquals(99L, taskId);
        verify(accessLogService).record("EXPORT", "ORDER", List.of(1L, 3L), 1200L);
        // 已提交的导出参数不被改写
        assertNull(request.getPlatform());
    }

    @Test
    void downloadOfOtherPlatformDoesNotRecord() {
        DownloadOrderRequest request = downloadRequest(WebsiteTypeEnum.V7_SHOP);
        when(orderService.download(request)).thenReturn(99L);

        assertEquals(99L, orderController.download(request));
        verify(accessLogService, never()).record(anyString(), anyString(), anyList(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadResponseUnaffectedByLogFailure() {
        session.when(SaSessionUtil::getLoginUser).thenReturn(loginUser);
        session.when(SaSessionUtil::getViewMode).thenReturn(ViewMode.TEAM);
        DownloadOrderRequest request = downloadRequest(WebsiteTypeEnum.SHOPIFY);
        when(orderService.download(request)).thenReturn(99L);
        when(orderService.findPaginated(any(QueryPageRequest.class), any(SystemUserDto.class), any(ViewMode.class)))
                .thenThrow(new RuntimeException("query failed"));

        assertEquals(99L, orderController.download(request));
        verify(accessLogService, never()).record(anyString(), anyString(), anyList(), any());
    }
}
