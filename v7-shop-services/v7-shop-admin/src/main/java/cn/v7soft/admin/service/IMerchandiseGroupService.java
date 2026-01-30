package cn.v7soft.admin.service;

import cn.v7soft.dao.entities.primary.MerchandiseGroup;

import java.util.Optional;

public interface IMerchandiseGroupService {
    Optional<MerchandiseGroup> findMerchandiseGroupsByMerchandise(String merchandise);
}
