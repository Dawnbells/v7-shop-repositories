package cn.v7soft.admin.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.v7soft.admin.service.IOrderTemplateService;
import cn.v7soft.admin.service.dto.OrderDownloadDto;
import cn.v7soft.common.controller.req.attributes.AccessDataRangeAttribute;
import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.dao.entities.primary.OrderTemplate;
import cn.v7soft.dao.entities.primary.OrderTemplateColumn;
import cn.v7soft.dao.repositories.primary.OrderTemplateRepository;

@Service
public class OrderTemplateService extends BaseDataRangeService<OrderTemplate, OrderTemplateRepository>
        implements IOrderTemplateService {

    public OrderTemplateService(OrderTemplateRepository repository) {
        super(repository);
    }

    @Override
    protected void checkKeyConstraint(OrderTemplate entity) {
        boolean exists = repository.existsByTemplateName(entity.getTemplateName());
        if (exists && (entity.getId() == null)) {
            throw new IllegalArgumentException("模版名称已存在");
        }
    }

    @Override
    public List<OrderTemplate> query(String type, String keyword) {
        return repository.query("download".equalsIgnoreCase(type), "%" + keyword + "%");
    }

    @Override
    @Transactional
    public Map<String, String> getHeaderAliasMap(String templateId, Boolean isAudit) {
        Optional<OrderTemplate> orderTemplateOptional = findById(Long.valueOf(templateId));
        return orderTemplateOptional.map(orderTemplate -> orderTemplate.getColumns().stream().filter(column -> OrderDownloadDto.filterAudit(column, isAudit))
                .collect(Collectors.toMap(
                        OrderTemplateColumn::getFieldKey,
                        OrderTemplateColumn::getHeaderName,
                        (v1, v2) -> v1,
                        LinkedHashMap::new
                ))).orElse(Boolean.TRUE.equals(isAudit) ? OrderDownloadDto.auditHeaderAlias() : OrderDownloadDto.headerAlias());
    }

    @Override
    public QueryAttribute getAccessDataRangeQueryAttribute() {
        return new AccessDataRangeAttribute(AccessDataRangeLevel.DEEP_DEPARTMENT);
    }
}
