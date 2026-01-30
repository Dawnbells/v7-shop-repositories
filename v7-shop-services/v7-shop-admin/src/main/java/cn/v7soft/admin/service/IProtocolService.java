package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.EditProtocolTranslationRequest;
import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.dao.entities.primary.Protocol;

public interface IProtocolService extends IBaseDataRangeService<Protocol> {

    void editProtocolTranslation(EditProtocolTranslationRequest request);
}
