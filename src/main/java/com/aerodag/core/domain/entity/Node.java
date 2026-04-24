package com.aerodag.core.domain.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import java.util.List;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "nodes")
public class Node {

  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(name = "node_id")
  private String nodeId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private NodeStatus status;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String instruction;

  @Column(columnDefinition = "TEXT")
  private String resultPayload;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "plan_id", nullable = false)
  private Plan plan;

  @Type(JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  private List<String> dependencies;

  @Type(JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  private List<String> toolsAllowed;
}
