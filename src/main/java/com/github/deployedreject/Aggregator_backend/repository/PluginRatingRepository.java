package com.github.deployedreject.Aggregator_backend.repository;

import com.github.deployedreject.Aggregator_backend.entity.PluginRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PluginRatingRepository extends JpaRepository<PluginRating, Long> {

    Optional<PluginRating> findByPluginIdAndVoterHash(String pluginId, String voterHash);

    void deleteByPluginId(String pluginId);
}
