# Error Contract Operations

This is an enhancement to the existing `ApiResponse` error contract. The JSON
shape and published error codes remain unchanged.

## Correlation ID

Every HTTP request receives a correlation identifier at the request boundary.
The backend reuses a valid `X-Correlation-ID` supplied by the client; when it is
missing or unusable, it generates a UUID. The response includes:

- `X-Correlation-ID` — the preferred header for FE support requests.
- `X-Request-Id` — retained as a compatibility alias for existing clients.

The header is CORS-allowed and CORS-exposed, so browser FE code can read it even
when the API is on another origin:

```typescript
const response = await fetch(url, request);
const correlationId = response.headers.get('X-Correlation-ID');
const body = await response.json();
```

The JSON envelope does not gain a `correlationId` field. Existing FE parsing of
`timestamp`, `status`, `code`, `message`, `data`, and
`retryAfterSeconds` therefore continues to work unchanged.

## Backend logging

The request boundary stores the ID in MDC under both the existing `requestId`
and `traceId` keys. Handled API and security errors log the stable error code,
HTTP status, correlation ID, method, and path. A small allow-list may add
non-sensitive identifiers such as `bookingId`, `courseId`, or `paymentOrderId`.
Operational logs can be searched with:

```text
correlationId=<value>
```

Request bodies, authorization headers, JWTs, refresh tokens, payment secrets,
storage credentials, and personal sensitive data are not included in these
error logs. Unexpected server exceptions keep their stack traces in backend
logs only; the FE receives the generic published server error message.

## Retry metadata decision

`retryAfterSeconds` remains scoped to rate limiting. A `429` raised by
`RateLimitExceededException` includes both the JSON field and the HTTP
`Retry-After` header. Validation, permission, expired-resource, and invalid-
state errors do not include retry hints.

Payment provider failures remain `502` without a retry hint. Checkout can have
already created or mutated a payment attempt, so automatic retry is not safe
without the existing payment/idempotency flow deciding how to resume it.
