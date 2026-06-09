package cn.v7soft.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.v7soft.admin.controller.req.SaveOrderSearchPresetRequest;
import cn.v7soft.admin.controller.resp.OrderSearchPresetResponse;
import cn.v7soft.admin.service.IOrderSearchPresetService;
import cn.v7soft.dao.enums.OrderSearchPresetPageType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/order-search-presets")
@Tag(name = "订单管理-个人搜索条件预设")
public class OrderSearchPresetController {

    private final IOrderSearchPresetService service;

    public OrderSearchPresetController(IOrderSearchPresetService service) {
        this.service = service;
    }

    @SaCheckLogin
    @GetMapping
    @Operation(summary = "查询当前用户当前页面的搜索条件预设")
    public List<OrderSearchPresetResponse> list(@RequestParam OrderSearchPresetPageType pageType) {
        return service.listCurrentUserPresets(pageType)
                .stream()
                .map(OrderSearchPresetResponse::convertEntity)
                .toList();
    }

    @SaCheckLogin
    @PostMapping
    @Operation(summary = "保存或覆盖当前用户搜索条件预设")
    public OrderSearchPresetResponse save(@Valid @RequestBody SaveOrderSearchPresetRequest request) {
        return OrderSearchPresetResponse.convertEntity(service.savePreset(request));
    }

    @SaCheckLogin
    @DeleteMapping("/{id}")
    @Operation(summary = "删除当前用户搜索条件预设")
    public void delete(@PathVariable Long id) {
        service.deletePreset(id);
    }

    @SaCheckLogin
    @PostMapping("/{id}/use")
    @Operation(summary = "使用当前用户搜索条件预设并刷新最近使用时间")
    public OrderSearchPresetResponse use(@PathVariable Long id) {
        return OrderSearchPresetResponse.convertEntity(service.usePreset(id));
    }
}
