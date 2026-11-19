# RideFlow --- Event-Driven Ride-Hailing Backend

RideFlow is a Spring Boot microservices project that models the core
backend workflow of a ride-hailing platform. It tracks drivers' latest
GPS coordinates, finds nearby drivers using Redis geospatial search, and
processes ride matching asynchronously with Apache Kafka.

> **Project scope:** This README documents the architecture and
> implementation flow covered in the project walkthrough. The API
> Gateway is part of the described system architecture; the main coding
> walkthrough focuses on Location Service, Ride Service, and Matching
> Service. Features such as payments, authentication, WebSocket
> streaming, and production-grade deployment are not claimed here.

## Key features

-   Driver location updates, designed to be sent approximately every 3
    seconds.
-   Redis GEO storage and radius-based nearby-driver lookup.
-   Ride creation and persistence in MySQL.
-   Asynchronous ride matching through Kafka events.
-   Matching Service retrieves nearby drivers through Location Service
    using an HTTP client (OpenFeign in the design).
-   Driver selection based on proximity and suitability signals such as
    rating and acceptance rate in the described algorithm.
-   Ride details can be retrieved by ride ID or rider ID.

## Architecture

``` mermaid
flowchart TB
    subgraph Clients
        Rider[Rider App]
        Driver[Driver App]
    end

    Gateway[API Gateway]

    subgraph Services
        Location[Location Service<br/>Spring Boot]
        Ride[Ride Service<br/>Spring Boot]
        Matching[Matching Service<br/>Spring Boot]
    end

    Redis[(Redis GEO<br/>Driver locations)]
    MySQL[(MySQL<br/>Ride records)]
    Kafka{{Apache Kafka}}

    Rider --> Gateway
    Driver --> Gateway
    Gateway --> Location
    Gateway --> Ride

    Driver -- "GPS update ~ every 3 seconds" --> Location
    Location <--> Redis

    Rider -- "Request ride" --> Ride
    Ride --> MySQL
    Ride -- "ride.requested" --> Kafka
    Kafka -- "ride.requested" --> Matching
    Matching -- "Nearby-driver query (HTTP / OpenFeign)" --> Location
    Matching -- "Search result" --> Redis
    Matching -- "ride.matched" --> Kafka
    Kafka -- "ride.matched" --> Ride
    Ride -- "Persist assigned driver / status" --> MySQL
```

### Component responsibilities

  -----------------------------------------------------------------------
  Component                           Responsibility
  ----------------------------------- -----------------------------------
  API Gateway                         Central entry point for rider and
                                      driver requests; routes requests to
                                      backend services.

  Location Service                    Receives driver ID and GPS
                                      coordinates, updates Redis GEO,
                                      returns nearby drivers, and removes
                                      offline drivers.

  Ride Service                        Creates and stores ride records,
                                      publishes ride-request events,
                                      consumes match events, and exposes
                                      ride retrieval.

  Matching Service                    Consumes ride requests, queries
                                      nearby drivers, ranks candidates,
                                      and publishes the selected match.

  Redis                               Stores the latest driver
                                      coordinates in a geospatial
                                      structure and supports radius
                                      searches.

  MySQL                               Persists ride details and ride
                                      status.

  Kafka                               Carries asynchronous
                                      `ride.requested` and `ride.matched`
                                      events.
  -----------------------------------------------------------------------

## End-to-end workflow

1.  A driver app sends its driver ID, latitude, and longitude to
    Location Service periodically (approximately every 3 seconds in the
    walkthrough).
2.  Location Service updates that driver's member in the Redis GEO
    collection.
3.  A rider submits a ride request with pickup and drop coordinates.
4.  Ride Service saves the ride record in MySQL and marks it as
    `MATCHING`.
5.  Ride Service publishes a `ride.requested` event to Kafka.
6.  Matching Service consumes the event and requests nearby drivers from
    Location Service.
7.  Location Service performs a Redis geospatial radius search and
    returns candidate drivers with coordinates and distance.
8.  Matching Service ranks the candidates and chooses a suitable driver.
9.  Matching Service publishes `ride.matched` to Kafka.
10. Ride Service consumes the match event and updates the ride with the
    assigned driver and updated status.

## Services and APIs

The following endpoint shapes reflect the Location Service routes shown
in the walkthrough. Confirm the exact base path and request schema in
your source code before using them in a client.

### Location Service

  -------------------------------------------------------------------------------------------------------------------------------
  Method                  Endpoint shape                                                                  Purpose
  ----------------------- ------------------------------------------------------------------------------- -----------------------
  `POST`                  `/api/v1/locations/drivers/update`                                              Update a driver's
                                                                                                          location.

  `GET`                   `/api/v1/locations/drivers/nearby?latitude={lat}&longitude={lon}&radius={km}`   Find drivers within a
                                                                                                          radius.

  `DELETE`                `/api/v1/locations/drivers/{driverId}`                                          Remove a driver when
                                                                                                          they go offline.
  -------------------------------------------------------------------------------------------------------------------------------

### Ride Service

The walkthrough includes ride creation and ride retrieval by rider ID or
ride ID. Check the controller in the project for the exact route names
and payload.

