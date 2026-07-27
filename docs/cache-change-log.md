# Caffeine Optimization Change Log

## 2026-07-24

- Added `CacheProperties` as the single source for local Caffeine size and TTL
  settings. Removed the unused `spring.cache.caffeine.spec` setting.
- Added Caffeine statistics and Actuator metrics exposure for catalog, blog,
  matching, and rate-limit caches.
- Replaced per-view blog trending invalidation with after-commit debounced
  invalidation. A burst of views now clears the ranking cache once per debounce
  window instead of once per view.
- Replaced Blog direct remote-address fingerprinting with
  `TrustedClientIpResolver`, matching authentication behavior behind trusted
  proxies.
- Replaced matching `getIfPresent`/load/put with Caffeine atomic `get` loading
  to avoid same-key cache stampedes.
- Moved mentor funnel dedupe before relationship queries. Telemetry remains
  best effort and non-blocking.
- Added an immutable active prohibited-phrase cache and after-commit
  invalidation on admin rule mutations.
- Replaced the unused manual catalog eviction method with an after-commit
  `CatalogChangedEvent` listener for future catalog writers.
- Cached public Blog category and tag DTO lists in the existing catalog cache.
  Admin taxonomy mutations now evict only those two keys after commit.

## 2026-07-27

- Split the former shared rate-limit cache into bounded `security`, `business`,
  `transfer`, and `bestEffort` Caffeine caches. This prevents high-cardinality
  telemetry or download traffic from evicting authentication and booking
  buckets on the single VPS.
- Made a full security rate-limit cache fail closed for new keys rather than
  evicting an active bucket and resetting its counter.
- Replaced per-rejection warning logs with one warning per scope per minute and
  added the `rate_limit_blocked_total{scope=...}` Micrometer counter.
- Bound Micrometer cache metrics for user-ban status, Google OAuth state,
  forum prohibited phrases, and mentor funnel dedupe caches.
