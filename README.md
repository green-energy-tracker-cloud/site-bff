# Site BFF (Backend-For-Frontend)

![Spring Boot version](https://img.shields.io/badge/Spring_Boot-3.5.8-green)
![Java version](https://img.shields.io/badge/Java-21-blue)
![GCP version](https://img.shields.io/badge/GCP-5.1.0-yellow)
![Reactive](https://img.shields.io/badge/Reactive-WebFlux-purple)
![Maven](https://img.shields.io/badge/Build-Maven-red)

---------------------
* [Introduction](#introduction)
* [Getting Started](#getting-started)
  * [Prerequisites](#prerequisites)
  * [Running Locally](#running-locally)
* [Configuration](#configuration)
* [API and Event Examples](#api-and-event-examples)
  * [Command: Creating a New Site](#command-creating-a-new-site)
  * [Query: Retrieving a Site](#query-retrieving-a-site)
* [Key Architectural Concepts](#key-architectural-concepts)
* [Authentication](#authentication)
---------------------

## Introduction
This component is a Spring Boot application that serves as a **Backend-For-Frontend (BFF)** for the Green Energy Tracker Cloud platform. It is built using a reactive stack (Spring WebFlux) and designed to be highly scalable and resilient.

The service follows a **CQRS (Command Query Responsibility Segregation)** and **Event-Driven** architecture:
- **Commands** (writes, such as creating, updating, or deleting a site) are processed asynchronously. The service receives the request, validates it, and publishes a corresponding event to a Google Cloud Pub/Sub topic. It does **not** directly write to the database.
- **Queries** (reads, such as fetching site details or a list of sites) are served directly from a read-optimized data store (Google Cloud Firestore), ensuring low-latency responses.

This architectural pattern decouples write and read operations, enhancing scalability, resilience, and flexibility.

## Getting Started

### Prerequisites
- Java 21
- Apache Maven
- Docker and Docker Compose

### Running Locally
The application is configured to run against local GCP emulators for development, which are defined in the `docker-compose.yml` file.

1.  **Start the GCP Emulators:**
    Open a terminal in the project root and run:
    ```bash
    docker-compose up
    ```
    This will start local emulators for **Firestore** and **Pub/Sub**.

2.  **Run the Spring Boot Application:**
    In a separate terminal, run the application using the Maven wrapper:
    ```bash
    ./mvnw spring-boot:run
    ```
    The application will automatically connect to the local emulators defined in `application.properties`.

Once started, the API documentation will be available at:
[http://localhost:8080/webjars/swagger-ui/index.html](http://localhost:8080/webjars/swagger-ui/index.html)

## Configuration
The main configuration is located in `src/main/resources/application.properties`. Key properties for local development include:

```properties
# Server port
server.port=8080

# GCP Pub/Sub topic for publishing site events
spring.cloud.gcp.pubsub.topic.site-events=site-events

# --- Emulator Settings for Local Development ---

# Instructs the application to use the Pub/Sub emulator
spring.cloud.gcp.pubsub.emulator-host=localhost:8085

# Instructs the application to use the Firestore emulator
spring.cloud.gcp.firestore.host-port=localhost:8695
spring.cloud.gcp.firestore.project-id=local-project
```

For different environments (e.g., `dev`, `prod`), you would typically use Spring Profiles to override these settings, particularly to remove the emulator configuration and point to real GCP services.

## API and Event Examples

### Command: Creating a New Site
To create a new site, you send a `POST` request to the `/sites` endpoint.

**API Request (`POST /sites`):**
```json
{
  "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
  "name": "Main Solar Farm",
  "address": "123 Green Way, Solar City, CA",
  "location": {
    "latitude": 34.0522,
    "longitude": -118.2437
  }
}
```

**Service Action:**
1. The BFF receives the request and validates it.
2. It generates a unique ID for the new site.
3. It publishes a `Site` message (using Protobuf) to the `site-events` Pub/Sub topic with an attribute `event_type: "CREATE"`.

**Published Pub/Sub Message (Payload):**
```protobuf
// Protobuf representation of the Site message
id: "generated-uuid-goes-here"
user_id: "a1b2c3d4-e5f6-7890-1234-567890abcdef"
name: "Main Solar Farm"
address: "123 Green Way, Solar City, CA"
location {
  latitude: 34.0522
  longitude: -118.2437
}
created_at: "..."
updated_at: "..."
```

**API Response (`202 Accepted`):**
The API immediately returns a `202 Accepted` response, indicating the request has been queued for processing.
```json
{
  "id": "generated-uuid-goes-here",
  "status": "ACCEPTED",
  "message": "Request queued. Trace ID: <pubsub-message-id>"
}
```

### Query: Retrieving a Site
To retrieve a site, you send a `GET` request. This data is read from Firestore and is therefore eventually consistent with the commands.

**API Request (`GET /sites/{id}`):**
`/sites/generated-uuid-goes-here`

**API Response (`200 OK`):**
```json
{
  "id": "generated-uuid-goes-here",
  "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
  "name": "Main Solar Farm",
  "address": "123 Green Way, Solar City, CA",
  "location": {
    "latitude": 34.0522,
    "longitude": -118.2437
  }
}
```

## Key Architectural Concepts
The **CQRS** pattern is central to this service's design. It's crucial to understand its implications:

- **Asynchronous Operations:** All write operations (create, update, delete) are asynchronous. The API response confirms that the command has been *accepted*, not that it has been *completed*.
- **Eventual Consistency:** There is a short delay between the moment a command is accepted and when its result becomes visible in the read model (Firestore). The system that consumes the Pub/Sub events is responsible for updating the Firestore database. This means that a `GET` request immediately following a `POST` might not yet reflect the new data.
- **Separation of Models:** The model used for writing (commands, Protobuf messages) is different from the model used for reading (queries, DTOs from Firestore). This allows each model to be optimized for its specific purpose.

## Authentication
- **Local Development:** When running with the GCP emulators, no authentication is required. The application connects to the emulators without credentials.
- **Deployed Environments (GCP):** When deployed to a Google Cloud environment (e.g., Cloud Run, GKE), the application uses **Application Default Credentials (ADC)**. It automatically authenticates using the credentials of the runtime service account, which is the standard and most secure practice on GCP. No manual credential management is needed in the code.
