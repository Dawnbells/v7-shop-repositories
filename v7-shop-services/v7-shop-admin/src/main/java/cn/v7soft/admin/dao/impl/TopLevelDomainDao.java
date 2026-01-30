package cn.v7soft.admin.dao.impl;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import cn.v7soft.admin.dao.ITopLevelDomainDao;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import cn.v7soft.dao.repositories.primary.TopLevelDomainRepository;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TopLevelDomainDao implements ITopLevelDomainDao {
    private final TopLevelDomainRepository repository;
    @Override
    @Transactional
    public void saveAndFlush(TopLevelDomain domain) {
        repository.saveAndFlush(domain);
    }
}
