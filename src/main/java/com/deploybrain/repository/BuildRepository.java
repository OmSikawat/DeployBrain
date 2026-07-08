package com.deploybrain.repository;

import com.deploybrain.entity.Build;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BuildRepository extends JpaRepository<Build, UUID> {
    Page<Build> findAllByOrderByTriggeredAtDesc(Pageable pageable);
    Optional<Build> findByWorkflowRunId(Long workflowRunId);
}