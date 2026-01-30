package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.req.TransferUserRequest;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.Website;
import cn.v7soft.dao.repositories.primary.WebsiteRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import cn.v7soft.admin.service.IWebsiteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebsiteService extends BaseDataRangeService<Website, WebsiteRepository> implements IWebsiteService {
    public WebsiteService(WebsiteRepository repository) {
        super(repository);
    }

    @Override
    protected void checkKeyConstraint(Website data) {
        SystemUserDto user = SaSessionUtil.getLoginUser();
        Website existingWebsite = repository.findBySameName(data.getName(), data.getId(), user.getLongId());
        ClientResponseEnum.PARAMETER_ILLEGAL.isNull(existingWebsite, "网站名称不允许重复");
    }

    @Override
    @Transactional
    public void transferUser(TransferUserRequest request) {
        Website website = repository.findById(request.getIdLongValue())
                .orElseThrow(() -> new RuntimeException("网站不存在"));

        SystemUserDto user = SaSessionUtil.getLoginUser();
        if (!user.hasManagerPermission(website.getOwner().getId(),
                website.getOwner().getDepartment().getId())) {
            ClientResponseEnum.PARAMETER_ILLEGAL.throwException("没有权限转移网站");
        }

        website.setOwner(SystemUser.builder().id(Long.valueOf(request.getTransferUserId())).build());
        repository.save(website);
    }
}
