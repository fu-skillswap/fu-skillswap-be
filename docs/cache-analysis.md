# Cache Analysis

## Scope

SkillSwap currently runs one Spring Boot instance on one VPS. Caffeine is an L1
process-local optimization only; PostgreSQL remains the source of truth. All
cache settings live under `application.cache` in `application.yaml` and bind to
`CacheProperties`.

## catalog

Purpose: Cache low-churn master data used by onboarding and mentor forms.

Configuration: `maximumSize=1000`, `ttl=24h` by default.

Read location: `CatalogService` and `AcademicService` through Spring
`@Cacheable`.

Write location: The cached values are generated from active help topics,
campuses, programs, and specializations.

Invalidation: `CatalogChangedEvent` is handled after commit by
`CatalogCacheInvalidationListener`, which clears the `catalog` cache. The
current codebase has no runtime admin writer for academic catalog data; future
writers must publish this event in their transaction.

Risk: A writer that changes catalog data without publishing the event can leave
the UI stale until TTL expiry.

Recommendation: Keep this cache. Do not cache student profile data in it.

## userBanStatus

Purpose: Avoid a user lookup on every ban-status guard evaluation.

Configuration: `maximumSize=10000`, `ttl=10m`.

Read location: `UserBanStatusAdapter`.

Write location: User status changes in moderation flows.

Invalidation: `UserBanStatusCacheInvalidationListener` invalidates the user key
after commit for status, ban, and deletion events.

Risk: Future user-status writers must publish the same events.

Recommendation: Keep this cache and preserve event-based invalidation.

## rateLimit

Purpose: Hold fixed-window counters for authentication, booking, forum, chat,
download URL, and telemetry throttles.

Configuration: four bounded caches. `security=10000`, `business=10000`,
`transfer=5000`, and `bestEffort=2000`; each call supplies its own window.

Read location: `InMemoryRateLimitService.check(scope, ...)`.

Write location: Every throttled request increments its bucket.

Invalidation: Dynamic Caffeine expiry removes the bucket when its fixed window
ends. Security rejects a new key when its cache is already at capacity instead
of evicting an existing security bucket and resetting its counter.

Risk: This is intentionally single-instance. A restart resets buckets, and a
future multi-instance deployment requires a shared limiter. Fixed windows allow
a bounded burst at a window boundary; the limiter is not a DDoS substitute.

Recommendation: Keep it for one VPS, configure limits at callers, and do not
use it as durable security state.

## googleOauthState

Purpose: Store one-time OAuth state and PKCE challenge during Google redirect.

Configuration: `maximumSize=10000`, `ttl=5m`.

Read location: `GoogleOAuthStateService.consume`.

Write location: `GoogleOAuthStateService.issue`.

Invalidation: Atomic removal on consume, otherwise TTL expiry.

Risk: Process restart makes pending login attempts fail safely and require a
fresh OAuth attempt.

Recommendation: Keep local for a single VPS; move to a shared store or signed
state design before adding backend replicas.

## matchingFeatures

Purpose: Cache immutable questionnaire-derived mentee matching features.

Configuration: `maximumSize=20000`, `ttl=10m`.

Read location: `CachedMenteeMatchingFeatureProvider.getLatestFeatures`.

Write location: Loader reads active/fallback questionnaire answers.

Invalidation: `MatchingFeatureCacheInvalidationListener` invalidates a user or
all entries after the source transaction commits.

Risk: Matching quality is stale until TTL only if a new writer does not emit an
invalidation event.

Recommendation: Keep it. Caffeine `get(key, loader)` coalesces concurrent
misses for the same mentee.

## mentorFunnelDedupe

Purpose: Drop duplicate client funnel telemetry before relationship queries and
telemetry persistence.

Configuration: `maximumSize=20000`, `ttl=10m`.

Read location: `MentorFunnelTelemetryService.recordClientEvent`.

Write location: First accepted event for user, type, and subject.

Invalidation: TTL only.

Risk: Telemetry is best effort. A malformed or currently invalid event consumes
its short dedupe key, which is acceptable because it has no business effect.

Recommendation: Keep it local and never let telemetry failures affect booking
or discovery responses.

## blogTrending

Purpose: Cache neutral ranked blog post IDs for anonymous and authenticated
segments.

Configuration: `maximumSize=3`, `ttl=10m`, invalidation debounce `45s`.

Read location: `BlogService.trending`.

Write location: Trending repository candidate query on cache miss.

Invalidation: `BlogTrendingRankingChangedListener` runs after commit. The first
ranking change in a debounce window clears the cache; later events retain the
new snapshot until the window ends.

Risk: Trending is intentionally eventually consistent for at most the debounce
window plus normal cache TTL behavior.

Recommendation: Cache IDs only, then hydrate and apply reader visibility from
the database.

## blogViewDedupe

Purpose: Prevent repeated view increments for the same post/viewer key.

Configuration: `maximumSize=100000`, `ttl=30m`.

Read location: `BlogService.recordView`.

Write location: First view in the TTL window.

Invalidation: TTL only.

Risk: This is anti-inflation best effort, not an audit record. Blog fingerprint
resolution must use `TrustedClientIpResolver` behind Cloudflare or Nginx.

Recommendation: Keep the bounded cache. It does not replace the request rate
limit or database authorization.

## blogCategories and blogTags

Purpose: Cache the public Blog taxonomy used repeatedly by editor and reader
navigation.

Configuration: Stored as two immutable DTO lists in the existing `catalog`
cache, using the catalog `maximumSize=1000`, `ttl=24h` defaults.

Read location: `BlogService.categories` and `BlogService.tags`.

Write location: Admin category and tag create, update, deactivation, and legacy
upsert actions.

Invalidation: `BlogTaxonomyChangedEvent` clears only the `blogCategories` and
`blogTags` keys after commit.

Risk: Never cache article cards, feeds, entitlement, or reader-specific
engagement state with this taxonomy cache.

Recommendation: Keep this small reuse of `catalog`; no separate cache manager
is warranted.

## forumProhibitedPhrase

Purpose: Cache immutable normalized active phrases for every forum post/comment
write check.

Configuration: `maximumSize=1`, `ttl=30m`.

Read location: `ForumProhibitedPhrasePolicy`.

Write location: Admin create/update/activate/deactivate mutations.

Invalidation: `ForumProhibitedPhraseChangedEvent` is published in the admin
transaction and invalidated after commit.

Risk: Cache holds only normalized strings, never JPA entities or rule metadata.

Recommendation: Keep it because forum writes are read-heavy against a
low-mutation rule set.

## localPrivateDownloadCredential

Purpose: Local/test-only private-resource credential lookup.

Configuration: `maximumSize=10000`, `ttl=10m`.

Read location: `LocalPrivateDownloadCredentialService`.

Write location: Local private download URL issuance.

Invalidation: TTL only.

Risk: Restart invalidates credentials, matching local bearer URL semantics.

Recommendation: Do not use it in production; production uses storage presigned
URLs.

## Observability

The catalog, user ban status, Google OAuth state, blog trending, blog view
dedupe, matching feature, prohibited phrase, funnel dedupe, and scoped
rate-limit caches enable Caffeine statistics. Actuator exposes metrics at
`/actuator/metrics`; relevant meters include `cache.gets`, `cache.size`, and
`cache.evictions`, with a `cache` tag identifying the cache name.

Rate-limit rejections also increment `rate_limit_blocked_total` with a `scope`
tag. Rejections are warning-logged at most once per scope per minute to avoid
turning an HTTP flood into a disk-log flood.
