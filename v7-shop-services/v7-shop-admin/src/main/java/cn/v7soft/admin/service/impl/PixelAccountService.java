package cn.v7soft.admin.service.impl;

import cn.v7soft.common.controller.req.attributes.AccessDataRangeAttribute;
import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.PixelAccount;
import cn.v7soft.dao.enums.PixelAccountPlatform;
import cn.v7soft.dao.enums.PixelAccountState;
import cn.v7soft.dao.repositories.primary.PixelAccountRepository;
import cn.v7soft.admin.service.IPixelAccountService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PixelAccountService extends BaseDataRangeService<PixelAccount, PixelAccountRepository> implements IPixelAccountService {

    public PixelAccountService(PixelAccountRepository repository) {
        super(repository);
    }

    @Override
    public List<PixelAccount> getPixelAccountsByWebsiteId(Long websiteId) {
        return List.of();
    }

    @Override
    public List<PixelAccount> getPixelAccountsByState(PixelAccountState state) {
        return repository.findByState(state);
    }

    @Override
    protected void checkKeyConstraint(PixelAccount data) {
        // 可以在这里添加业务逻辑来检查某些约束条件，例如避免重复的像素ID
        ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(data.getPixelId(), "像素ID不能为空");
        if (data.getPlatform() == PixelAccountPlatform.GOOGLE) {
            ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(data.getAccessToken(), "请输入 Google Ads 转化标签");
        }
        if (data.getPlatform() == PixelAccountPlatform.GTM) {
            String containerId = PixelAccountPlatform.normalizeGtmContainerId(data.getPixelId());
            ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(containerId, "GTM 容器 ID 格式不正确，应为 GTM-XXXXXX");
            data.setPixelId(containerId);
        }
    }

    @Override
    public QueryAttribute getAccessDataRangeQueryAttribute() {
        return new AccessDataRangeAttribute(AccessDataRangeLevel.PERSON);
    }
}
