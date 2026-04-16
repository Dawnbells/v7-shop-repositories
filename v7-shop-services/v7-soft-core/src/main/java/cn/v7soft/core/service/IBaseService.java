package cn.v7soft.core.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import cn.v7soft.core.controller.request.BasePageRequest;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.entities.IBaseEntity;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.core.exception.ClientException;
import jakarta.validation.constraints.NotNull;


public interface IBaseService<T extends IBaseEntity> {
    /**
     * 创建实体对象
     *
     * @param entity 实体对象
     * @return 新增后的实体对象，包含ID
     */
    @Transactional
    T save(T entity);

    /**
     * 创建实体对象并刷新到数据库
     *
     * @param entity 实体对象
     * @return 新增后的实体对象，包含ID
     */
    T saveAndFlush(T entity);

    /**
     * 根据ID获取数据
     *
     * @param id id
     * @return 获取的实体对象
     * @throws ClientException 如果不存在，会抛出异常
     */
    @NotNull
    T getById(Long id) throws ClientException;

    Optional<T> findById(Long id) throws ClientException;

    /**
     * 分页查询
     *
     * @param request 分页信息
     * @return Page
     */

    Page<T> findPaginated(BasePageRequest request);

    Page<T> findPaginated(QueryPageRequest<T> request);

    void delete(Long id);

    void deleteAll(List<Long> ids);

    void switchStatus(Long id, StatusEnum status);
}
