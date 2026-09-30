package com.github.deployedreject.Aggregator_backend.repository;

import com.github.deployedreject.Aggregator_backend.entity.ModerationAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModerationAlertRepository extends JpaRepository<ModerationAlert, Long> {

    boolean existsByPluginIdAndResolvedFalse(String pluginId);

    List<ModerationAlert> findByPluginIdOrderByAlertedAtDesc(String pluginId);

    List<ModerationAlert> findByResolvedFalseOrderByAlertedAtDesc();
}
