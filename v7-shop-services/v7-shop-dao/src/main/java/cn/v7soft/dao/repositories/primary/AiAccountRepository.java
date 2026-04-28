package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.enums.AiProvider;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AiAccountRepository extends BaseRepository<AiAccount> {

    @Query("FROM AiAccount WHERE name = :name AND (:id IS NULL OR id <> :id) AND status = 'VALID'")
    AiAccount findBySameName(@Param("name") String name, @Param("id") Long id);

    List<AiAccount> findByProviderAndEnabledTrueOrderByPriorityAscIdAsc(AiProvider provider);
}
