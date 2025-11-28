# Site BFF (Backend-For-Frontend)

![Spring Boot version](https://img.shields.io/badge/Spring_Boot-3.5.8-green)
![Java version](https://img.shields.io/badge/Java-21-blue)
![GCP version](https://img.shields.io/badge/GCP-5.1.0-yellow)
![Reactive](https://img.shields.io/badge/Reactive-WebFlux-purple)
![Maven](https://img.shields.io/badge/Build-Maven-red)
![Redis](https://img.shields.io/badge/Cache-Redis-red)
![Coverage](https://img.shields.io/badge/Coverage-95%25-brightgreen)

---------------------
## Table of Contents
* [Introduction](#introduction)
* [Architecture Overview](#architecture-overview)
  * [CQRS Pattern](#cqrs-pattern)
  * [Cache Strategy](#cache-strategy)
  * [Component Diagram](#component-diagram)
* [Getting Started](#getting-started)
  * [Prerequisites](#prerequisites)
  * [Running Locally](#running-locally)
  * [Running Tests](#running-tests)
* [Configuration](#configuration)
  * [Local Development](#local-development)
  * [Production (Cloud Run)](#production-cloud-run)
* [API Documentation](#api-documentation)
  * [Endpoints](#endpoints)
  * [Examples](#examples)
* [Key Architectural Concepts](#key-architectural-concepts)
  * [Event-Driven Architecture](#event-driven-architecture)
  * [Eventual Consistency](#eventual-consistency)
  * [Resilience & Error Handling](#resilience--error-handling)
* [Observability](#observability)
* [Authentication & Security](#authentication--security)
* [Development Guide](#development-guide)
* [Deployment](#deployment)
---------------------

## Introduction

This component is a **Backend-For-Frontend (BFF)** microservice for the Green Energy Tracker Cloud platform. It provides a reactive, highly scalable API layer built with Spring Boot and Spring WebFlux, designed to run on Google Cloud Run.

### Key Features
- **Reactive & Non-blocking**: Built with Project Reactor and WebFlux for maximum throughput
- **CQRS & Event Sourcing**: Separates read and write operations for scalability
- **Cache-Aside Pattern**: Redis caching with intelligent fallback for optimal performance
- **Cloud Native**: Designed for Google Cloud Platform with native integrations
- **Contract-First API**: OpenAPI specification with auto-generated DTOs
- **Production Ready**: Comprehensive error handling, logging, and monitoring

---

## Architecture Overview

### CQRS Pattern

The service implements **Command Query Responsibility Segregation**:

#### **Commands (Write Operations)**
- `POST /sites` - Create site
- `PUT /sites/{id}` - Full update
- `PATCH /sites/{id}` - Partial update
- `DELETE /sites/{id}` - Delete site

**Flow:**
1. BFF receives request and validates input
2. Generates unique ID and builds Protobuf message
3. Publishes event to Google Cloud Pub/Sub topic `site-events`
4. Returns `202 Accepted` immediately (asynchronous)

#### **Queries (Read Operations)**
- `GET /sites/{id}` - Retrieve single site
- `GET /sites?userId={uuid}&page={n}&size={m}` - List sites with pagination

**Flow:**
1. Check Redis cache first
2. On cache miss, query Firestore
3. Store result in cache with TTL (3600s)
4. Return `200 OK` with data

### Cache Strategy

Implements **Cache-Aside Pattern** with resilient fallback:

```
Request → Redis Cache
            ├─ HIT → Return cached data
            └─ MISS → Firestore DB
                       ├─ Found → Cache + Return
                       └─ Not Found → 404
```

**Resilience:**
- Cache failures don't block requests (automatic DB fallback)
- Configurable TTL (default: 1 hour)
- Separate cache keys for single sites and paginated lists

**Cache Invalidation:**
Handled by the separate **Processor** microservice that:
1. Consumes `site-events` from Pub/Sub
2. Writes to Firestore
3. Invalidates Redis cache keys

### Component Diagram

```
┌─────────────┐
│   Client    │
│  (Firebase  │
│    Auth)    │
└──────┬──────┘
       │ HTTPS + JWT
       ▼
┌─────────────────────────────────┐
│      Cloud Run (Auto-scale)      │
│  ┌───────────────────────────┐  │
│  │      Site BFF             │  │
│  │                           │  │
│  │  ┌─────────────────────┐ │  │
│  │  │  SiteController     │ │  │
│  │  │  - REST Endpoints   │ │  │
│  │  │  - Validation       │ │  │
│  │  └──────────┬──────────┘ │  │
│  │             │             │  │
│  │  ┌──────────▼──────────┐ │  │
│  │  │  SiteService        │ │  │
│  │  │  - Business Logic   │ │  │
│  │  │  - Pub/Sub Publish  │ │  │
│  │  └──────────┬──────────┘ │  │
│  │             │             │  │
│  │  ┌──────────▼──────────┐ │  │
│  │  │  SiteCacheService   │ │  │
│  │  │  - Cache-Aside      │ │  │
│  │  └──────────┬──────────┘ │  │
│  │             │             │  │
│  │  ┌──────────▼──────────┐ │  │
│  │  │  SiteRepository     │ │  │
│  │  │  - Firestore Access │ │  │
│  │  └─────────────────────┘ │  │
│  └───────────────────────────┘  │
└──────┬───────────────────┬──────┘
       │                   │
       │ Write             │ Read
       ▼                   ▼
┌─────────────┐     ┌──────────────┐
│  Pub/Sub    │     │   Redis      │
│ site-events │     │   (Cache)    │
└──────┬──────┘     └──────┬───────┘
       │                   │ miss
       │                   ▼
       │            ┌──────────────┐
       │            │  Firestore   │
       │            │  (Read DB)   │
       │            └──────▲───────┘
       │                   │
       ▼                   │
┌─────────────┐            │
│  Processor  │────────────┘
│  Service    │  write + invalidate cache
└─────────────┘
```

**Observability Layer (Transparent):**
- Cloud Logging (structured logs)
- Cloud Monitoring (metrics & dashboards)
- Cloud Trace (distributed tracing)
- Error Reporting (exception tracking)

---

## Getting Started

### Prerequisites

- **Java 21** (OpenJDK or Oracle JDK)
- **Apache Maven 3.9+**
- **Docker & Docker Compose** (for local emulators)
- **Google Cloud SDK** (optional, for gcloud CLI)

### Running Locally

#### 1. Start GCP Emulators & Redis

```bash
# Start Firestore, Pub/Sub, and Redis
docker-compose up -d

# Verify services are running
docker-compose ps
```

This starts:
- **Firestore Emulator** on `localhost:8695`
- **Pub/Sub Emulator** on `localhost:8085`
- **Redis** on `localhost:6379`

#### 2. Run the Application

```bash
# Using Maven wrapper (recommended)
./mvnw spring-boot:run

# Or with explicit profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

#### 3. Access API Documentation

Once started, navigate to:
- **Swagger UI**: http://localhost:8080/webjars/swagger-ui/index.html
- **OpenAPI Spec**: http://localhost:8080/v3/api-docs

### Running Tests

```bash
# Unit tests only
./mvnw test

# Unit tests + Coverage report
./mvnw clean test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

**Coverage Requirements:**
- Line Coverage: **95%**
- Branch Coverage: **90%**

**Integration tests** are executed in the CI/CD pipeline (Cloud Build) against real GCP emulators.

---

## Configuration

### Local Development

Configuration file: `src/main/resources/application-local.yaml`

```yaml
spring:
  cloud:
    gcp:
      project-id: local-project
      core:
        credentials:
          enabled: false  # No auth for emulators
      pubsub:
        emulator-host: localhost:8085
        topic:
          site-events: site-events
      firestore:
        emulator:
          enabled: true
        host-port: localhost:8695

  data:
    redis:
      host: localhost
      port: 6379
      prefix-key: site
      ttl-seconds: 3600

pagination:
  default:
    page: 0
    size: 10
```

### Production (Cloud Run)

Configuration file: `src/main/resources/application.yaml` (base) + environment variables

**Environment Variables:**

| Variable | Description | Example |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active profile | `prod` |
| `GCP_PROJECT_ID` | GCP Project ID | `green-energy-tracker-prod` |
| `PUBSUB_TOPIC_SITE_EVENTS` | Pub/Sub topic name | `site-events` |
| `REDIS_HOST` | Redis instance host | `10.0.0.3` |
| `REDIS_PORT` | Redis port | `6379` |
| `REDIS_TTL_SECONDS` | Cache TTL | `3600` |

**Cloud Run Service Configuration:**

```yaml
apiVersion: serving.knative.dev/v1
kind: Service
metadata:
  name: site-bff
  annotations:
    run.googleapis.com/ingress: all
spec:
  template:
    metadata:
      annotations:
        autoscaling.knative.dev/minScale: '1'
        autoscaling.knative.dev/maxScale: '100'
    spec:
      serviceAccountName: site-bff-sa@PROJECT_ID.iam.gserviceaccount.com
      containers:
      - image: gcr.io/PROJECT_ID/site-bff:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: prod
        resources:
          limits:
            cpu: '2'
            memory: 512Mi
```

**IAM Permissions Required:**

Service Account needs:
- `roles/pubsub.publisher` - Publish to Pub/Sub
- `roles/datastore.user` - Read from Firestore
- `roles/logging.logWriter` - Write logs
- `roles/cloudtrace.agent` - Write traces

---

## API Documentation

### Endpoints

| Method | Endpoint | Description | Response |
|--------|----------|-------------|----------|
| `POST` | `/sites` | Create new site | `202 Accepted` |
| `GET` | `/sites/{id}` | Get site by ID | `200 OK` |
| `GET` | `/sites?userId={uuid}&page={n}&size={m}` | List sites (paginated) | `200 OK` |
| `PUT` | `/sites/{id}` | Full update site | `202 Accepted` |
| `PATCH` | `/sites/{id}` | Partial update site | `202 Accepted` |
| `DELETE` | `/sites/{id}` | Delete site | `202 Accepted` |

### Examples

#### Create Site

**Request:**
```bash
curl -X POST http://localhost:8080/sites \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "name": "Main Solar Farm",
    "address": "123 Green Way, Solar City, CA",
    "location": {
      "latitude": 34.0522,
      "longitude": -118.2437
    }
  }'
```

**Response (202 Accepted):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ACCEPTED",
  "message": "Request queued. Trace ID: 1234567890"
}
```

#### Get Site

**Request:**
```bash
curl http://localhost:8080/sites/550e8400-e29b-41d4-a716-446655440000
```

**Response (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
  "name": "Main Solar Farm",
  "address": "123 Green Way, Solar City, CA",
  "location": {
    "latitude": 34.0522,
    "longitude": -118.2437
  },
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

#### List Sites

**Request:**
```bash
curl "http://localhost:8080/sites?userId=a1b2c3d4-e5f6-7890-1234-567890abcdef&page=0&size=10"
```

**Response (200 OK):**
```json
{
  "sites": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
      "name": "Main Solar Farm",
      "address": "123 Green Way, Solar City, CA",
      "location": {
        "latitude": 34.0522,
        "longitude": -118.2437
      }
    }
  ],
  "totalCount": 1,
  "totalPages": 1
}
```

#### Error Response

**Response (400 Bad Request):**
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for request",
  "path": "/sites",
  "validationErrors": {
    "name": "must not be blank",
    "userId": "must be a valid UUID"
  }
}
```

---

## Key Architectural Concepts

### Event-Driven Architecture

All write operations publish events to Pub/Sub instead of directly modifying the database:

**Benefits:**
- **Decoupling**: BFF doesn't know about database writes
- **Scalability**: Async processing allows high throughput
- **Audit Trail**: All changes captured as events
- **Extensibility**: New consumers can subscribe to events

**Published Event Structure:**
```protobuf
// Protobuf message
message Site {
  string id = 1;
  string user_id = 2;
  string name = 3;
  string address = 4;
  GeoLocation location = 5;
  string created_at = 6;
  string updated_at = 7;
}

// Pub/Sub message attributes
{
  "event_type": "CREATE|UPDATE|PATCH|DELETE",
  "entity_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Eventual Consistency

**Important:** Read operations are eventually consistent with writes.

**Timeline Example:**
```
T0: POST /sites → 202 Accepted
T1: Event published to Pub/Sub
T2: Processor consumes event
T3: Processor writes to Firestore
T4: Processor invalidates cache
T5: GET /sites/{id} → 200 OK (data visible)
```

Typical delay: **< 500ms** (p95)

### Resilience & Error Handling

#### GCP Client Resilience (Built-in)

All GCP clients have automatic retry with exponential backoff:

**Pub/Sub Publisher:**
- Retry attempts: 3
- Initial delay: 100ms
- Max delay: 60s
- Timeout: 30s

**Firestore Client:**
- Retry attempts: 5
- Exponential backoff
- gRPC connection pooling
- Auto-reconnection

**Redis (Lettuce):**
- Auto-reconnection
- Connection pooling
- Fallback to DB on error (implemented in `SiteCacheServiceImpl`)

#### Error Handling

Centralized exception handling in `SiteControllerAdvice`:

| Exception | HTTP Status | Description |
|-----------|-------------|-------------|
| `WebExchangeBindException` | 400 | Validation errors |
| `ResponseStatusException` | varies | Business logic errors |
| `SiteProcessingException` | 503 | Processing failures |
| `Exception` | 500 | Unexpected errors |

All errors return standardized `ApiErrorDto` with:
- Timestamp
- HTTP status code
- Error message
- Request path
- Validation errors (if applicable)

---

## Observability

### Logging

**Structured JSON logging** with Cloud Logging integration:

```java
log.info("Site created: siteId={}, userId={}, messageId={}",
         id, request.getUserId(), messageId);
```

**Log Levels:**
- `INFO`: Normal operations (site created, cache hit/miss)
- `WARN`: Recoverable errors (cache failure with fallback)
- `ERROR`: Non-recoverable errors (Pub/Sub publish failed)

### Metrics

Automatically exported to Cloud Monitoring:

**Application Metrics:**
- Request rate (req/s)
- Error rate (%)
- Latency (p50, p95, p99)
- Cache hit rate (%)

**Infrastructure Metrics:**
- CPU utilization
- Memory usage
- Active connections
- JVM heap/garbage collection

### Tracing

**Cloud Trace** integration for distributed tracing:

```
Request [trace-id: abc123]
  ├─ SiteController.createSite (2ms)
  ├─ SiteService.create (5ms)
  │  └─ PubSubPublisher.publish (150ms)
  └─ Total: 157ms
```

### Health Checks

```bash
# Liveness probe
curl http://localhost:8080/actuator/health/liveness

# Readiness probe
curl http://localhost:8080/actuator/health/readiness
```

---

## Authentication & Security

### Local Development
No authentication required when using emulators.

### Production (Cloud Run + Firebase)

#### Client Authentication Flow:

1. **Client authenticates with Firebase Auth**
   - Receives JWT ID token

2. **Client calls BFF with token**
   ```bash
   curl -H "Authorization: Bearer <firebase-id-token>" \
        https://site-bff-xxx.run.app/sites
   ```

3. **Cloud Run validates token**
   - Firebase Auth verifies JWT signature
   - Extracts user claims (userId, email, etc.)

4. **BFF processes request**
   - Access user info from security context
   - Apply authorization rules

#### Security Configuration

**Service Account Permissions:**
- Minimum privileges (least privilege principle)
- No service account key files (uses ADC)

**Network Security:**
- HTTPS enforced
- Cloud Run ingress control
- VPC connector for Redis/internal services

**Data Protection:**
- Secrets in Google Secret Manager
- No credentials in source code
- Environment-based configuration

---

## Development Guide

### Project Structure

```
site-bff/
├── src/
│   ├── main/
│   │   ├── java/.../site_bff/
│   │   │   ├── config/          # Redis, etc.
│   │   │   ├── controller/      # REST endpoints
│   │   │   ├── exception/       # Error handling
│   │   │   ├── model/           # Domain models
│   │   │   ├── repository/      # Firestore repos
│   │   │   └── service/         # Business logic
│   │   └── resources/
│   │       ├── application.yaml
│   │       └── application-local.yaml
│   └── test/
│       └── java/.../site_bff/   # Unit tests
├── target/
│   └── generated-sources/       # OpenAPI generated code
├── docker-compose.yml           # Local emulators
├── pom.xml                      # Maven dependencies
└── README.md
```

### Code Conventions

- **Reactive Programming**: Use `Mono<T>` and `Flux<T>` consistently
- **Lombok**: Use for boilerplate reduction (`@Data`, `@RequiredArgsConstructor`)
- **MapStruct**: Auto-generate mappers between DTOs and entities
- **Dependency Injection**: Constructor injection (required by Lombok)
- **Validation**: Use Bean Validation annotations (`@Valid`, `@NotNull`)

### Adding New Endpoints

1. **Update OpenAPI contract** in `api-contracts` repository
2. **Rebuild project** to generate DTOs
3. **Implement in Controller** (implement generated interface)
4. **Add business logic** in Service layer
5. **Write tests** (unit + integration)

### Dependencies Management

**Key Libraries:**

| Library | Version | Purpose |
|---------|---------|---------|
| Spring Boot | 3.5.8 | Framework |
| Spring WebFlux | (included) | Reactive web |
| Spring Cloud GCP | 5.1.0 | GCP integrations |
| Redis Reactive | (included) | Caching |
| Protobuf | 4.28.2 | Message serialization |
| MapStruct | 1.6.3 | Object mapping |
| JaCoCo | 0.8.11 | Code coverage |

**External Dependency:**
- `api-contracts:1.1.1` - OpenAPI specs from Google Artifact Registry

---

## Deployment

### Cloud Build Pipeline

**Pipeline Stages:**

```yaml
# cloudbuild.yaml (simplified)
steps:
  # 1. Build
  - name: maven:3-openjdk-21
    args: ['mvn', 'clean', 'package', '-DskipTests']

  # 2. Unit Tests
  - name: maven:3-openjdk-21
    args: ['mvn', 'test']

  # 3. Integration Tests (with emulators)
  - name: maven:3-openjdk-21
    args: ['mvn', 'verify', '-P', 'integration-tests']
    env: [FIRESTORE_EMULATOR_HOST, PUBSUB_EMULATOR_HOST]

  # 4. Build Docker Image
  - name: gcr.io/cloud-builders/docker
    args: ['build', '-t', 'gcr.io/$PROJECT_ID/site-bff:$SHORT_SHA', '.']

  # 5. Push to Container Registry
  - name: gcr.io/cloud-builders/docker
    args: ['push', 'gcr.io/$PROJECT_ID/site-bff:$SHORT_SHA']

  # 6. Deploy to Cloud Run
  - name: gcr.io/google.com/cloudsdktool/cloud-sdk
    args: ['gcloud', 'run', 'deploy', 'site-bff', ...]
```

### Dockerfile (Multi-stage)

```dockerfile
# Build stage
FROM maven:3-openjdk-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Environment-Specific Configs

Use Spring Profiles:
- `local` - Local development with emulators
- `dev` - Development environment on GCP
- `staging` - Staging environment
- `prod` - Production environment

---

## Monitoring & Alerts

### Recommended Alerts

**High Priority:**
- Error rate > 5% (5 min window)
- P95 latency > 2s
- Pub/Sub publish failures > 1%
- Memory usage > 90%

**Medium Priority:**
- Cache hit rate < 70%
- Firestore read latency > 500ms
- Container restart rate > 2/hour

### Dashboards

**Key Metrics to Monitor:**
- Request throughput (req/s)
- Error rate by endpoint
- Cache performance (hit rate, latency)
- Pub/Sub publish success rate
- Firestore query performance
- JVM metrics (heap, GC)

---

## Troubleshooting

### Common Issues

**Issue: Cache connection failures**
```
Solution: Check Redis connectivity and credentials
Command: redis-cli -h <host> -p <port> PING
```

**Issue: Pub/Sub publish timeouts**
```
Solution: Verify topic exists and service account has publisher role
Command: gcloud pubsub topics describe site-events
```

**Issue: Firestore emulator not reachable**
```
Solution: Ensure docker-compose is running
Command: docker-compose ps
```

### Debug Logging

Enable debug logging for specific components:

```yaml
logging:
  level:
    com.green.energy.tracker.cloud.site_bff: DEBUG
    org.springframework.cloud.gcp.pubsub: DEBUG
    org.springframework.data.redis: DEBUG
```

---

## Contributing

### Quality Gates

Before submitting PR:
- [ ] All tests pass (`./mvnw test`)
- [ ] Code coverage ≥ 95% line, 90% branch
- [ ] No checkstyle violations
- [ ] OpenAPI contract updated (if API changes)
- [ ] README updated (if applicable)

### Commit Message Format

```
type(scope): subject

body

footer
```

**Example:**
```
feat(cache): add TTL configuration for site cache

- Add spring.data.redis.ttl-seconds property
- Update SiteCacheServiceImpl to use configurable TTL
- Add tests for TTL configuration

Closes #123
```

---

## License

Copyright © 2024 Green Energy Tracker Cloud
All rights reserved.

---

## Support

For issues or questions:
- **Issues**: GitHub Issues
- **Documentation**: Confluence Wiki
- **Chat**: Slack #site-bff-support
