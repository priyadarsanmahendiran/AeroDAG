package com.aerodag.core.repository;

import com.aerodag.core.domain.entity.Plan;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, UUID> {}
