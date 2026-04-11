package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.service.INoticeService;
import cn.v7soft.dao.entities.primary.Notice;
import cn.v7soft.dao.repositories.primary.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NoticeService implements INoticeService {

    private final NoticeRepository noticeRepository;

    @Override
    public List<Notice> getUnreadNotices(Long userId) {
        return noticeRepository.findUnreadByUserId(userId);
    }

    @Override
    public long getUnreadCount(Long userId) {
        return noticeRepository.countUnreadByUserId(userId);
    }

    @Override
    @Transactional
    public void markAsRead(Long id) {
        noticeRepository.markAsRead(id);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        noticeRepository.markAllAsReadByUserId(userId);
    }

    @Override
    public Notice createNotice(String title, String content, String type, Long userId) {
        Notice notice = Notice.builder()
                .title(title)
                .content(content)
                .type(type)
                .userId(userId)
                .build();
        return noticeRepository.save(notice);
    }
}
