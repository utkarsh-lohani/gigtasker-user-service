# 👤 GigTasker User Service

This service is the **"Membership Manager"** for the GigTasker platform. It is the single, authoritative microservice responsible for all business logic and data related to user profiles.

It serves as the **Single Source of Truth (SSOT)** for internal user data. While Keycloak handles *authentication* (who you are), this service handles *profile data* (your name, your associated bids/tasks, and your internal Postgres `id`).

---

## ✨ Core Responsibilities

* **Just-In-Time (JIT) Provisioning:** This service's most critical feature. The first time a user logs in, the platform calls the `/api/v1/users/me` endpoint. This service takes the user's valid Keycloak token, extracts their details (email, name), and *creates a new profile* for them in its own `gig_users` Postgres table.

* **Profile Management:** Provides the central `GET /api/v1/users/me` endpoint that other services (like `bid-service`) can call to securely get the profile of the currently logged-in user.

* **Batch Data Provider:** Exposes a `POST /api/v1/users/batch` endpoint. This allows other services (like `bid-service`) to send a list of user IDs (e.g., `[1, 5, 42]`) and get back a "zipped" list of rich `UserDTO` objects (with names, etc.) in a single, efficient network call.

* **Security:** Acts as a secure **OAuth2 Resource Server**. It validates a Keycloak JWT on *every* request to ensure the user is authenticated and authorized.

---

## 🛠️ Tech Stack

* **Framework:** Spring Boot 3
* **Language:** Java 25
* **Database:** Spring Data JPA with PostgreSQL
* **Security:** Spring Security (OAuth2 Resource Server) for JWT validation.
* **Platform:**
    * Spring Cloud Config Client (for configuration)
    * Spring Cloud Netflix Eureka Client (for service discovery)

---

## 🔌 Service Communication

This is a foundational service. It is "called by" many but "calls" none.

### Inbound (Its API - `/api/v1/users`)

This service exposes the following internal endpoints, which are routed by the `api-gateway` or called by other services:

* **`GET /me`**: (The JIT Endpoint) Securely gets the profile for the user associated with the provided JWT. If no profile exists, it **creates one**.
* **`GET /{id}`**: Retrieves the public-facing `UserDTO` for a specific user ID. Used to get a task poster's name.
* **`POST /batch`**: (Internal) Takes a `List<Long>` of user IDs and returns a `List<UserDTO>`.
* **`POST /`**: Creates a new user (though this is primarily handled by the `/me` endpoint).

### Outbound (Calls to Other Services)

* **None.** This service is a self-contained "source of truth."

### Outbound (Events Published to RabbitMQ)

* **None.** This service currently only provides data on request.

---

## ⚙️ Configuration

This service gets its configuration from the `config-server` on startup.

* **Base Config (`user-service.yml`):** Contains all database credentials, JPA settings, and security settings (like the Keycloak `issuer-uri`).
* **Local Profile (`user-service-local.yml`):** Overrides the network settings for local development, specifically setting `eureka.instance.hostname: localhost` to fix the Docker networking issue.
* **Data Model:** This service is the owner of the `gig_users` table.

---

## 🚀 How to Run

1.  **Start Dependencies (CRITICAL):**
    * Run `docker-compose up -d` (for **Postgres**, RabbitMQ, Keycloak).
    * Start the `config-server`.
    * Start the `service-registry`.

2.  **Run this Service:**
    Once the config and registry are running, you can start this service. It should be started *before* any service that depends on it (like `bid-service`).
    ```bash
    # From your IDE, run UserServiceApplication.java
    # Or, from the command line:
    java -jar target/user-service-0.0.1.jar
    ```

This service will start on a **random port** (as defined by `server.port: 0`) and register itself with the Eureka `service-registry`.