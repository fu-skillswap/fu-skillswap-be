# Cache Policy

## Principles

- Caffeine is a local performance cache, never a business source of truth.
- Cache immutable DTOs, normalized strings, and neutral IDs rather than JPA
  entities or viewer-specific responses.
- Mutable cached data requires a TTL and an after-commit invalidation path.
- A cache miss must always be safe and correct.

## Never Cache

The following require database revalidation or a durable transactional source:

- Booking state, candidate availability, quotas, and overlap checks.
- Payment orders, wallet balances, ledger entries, settlement, refunds, and
  payout eligibility.
- Slot availability, booking policy decisions, and scheduled time locks.
- Notification unread state and read state.
- Chat access permissions, conversation locks, and attachment entitlement.
- Authentication sessions, refresh-token rotation, and authorization decisions
  other than the bounded ban-status optimization already invalidated after
  commit.

## Approved Cache Patterns

- Master catalog data with event invalidation.
- Scoped bounded rate-limit buckets for one backend instance. Security buckets
  are isolated from business, transfer and best-effort traffic and fail closed
  when their configured capacity is exhausted.
- One-time OAuth state with short TTL and atomic consume.
- Immutable matching features with invalidation after questionnaire changes.
- Neutral ranked IDs for public trending/feed queries, followed by DB hydrate.
- Active forum prohibited phrases as immutable normalized strings.

## Deferred Cache Candidates

- Forum program-first feed currently queries PostgreSQL through its indexed
  keyset query. Do not cache a viewer response, reactions, or permission state.
  Reconsider a short-lived `programId -> post IDs` cache only after production
  metrics show this query is a sustained database hot path.

## Multi-Instance Trigger

Before adding a second Spring Boot instance, replace or redesign local state
that needs cross-instance consistency: rate limiting, OAuth state, local
credentials, and cache invalidation delivery. Redis is a future shared-state
option; Caffeine remains useful as L1 cache.
