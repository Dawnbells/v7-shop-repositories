package cn.v7soft.dao.repositories.primary;

import cn.v7soft.dao.entities.primary.ImagePolicyCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ImagePolicyCacheRepository extends JpaRepository<ImagePolicyCache, Long> {

    Optional<ImagePolicyCache> findByImageHash(String imageHash);
}
