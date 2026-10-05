# medicore-appointment-service

The booking engine of the **MediCore** healthcare platform
([monorepo](https://github.com/Vamshikrishna720/medicore)) — and the microservices showcase:

## Highlights

- **OpenFeign** clients to doctor-service & patient-service (Eureka load-balanced, internal token propagated)
- **Resilience4j** circuit breakers + retry + time limiters with fallback factories → clean 503s instead of cascading failures
- **Double-booking prevention**: 30-minute slot grid + doctor availability-window check + JPQL overlap query on composite index `(doctor_id, appointment_date)` + `@Version` optimistic locking
- **Event-driven notifications**: `@TransactionalEventListener(AFTER_COMMIT)` → `@Async` Feign call to notification-service — a failed email can never roll back a booking
- **Streams API** admin stats (`groupingBy` + `counting`); ownership checks (patients cancel only their own, doctors manage only their own)

## Endpoints (via gateway, `/api`)

| Method | Path | Access |
|---|---|---|
| POST | `/appointments` | PATIENT |
| GET | `/appointments` | ADMIN |
| GET | `/appointments/me/patient`, `/appointments/me/doctor` | PATIENT / DOCTOR |
| PATCH | `/appointments/{id}/cancel` | owner, DOCTOR (own), ADMIN |
| PATCH | `/appointments/{id}/status?status=CONFIRMED\|COMPLETED` | DOCTOR (own), ADMIN |
| GET | `/appointments/stats` | ADMIN |

## Run

> **Prerequisite:** this repo depends on `com.medicore:medicore-common:1.0.0`. Install it to your local Maven repo first — clone [medicore-common](https://github.com/Vamshikrishna720/medicore-common) and run `mvn clean install` there. CI has the same requirement (publishing common to GitHub Packages would make this repo fully self-contained).

```bash
mvn spring-boot:run          # :8084 (needs MySQL + Eureka + doctor/patient/notification services)
```

Swagger: `http://localhost:8084/swagger-ui/index.html`
