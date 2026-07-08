package com.deploybrain.repository;

import com.deploybrain.entity.Failure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FailureRepository extends JpaRepository<Failure, UUID> {
    Optional<Failure> findByBuildId(UUID buildId);

    List<Failure> findTop3ByBuildRepoNameAndFailureTypeAndAgentStatusOrderByCreatedAtDesc(
            String repoName,
            Failure.FailureType failureType,
            Failure.AgentStatus agentStatus
    );
}