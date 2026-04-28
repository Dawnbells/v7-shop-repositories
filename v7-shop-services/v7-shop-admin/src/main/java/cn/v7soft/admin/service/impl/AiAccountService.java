package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.service.IAiAccountService;
import cn.v7soft.common.controller.req.attributes.AccessDataRangeAttribute;
import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.enums.AiApiChannel;
import cn.v7soft.dao.enums.AiProvider;
import cn.v7soft.dao.repositories.primary.AiAccountRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AiAccountService extends BaseDataRangeService<AiAccount, AiAccountRepository> implements IAiAccountService {

    public AiAccountService(AiAccountRepository repository) {
        super(repository);
    }

    @Override
    public List<AiAccount> findAvailableAccounts(AiProvider provider) {
        return repository.findByProviderAndEnabledTrueOrderByPriorityAscIdAsc(provider);
    }

    @Override
    protected void checkKeyConstraint(AiAccount entity) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(entity.getName(), "AI账号名称不能为空");
        ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(entity.getApiKey(), "API Key不能为空");
        ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(entity.getModel(), "模型不能为空");
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(entity.getProvider(), "AI服务商不能为空");
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(entity.getApiChannel(), "API渠道不能为空");
        if (entity.getApiChannel() == AiApiChannel.SUB2API) {
            ClientResponseEnum.PARAMETER_ILLEGAL.isTrue(StrUtil.isNotBlank(entity.getBaseUrl()), "Sub2API渠道必须填写Base URL");
        }
        checkPrice(entity.getTextInputPrice(), entity.getTextInputPriceUnit(), "文本输入");
        checkPrice(entity.getTextOutputPrice(), entity.getTextOutputPriceUnit(), "文本输出");
        checkPrice(entity.getImageInputPrice(), entity.getImageInputPriceUnit(), "图片输入");
        checkPrice(entity.getImageOutputPrice(), entity.getImageOutputPriceUnit(), "图片输出");
        checkPrice(entity.getVideoInputPrice(), entity.getVideoInputPriceUnit(), "视频输入");
        checkPrice(entity.getVideoOutputPrice(), entity.getVideoOutputPriceUnit(), "视频输出");
        AiAccount existing = repository.findBySameName(entity.getName(), entity.getId());
        ClientResponseEnum.PARAMETER_ILLEGAL.isNull(existing, "AI账号名称不允许重复");
    }

    private void checkPrice(BigDecimal price, Object priceUnit, String name) {
        if (price == null) {
            return;
        }
        ClientResponseEnum.PARAMETER_ILLEGAL.isTrue(price.signum() >= 0, name + "价格不能小于0");
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(priceUnit, name + "价格已填写时必须选择计费单位");
    }

    @Override
    public QueryAttribute getAccessDataRangeQueryAttribute() {
        return new AccessDataRangeAttribute(AccessDataRangeLevel.COMPANY);
    }
}
