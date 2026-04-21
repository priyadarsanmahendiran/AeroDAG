package com.aerodag.core.repository;

import com.aerodag.core.domain.entity.Node;
import com.aerodag.core.domain.entity.NodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NodeRepository extends JpaRepository<Node, UUID> {

    List<Node> findByPlanId(UUID planId);

    List<Node> findByPlanIdAndStatus(UUID planId, NodeStatus status);
}
