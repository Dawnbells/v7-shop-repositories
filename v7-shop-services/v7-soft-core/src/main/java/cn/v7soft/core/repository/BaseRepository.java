package cn.v7soft.core.repository;

import cn.v7soft.core.entities.BaseEntity;
import cn.v7soft.core.entities.IBaseEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface BaseRepository<T extends IBaseEntity> extends JpaRepository<T, Long>, JpaSpecificationExecutor<T> {

}
