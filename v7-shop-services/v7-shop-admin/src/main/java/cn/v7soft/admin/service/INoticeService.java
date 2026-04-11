package cn.v7soft.admin.service;

import cn.v7soft.dao.entities.primary.Notice;

import java.util.List;

public interface INoticeService {

    List<Notice> getUnreadNotices(Long userId);

    long getUnreadCount(Long userId);

    void markAsRead(Long id);

    void markAllAsRead(Long userId);

    Notice createNotice(String title, String content, String type, Long userId);
}
