# Modulith boundaries

## Ownership

Business modules are `admin`, `blog`, `booking`, `catalog`, `chat`, `course`,
`feedback`, `filestorage`, `forum`, `identity`, `mentor`, `notification`, and
`payment`. Technical adapters live under `infrastructure` and are not business
application modules.

Each module owns its controllers, services, repositories, entities, scheduled
jobs, and database tables. Consumers must not import another module's
`controller`, `service`, `repository`, or `domain` packages.

| Module | Owns | Web/job/event responsibility |
|---|---|---|
| admin | Backoffice moderation and case support | `/api/admin/**`, audit-intent consumer |
| blog | Publishing, taxonomy, reader engagement | Blog REST and publish events |
| booking | Scheduling, sessions, attendance, disputes | Booking REST and lifecycle jobs |
| catalog | Tags and learning goals | Catalog REST and catalog-change event |
| chat | Conversations and messages | Chat REST/realtime consumers |
| course | Curriculum and enrollment | Course REST and enrollment events |
| feedback | Session review | Feedback REST |
| filestorage | File metadata and upload intent | File-storage REST |
| forum | Posts, comments and reports | Forum REST and moderation events |
| identity | Users, authentication and academic profile | Auth REST and calendar consumer |
| mentor | Profiles, services, verification and availability policy | Mentor REST and availability events |
| notification | Durable notification/email outbox | Notification REST and delivery jobs |
| payment | Checkout, webhook, ledger, settlement and payout | Payment REST and reconciliation jobs |

`infrastructure.web.seo`, `infrastructure.telemetry`, and
`infrastructure.bootstrap.demo` are technical adapters. They do not own a
business table or expose a business-module API.

## Public contracts

Cross-module calls use a named `api` contract in the owning module. During the
filesystem migration those contracts remain physically under `port`, but every
such package is explicitly `@NamedInterface("api")`. Contracts return IDs or
immutable projection values; they must not expose JPA entities. For example,
course publishes enrollment events and chat owns the resulting group membership;
neither module references the other's service implementation.

## Dependency matrix

The current migration contracts are deliberately narrow. A cell in this table
means the consumer may use only the named contract, never the provider's
`service`, `repository`, `controller`, or JPA entity.

| Consumer | Provider contract | Purpose |
|---|---|---|
| blog | `booking.port.ContentEntitlementQuery` | Premium blog visibility |
| blog, feedback | `notification.port.NotificationCommandPort` | Immutable notification intent |
| booking | `identity.port.AcademicEligibilityQuery` | Booker academic eligibility |
| booking | `mentor.port.MentorBookingPolicyQuery` | Booking-window policy |
| course | `payment.port.CoursePaymentPort` | Course pricing, collection, refund and settlement |
| chat, forum | `mentor.port.MentorViolationCommandPort` | Confirmed moderation violation |
| mentor | `feedback.port.FeedbackQueryPort` | Public review projection |
| infrastructure SEO | `blog.port.BlogShareQueryPort`, `mentor.port.MentorShareQueryPort` | Social-share metadata |

`@ApplicationModule(allowedDependencies = ...)` is intentionally not enabled
yet. It is the final enforcement step only after `ApplicationModules.verify()`
has no cycles and no references to unexposed types; enabling it now would turn
the current red graph into a larger, less actionable error set rather than
enforce a truthful boundary.

## Dependency rules

1. Prefer owner-owned query ports for reads and command ports for mutations.
2. Publish events/outbox intents for asynchronous side effects such as email,
   notifications, calendar sync, and audit records.
3. Do not use `ObjectProvider`, reflection, or a service locator to avoid a
   business-module dependency.
4. New dependencies require an architecture test and an update to this file.

## Verification

`mvn test` runs the taxonomy and ArchUnit rules. The CI job also runs
`mvn -Pmodulith-architecture-audit test`; this is a required gate and calls
`ApplicationModules.verify()`, which rejects cycles and references to types
that are not public module contracts.

ArchUnit additionally forbids `ObjectProvider` in business modules and forbids
new `api` packages from leaking JPA or Spring implementation types. These rules
are non-frozen: a violation fails immediately and cannot be silently accepted
by updating a baseline store.
