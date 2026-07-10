# Observability Policy

## Trace / Correlation ID
- Header chuan: `X-Request-Id`
- MDC key chuan:
  - `requestId`
  - `traceId`
- Trong Phase 1, `requestId == traceId`
- Neu client gui `X-Request-Id` hop le:
  - he thong tai su dung gia tri do
- Neu client khong gui:
  - he thong tu sinh trace id

## Noi trace id phai xuat hien
- request start/end log
- exception log
- response error header
- async tasks thong qua `TaskDecorator`
- outbox record neu context ton tai
- Rabbit publish headers tu outbox
- consumer log khi restore duoc trace context

## Error response policy
- Khong doi success envelope trong Phase 1.
- Error responses phai kem `X-Request-Id` header.
- Neu sau nay can expose trace id trong body loi, phai thong qua phase rieng va cap nhat API docs.

## Structured logging
- Logger pattern console hien tai dung MDC `requestId`.
- Trace policy cua Phase 1 yeu cau bo sung MDC `traceId` de giu y nghia ro rang cho production tooling.
- Log cua scheduler/consumer khong duoc tao trace id gia khi khong co context; chi restore trace id neu outbox/message co metadata.

## Health / Readiness
- Public health probes cho orchestrator:
  - `/actuator/health`
  - `/actuator/health/liveness`
  - `/actuator/health/readiness`
- Readiness phai phan anh kha nang phuc vu request cua app o muc runtime co y nghia.
- Khong expose them management endpoint ngoai health probes trong Phase 1.