## Kafka events

  ------------------------------------------------------------------------
  Topic              Producer          Consumer          Purpose
  ------------------ ----------------- ----------------- -----------------
  `ride.requested`   Ride Service      Matching Service  Starts driver
                                                         matching for a
                                                         new ride.

  `ride.matched`     Matching Service  Ride Service      Communicates the
                                                         selected driver
                                                         back to Ride
                                                         Service.
  ------------------------------------------------------------------------

The walkthrough also configures JSON event
serialization/deserialization. It addresses Kafka trusted-package
configuration during local testing.

## Data storage

### Redis GEO

Driver locations are stored under a shared geospatial key (the
walkthrough uses a drivers-location key). A driver ID is used as the GEO
member, so a subsequent location update for the same ID updates its
coordinates rather than creating a separate driver record.

Redis GEO operations demonstrated include:

-   `GEOADD` --- add or update a driver's coordinates.
-   `GEOSEARCH` --- search for members within a radius and sort by
    distance.
-   `GEODIST` --- calculate the distance between two members.
-   `GEOPOS` --- retrieve a member's coordinates.
-   `ZREM` --- remove an offline driver from the underlying geospatial
    sorted set.

**Coordinate order matters:** Redis geospatial commands expect longitude
first and latitude second.

### MySQL

MySQL stores persistent ride information, including ride ID, rider ID,
pickup and drop coordinates, ride status, and assigned driver
information.

## Technology stack

-   **Language:** Java
-   **Framework:** Spring Boot
-   **Build:** Maven
-   **Service communication:** HTTP client / OpenFeign in the matching
    flow
-   **Geospatial store:** Redis GEO
-   **Relational database:** MySQL
-   **Event streaming:** Apache Kafka
-   **Kafka coordination in the walkthrough:** ZooKeeper
-   **Local infrastructure:** Docker / Docker Compose
-   **Testing:** Postman

## Local infrastructure

The walkthrough brings up the infrastructure dependencies before running
the Spring Boot services:

-   Redis
-   MySQL
-   ZooKeeper
-   Kafka

The transcript demonstrates Docker-based startup for Redis and
Kafka/ZooKeeper, while MySQL is also shown running locally. Adapt the
commands and configuration to your own `docker-compose.yml` and
application property files.

Typical Docker Compose commands:

``` bash
docker compose up -d
docker compose ps
docker compose logs -f
docker compose down
```

## Running the services

1.  Start Redis, MySQL, ZooKeeper, and Kafka.
2.  Configure each service's `application.properties` with the correct
    database, Redis, and Kafka connection details.
3.  Start Location Service.
4.  Start Ride Service.
5.  Start Matching Service.
6.  Use Postman to update driver locations, create a ride, and retrieve
    the ride by its ID to inspect the matching result.

> The exact ports, Maven commands, environment variables, and database
> credentials depend on your local project configuration. Do not commit
> credentials to source control.

## Example request payloads

### Update driver location

``` json
{
  "driverId": "DRIVER-101",
  "latitude": 12.9716,
  "longitude": 77.5946
}
```

### Create a ride

The ride creation request uses rider identity and pickup/drop
coordinates. Use the exact DTO field names defined in your Ride Service.

## Matching strategy

The matching flow first searches for nearby drivers within a configured
radius (5 km in the walkthrough). It then ranks candidates using
distance and driver suitability signals, including rating and acceptance
rate as described in the design.

The key distinction is that the system does not rely only on a
relational-table scan for every nearby-driver lookup: Redis GEO is used
for the geospatial search.

## Repository structure

A clear multi-service repository layout can look like this:

``` text
rideflow/
├── location-service/
├── ride-service/
├── matching-service/
├── docker-compose.yml
└── README.md
```

Adjust this tree to match the actual folders in your repository.

## Testing checklist

-   [ ] Start Redis, MySQL, ZooKeeper, and Kafka.
-   [ ] Start all three Spring Boot services.
-   [ ] Send a driver location update.
-   [ ] Query nearby drivers using latitude, longitude, and radius.
-   [ ] Remove a driver and verify it no longer appears in nearby
    results.
-   [ ] Create a ride and verify it is persisted in MySQL.
-   [ ] Verify `ride.requested` is consumed by Matching Service.
-   [ ] Verify Matching Service queries nearby drivers.
-   [ ] Verify `ride.matched` is consumed by Ride Service.
-   [ ] Retrieve the ride by ride ID and verify the assigned driver and
    status.

## Design considerations and future improvements

These are potential extensions, not features claimed as implemented in
the walkthrough:

-   Add authentication and authorization for rider and driver APIs.
-   Add idempotent event processing and retry handling with a Dead
    Letter Queue.
-   Use the transactional outbox pattern to avoid inconsistencies
    between database writes and Kafka publishing.
-   Add driver reservation/locking to prevent assigning one driver to
    multiple concurrent rides.
-   Add observability with centralized logs, metrics, tracing, and
    health checks.
-   Add automated integration tests using Testcontainers.
-   Add rate limiting and validation at the API Gateway.
-   Consider Kafka consumer retry policies and failure recovery.
-   Add ride cancellation, trip lifecycle, fare calculation, and payment
    services.


