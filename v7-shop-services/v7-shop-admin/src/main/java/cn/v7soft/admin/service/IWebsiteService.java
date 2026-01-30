package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.TransferUserRequest;
import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.dao.entities.primary.Website;

public interface IWebsiteService extends IBaseDataRangeService<Website> {
    void transferUser(TransferUserRequest request);
}
