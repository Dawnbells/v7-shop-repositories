package cn.v7soft.admin.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.EditOrderTemplateColumnRequest;
import cn.v7soft.admin.controller.req.EditOrderTemplateRequest;
import cn.v7soft.admin.controller.req.QueryOrderTemplateRequest;
import cn.v7soft.admin.controller.resp.OrderTemplateResponse;
import cn.v7soft.admin.service.IOrderTemplateService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.controller.request.attributes.LikeAttribute;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.OrderTemplate;
import cn.v7soft.dao.entities.primary.OrderTemplateColumn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/order-template")
@Tag(name = "订单管理-下载模版管理")
@Validated
public class OrderTemplateController extends BaseDataRangeController<
        OrderTemplate,
        IOrderTemplateService,
        OrderTemplateResponse,
        QueryOrderTemplateRequest,
        EditOrderTemplateRequest> {

    protected OrderTemplateController(IOrderTemplateService service) {
        super(service);
    }

    @Override
    protected QueryPageRequest<OrderTemplate> convertQueryPageRequest(QueryOrderTemplateRequest request) {
        return super.convertQueryPageRequest(request)
                .addConstraint(StrUtil.isNotBlank(request.getTemplateName()),
                               LikeAttribute.builder().name("templateName").value("%" + request.getTemplateName() + "%").build());
    }

    @Override
    protected OrderTemplateResponse convertEntity(OrderTemplate entity) {
        return OrderTemplateResponse.convertEntity(entity);
    }

    @Override
    protected OrderTemplate convertRequest(@Nullable OrderTemplate dbEntity, EditOrderTemplateRequest request) {
        OrderTemplate entity = Optional.ofNullable(dbEntity).orElse(OrderTemplate.builder().build());
        entity.setTemplateName(request.getTemplateName());
        entity.setDownloadTemplate(request.isDownloadTemplate());
        ClientResponseEnum.PARAMETER_ILLEGAL.notEmpty(request.getColumns(), "表头配置不能为空");
        List<OrderTemplateColumn> columnList = new ArrayList<>();
        for (int index = 0; index < request.getColumns().size(); index++) {
            EditOrderTemplateColumnRequest item = request.getColumns().get(index);
            OrderTemplateColumn column = OrderTemplateColumn.builder()
                    .orderTemplate(entity)
                    .headerName(item.getHeaderName())
                    .fieldKey(item.getFieldKey())
                    .sortOrder(index)
                    .build();
            columnList.add(column);
        }
        if (entity.getColumns() == null) {
            entity.setColumns(new ArrayList<>());
        } else {
            entity.getColumns().clear();
        }
        entity.getColumns().addAll(columnList);
        return entity;
    }

    @Override
    protected String getPermissionPrefix() {
        return "order-template";
    }

    @Override
    protected boolean cleanupBeforeDelete(DeleteRequest request) {
        return true;
    }

    @GetMapping("/query")
    @Operation(summary = "查询")
    @SaCheckPermission("order-template.query")
    public List<OrderTemplateResponse> query(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword
    ) {
       return service.findPaginated(
               QueryPageRequest.<OrderTemplate>fromUnLimit()
                       .add(EqualsQueryAttribute.builder().name("downloadTemplate").value("download".equalsIgnoreCase(type)).build())
                       .addConstraint(StrUtil.isNotBlank(keyword), LikeAttribute.builder().name("templateName").value(keyword).build())
               )
                .map(OrderTemplateResponse::convertEntity)
                .toList();
    }
}
