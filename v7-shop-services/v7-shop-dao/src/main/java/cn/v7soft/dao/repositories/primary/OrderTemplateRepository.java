package cn.v7soft.dao.repositories.primary;

import java.util.List;

import org.springframework.data.jpa.repository.Query;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.OrderTemplate;

public interface OrderTemplateRepository extends BaseRepository<OrderTemplate> {
    boolean existsByTemplateName(String templateName);

    @Query("from OrderTemplate where downloadTemplate =:downloadTemplate and (:keyword is null or templateName like :keyword)")
    List<OrderTemplate> query(boolean downloadTemplate, String keyword);
}
