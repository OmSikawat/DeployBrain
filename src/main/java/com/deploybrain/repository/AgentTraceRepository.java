package com.deploybrain.repository;

import com.deploybrain.entity.AgentTrace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentTraceRepository extends JpaRepository<AgentTrace, UUID> {
    List<AgentTrace> findByFailureIdOrderByStepIndex(UUID failureId);
}