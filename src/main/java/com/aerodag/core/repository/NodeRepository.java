package com.aerodag.core.repository;

import com.aerodag.core.domain.entity.Node;
import com.aerodag.core.domain.entity.NodeStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NodeRepository extends JpaRepository<Node, UUID> {

  List<Node> findByPlanId(UUID planId);

  List<Node> findByPlanIdAndStatus(UUID planId, NodeStatus status);

  List<Node> findByPlanIdAndNodeIdIn(UUID planId, List<String> nodeIds);
}
