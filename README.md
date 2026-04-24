# AeroDAG

Enterprise-grade Plan-then-Execute AI Orchestrator. Takes a natural language objective, decomposes it into a Directed Acyclic Graph (DAG) of tasks using Claude, persists the plan, and executes nodes in dependency order — with automatic mid-execution replanning when nodes fail.

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 17, Spring Boot 3.3 |
| AI | Spring AI 1.0.0-M6 + Anthropic Claude |
| Persistence | PostgreSQL 15, Spring Data JPA, Flyway |
| JSONB mapping | hypersistence-utils-hibernate-63 3.8.2 |
| Cache / Queue | Redis (redis-stack) |
| API Docs | springdoc-openapi (Swagger UI) |
| Testing | JUnit 5, Testcontainers |

## How It Works

1. **Plan** — A natural language objective is sent to Claude, which returns a DAG of atomic tasks as JSON.
2. **Persist** — The `Plan` and its `Node` entities are saved to PostgreSQL. Root nodes (no dependencies) are pushed to a Redis queue.
3. **Execute** — `RedisNodeWorkerListener` polls the queue, runs each node's instruction via Claude, and saves the result payload.
4. **Advance** — On `NodeCompletedEvent`, the orchestrator checks if dependent nodes are unblocked and queues them.
5. **Replan** — On `NodeFailedEvent`, `DynamicReplanningService` cancels all remaining PENDING nodes, summarises what succeeded and what failed, and re-prompts Claude to generate a new DAG attached to the same `Plan`.

## Node Lifecycle

```
PENDING → RUNNING → COMPLETED
                  ↘ FAILED
PENDING → CANCELLED  (when a sibling fails and the plan is replanned)
```

## Prerequisites

- Java 17+
- Maven 3.9+
- Docker + Docker Compose
- An Anthropic API key

## Running Locally

**1. Start infrastructure**

```bash
docker compose up -d
```

This starts:
- PostgreSQL 15 on `localhost:5432`
- Redis Stack on `localhost:6379` (RedisInsight UI on `localhost:8001`)

**2. Set environment variables**

```bash
export ANTHROPIC_API_KEY=sk-ant-...
# Optional overrides (defaults shown)
export DB_USERNAME=aerodag
export DB_PASSWORD=aerodag
export REDIS_HOST=localhost
export REDIS_PORT=6379
```

**3. Run the application**

```bash
./mvnw spring-boot:run
```

Flyway migrations run automatically on startup. The app will be available at `http://localhost:8080`.

## API Documentation

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Project Structure

```
src/main/java/com/aerodag/
├── AeroDagApplication.java
└── core/
    ├── domain/
    │   ├── dto/          # LLM response records (DagGenerationResponse, NodeResponse)
    │   └── entity/       # JPA entities (Plan, Node) + enums (NodeStatus, PlanStatus)
    ├── exception/        # DagGenerationException
    ├── messaging/
    │   ├── event/        # NodeCompletedEvent, NodeFailedEvent
    │   ├── listener/     # RedisNodeWorkerListener — polls queue, runs LLM, publishes events
    │   └── publisher/    # NodeQueuePublisher — pushes node UUIDs onto Redis list
    ├── repository/       # PlanRepository, NodeRepository
    └── service/
        └── planner/      # PlannerService — initial DAG generation
                          # DynamicReplanningService — failure recovery + replanning
```

## Configuration

Key properties in `application.yml`:

| Property | Default | Description |
|---|---|---|
| `ANTHROPIC_API_KEY` | — | Required. Anthropic API key |
| `DB_USERNAME` | `aerodag` | PostgreSQL username |
| `DB_PASSWORD` | `aerodag` | PostgreSQL password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PASSWORD` | _(empty)_ | Redis password |

## License

MIT — see [LICENSE](LICENSE).
