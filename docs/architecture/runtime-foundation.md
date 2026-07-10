# Runtime Foundation

## Entry point
- `ProjectApplication`
  - Bat `@SpringBootApplication`
  - Bat `@ConfigurationPropertiesScan`
  - Bat `@EnableCaching`
  - Set default timezone `Asia/Ho_Chi_Minh`

## Response va exception foundation
- Moi business API su dung `ApiResponse<T>`.
- Offset list dung `PageResponse<T>`.
- Cursor list dung `CursorPageResponse<T>`.
- `GlobalExceptionHandler` la entrypoint chuan cho response loi.
- Service business nem `BaseException` hoac runtime exception co y nghia ro rang, khong tu build `ResponseEntity`.

## Security foundation
- `SecurityConfig` dung `SecurityFilterChain` stateless.
- `JwtAuthenticationFilter` doc access token tu `Authorization: Bearer ...`.
- STOMP handshake duoc allow o `/ws-stomp`, nhung xac thuc that su xay ra tai `CONNECT`.

## Async/Scheduling foundation
- Ownership chot tai `infrastructure.config`.
- Tat ca executors phai:
  - co ten bean ro rang
  - co thread prefix ro rang
  - co `TaskDecorator` de carry MDC trace context
- `@EnableAsync` va `@EnableScheduling` chi nen duoc khai bao mot lan o lop owner chinh.

## Realtime / Outbox foundation
- REST la source of truth cho chat va notification.
- Pipeline realtime production:
  - business transaction
  - ghi `domain_event_outbox`
  - publisher polling `FOR UPDATE SKIP LOCKED`
  - RabbitMQ
  - consumer
  - STOMP relay
- Trace ID phai di kem outbox record va duoc dua vao message headers khi publish neu context ton tai.

## Health foundation
- Legacy `/health` van giu de smoke check nhanh.
- Production probes chuan dung Actuator:
  - `/actuator/health`
  - `/actuator/health/liveness`
  - `/actuator/health/readiness`
- Chi expose health probes, khong mo rong public them endpoint Actuator khac trong Phase 1.
