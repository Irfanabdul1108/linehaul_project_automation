package com.example.linehaul.repository;

import com.example.linehaul.model.LaneDuration;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface LaneDurationRepository extends MongoRepository<LaneDuration, String> {

    Optional<LaneDuration> findByLaneKey(String laneKey);

    boolean existsByLaneKey(String laneKey);
}
