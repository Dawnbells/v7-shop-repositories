package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.service.IMerchandiseGroupService;
import cn.v7soft.core.service.impl.BaseService;
import cn.v7soft.dao.entities.primary.MerchandiseGroup;
import cn.v7soft.dao.repositories.primary.MerchandiseGroupRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MerchandiseGroupService extends BaseService<MerchandiseGroup, MerchandiseGroupRepository> implements IMerchandiseGroupService {

    public MerchandiseGroupService(MerchandiseGroupRepository repository) {
        super(repository);
    }

    @Override
    public Optional<MerchandiseGroup> findMerchandiseGroupsByMerchandise(String merchandise) {
        return repository.findByMerchandiseContains( merchandise); // 注意要加上双引号
    }
}
