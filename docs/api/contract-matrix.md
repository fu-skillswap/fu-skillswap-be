# API contract matrix

This matrix is the review baseline for the public HTTP contracts in Tasks 13–17.
Requests contain client intent only; server-owned state is derived from the
authenticated actor and owner ports. Responses are split by audience (reader,
participant, admin, and realtime) and are not reused across those boundaries.

## Booking

| Endpoint | Actor | Client intent | Server authority / stored state | Consumer | Decision |
|---|---|---|---|---|---|
| `POST /api/bookings/quote` | authenticated mentee | `slotId`, `serviceId`, learning-goal fields, `startAt` | mentor capability, policy/version, duration, price and end time | booking UI | Keep; reject end-time and price input |
| `POST /api/bookings` | authenticated mentee | slot/service, `startAt`, learning-goal fields | actor IDs, service/policy snapshot, quote version, lifecycle state | booking/payment UI | Keep intent-only shape |
| booking reschedule | participant | selected slot/start intent | availability, timezone and end time derived by booking | participant UI | Keep server-derived time/state |

`legacySelectedEndTime`, LocalDateTime bridge constructors, and deprecated
selected-time accessors are removed. JSON/OpenAPI snapshots must assert they are
not accepted or emitted.

## Payment

| Endpoint | Actor | Client intent | Server authority / stored state | Consumer | Decision |
|---|---|---|---|---|---|
| checkout preview | participant | booking reference, coupon/credit intent | charge snapshot, payable amount, campaign and discount ordering | checkout UI | Never accept price/order/status fields |
| checkout session | participant | booking reference and discount intent | provider order code, expiry, payment URL and settlement state | checkout UI | Provider data is response-only |
| payment webhook | provider adapter | signed provider payload | signature verification, provider-neutral event, idempotency | payment/booking listeners | Keep as inbound adapter DTO only |

## Chat and notification

| Endpoint | Actor | Client intent | Server authority / stored state | Consumer | Decision |
|---|---|---|---|---|---|
| send message | conversation participant | content, `clientMessageId`, attachment IDs | path conversation/actor, sequence, cursor and entitlement | chat UI/realtime | Reject duplicated body conversation ID |
| mark read | conversation participant | read sequence/cursor | participant membership and monotonic cursor | chat UI | Keep cursor server-owned |
| notification REST feed | authenticated user | filters/cursor | recipient ID, unread/read state | notification UI | No realtime envelope fields |
| realtime notification | authenticated connection | none | event ID, recipient and event payload | websocket client | Separate envelope from REST response |

## Forum

| Endpoint | Actor | Client intent | Server authority / stored state | Consumer | Decision |
|---|---|---|---|---|---|
| create post/comment | authenticated user | topic/content, confirmed asset IDs | author ID, moderation status, counts and timestamps | forum UI | Never accept author/status/counts/raw storage URLs |
| reaction toggle | authenticated user | reaction intent | actor, uniqueness and resulting counts | forum UI | Keep idempotent uniqueness |
| report | authenticated user | target, reason and description | reporter, throttle, moderation status and review actor | forum/admin | Keep moderation fields server-owned |
| public feed | anonymous/authenticated reader | cursor/filter | author projection, counts and reader reaction state | forum UI | Do not leak report/moderation counts |

## Blog

| Endpoint | Actor | Client intent | Server authority / stored state | Consumer | Decision |
|---|---|---|---|---|---|
| create/update article | mentor/admin | title, content, category/tag and asset IDs | author, publish status, visibility and timestamps | blog editor | Keep author/publish state server-owned |
| reader detail/feed | reader | cursor and filters | entitlement, author projection and engagement state | blog UI/SEO | No CMS analytics or internal fields |
| premium library | entitled reader | service reference and cursor | entitlement checked through `ContentEntitlementQuery` | blog UI | Reject client pricing/entitlement claims |

## Required verification

Each changed request/response requires JSON deserialize/serialize, authorization,
and OpenAPI snapshot coverage. Every remaining field must have a named
consumer and authority owner; compatibility aliases require an explicit
deprecation date and migration note.
