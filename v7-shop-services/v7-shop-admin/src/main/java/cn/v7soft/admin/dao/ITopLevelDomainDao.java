package cn.v7soft.admin.dao;

import cn.v7soft.dao.entities.primary.TopLevelDomain;

public interface ITopLevelDomainDao {

    void saveAndFlush(TopLevelDomain domain);
}
