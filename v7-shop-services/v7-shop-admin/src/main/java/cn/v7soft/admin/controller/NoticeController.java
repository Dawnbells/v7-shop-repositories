package cn.v7soft.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.v7soft.admin.controller.resp.NoticeResponse;
import cn.v7soft.admin.service.INoticeService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.Notice;
import cn.v7soft.dao.utils.SaSessionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/notice")
@Tag(name = "通知管理")
@RequiredArgsConstructor
public class NoticeController {

    private final INoticeService noticeService;

    @SaCheckLogin
    @GetMapping("/getList")
    @Operation(summary = "获取未读通知列表")
    public Map<String, Object> getList() {
        SystemUserDto user = SaSessionUtil.getLoginUser();
        Long userId = Long.parseLong(user.getId());
        List<Notice> notices = noticeService.getUnreadNotices(userId);
        List<NoticeResponse> list = notices.stream()
                .map(NoticeResponse::convertEntity)
                .collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", list.size());
        return result;
    }

    @SaCheckLogin
    @PostMapping("/markAsRead/{id}")
    @Operation(summary = "标记通知为已读")
    public void markAsRead(@PathVariable Long id) {
        noticeService.markAsRead(id);
    }

    @SaCheckLogin
    @PostMapping("/markAllAsRead")
    @Operation(summary = "标记所有通知为已读")
    public void markAllAsRead() {
        SystemUserDto user = SaSessionUtil.getLoginUser();
        Long userId = Long.parseLong(user.getId());
        noticeService.markAllAsRead(userId);
    }
}
