package com.github.deployedreject.Aggregator_backend.repository;

import com.github.deployedreject.Aggregator_backend.entity.Plugin;
import com.github.deployedreject.Aggregator_backend.entity.PluginChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PluginRepository extends JpaRepository<Plugin, String> {

    Page<Plugin> findByChannelAndIsActive(PluginChannel channel, boolean isActive, Pageable pageable);

    @Query("SELECT p FROM Plugin p WHERE p.channel = :channel AND p.isActive = :isActive AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Plugin> searchPlugins(@Param("channel") PluginChannel channel,
                               @Param("isActive") boolean isActive,
                               @Param("query") String query,
                               Pageable pageable);

    @Modifying
    @Query("UPDATE Plugin p SET p.likesCount = p.likesCount + :likeDelta, p.dislikesCount = p.dislikesCount + :dislikeDelta WHERE p.id = :pluginId")
    int updateVoteCounts(@Param("pluginId") String pluginId,
                         @Param("likeDelta") int likeDelta,
                         @Param("dislikeDelta") int dislikeDelta);
}
