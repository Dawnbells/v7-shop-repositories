package cn.v7soft.common.service.impl;

import cn.v7soft.common.controller.resp.CurrencyResponse;
import cn.v7soft.common.controller.resp.WebsiteResponse;
import cn.v7soft.common.service.IWebsiteContextService;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.repositories.primary.WebsiteRepository;
import cn.v7soft.dao.tenant.WebsiteContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebsiteContextService implements IWebsiteContextService {
    private final WebsiteRepository websiteRepository;

    @Override
    public String getCurrentWebsiteName() {
        return websiteRepository.getNameById(WebsiteContext.getCurrentWebsiteId());
    }

    @Override
    public CurrencyResponse getCurrentWebsiteCurrency() {
        return CurrencyResponse.convertEntity(websiteRepository.getCurrencyById(WebsiteContext.getCurrentWebsiteId()));
    }

    @Override
    public WebsiteResponse getCurrentWebsite() {
        Long websiteId = WebsiteContext.getCurrentWebsiteId();
        String domain = WebsiteContext.getDomain();
        return WebsiteResponse.convertEntity(
                websiteRepository.findById(websiteId)
                        .orElseThrow(() -> {
                            log.warn("商城不存在: websiteId={}, domain={}, isWebsiteAdmin={}", websiteId, domain, WebsiteContext.isWebsiteAdmin());
                            return ClientResponseEnum.NOT_FOUND.newException("当前商城不存在或未配置");
                        })
        );
    }
}
