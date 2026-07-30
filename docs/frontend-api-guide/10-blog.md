# Blog API Contract

## Purpose
Blog is a mentor-authority and conversion feature, not a general CMS:

```text
Mentor article -> trust -> booking -> premium library
Platform article -> SEO / announcement / education
```

`BlogPostStatus` is editorial state only: `DRAFT`, `PUBLISHED`, `ARCHIVED`. Soft deletion is represented separately by `deleted` and `deletedAt` in admin responses.

## Visibility
| Value | Who can read | Collection behavior |
| --- | --- | --- |
| `PUBLIC` | everyone | public reader surfaces |
| `AUTHENTICATED` | any valid logged-in user | hidden from anonymous readers |
| `BOOKED_MEMBERS` | user entitled to an attached Mentor Service | excluded from search, feed, trending and related; shown only in Library |

Reader detail and mutation endpoints return a generic `404` for missing, deleted, unpublished, archived, suspended-author or inaccessible content. FE must not infer the reason.

## API inventory
| Method | Endpoint | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/api/blog/posts` | optional | Reader list; category/tag/keyword filters only. No audience filter. |
| GET | `/api/blog/posts/{slug}` | optional | Reader detail if current viewer is eligible. |
| GET | `/api/blog/featured` | optional | Editorial placement; `featured` is not ranking. |
| GET | `/api/blog/trending` | optional | `PUBLIC` for anonymous; `PUBLIC + AUTHENTICATED` for logged-in users. |
| GET | `/api/blog/posts/{slug}/related` | optional | Related reader content; never premium. |
| GET | `/api/blog/posts/{slug}/recommendations` | optional | Deprecated alias of `/related`; do not add new client usage. |
| GET | `/api/me/blog/library?serviceId=` | required | Premium articles unlocked for the selected booked service. |
| GET | `/api/me/blog/bookmarks` | required | Cursor-paginated articles bookmarked by the current user. |
| GET | `/api/me/blog/feed` | required | Cursor-paginated followed mentor/category posts, then eligible latest fallback. |
| GET | `/api/me/blog/posts` | mentor | Mentor-owned authoring list. |
| POST | `/api/me/blog/posts` | eligible mentor | Create mentor draft. |
| GET/PUT | `/api/me/blog/posts/{postId}` | eligible mentor | Read/update own post. |
| POST | `/api/me/blog/posts/{postId}/publish` | eligible mentor | Publish with `expectedVersion`. |
| POST | `/api/me/blog/posts/{postId}/archive` | mentor owner | Archive with `expectedVersion`. |
| POST | `/api/me/blog/assets/upload-intents` | eligible mentor | Get public Blog image upload credential. |
| POST | `/api/me/blog/assets/{intentId}/confirm` | eligible mentor | Confirm uploaded image and receive `assetId` + public URL. |
| POST | `/api/blog/posts/{postId}/view` | optional | Record a rate-limited, deduplicated reader view; no UI state depends on its result. |
| POST | `/api/blog/posts/{postId}/author-cta-click` | optional | Best-effort author CTA telemetry. |
| POST | `/api/blog/posts/{postId}/booking-started` | optional | Best-effort booking-started telemetry. |
| POST | `/api/blog/posts/{postId}/notification-click` | optional | Best-effort notification-click telemetry. |
| POST | `/api/blog/posts/{postId}/recommendation-click` | optional | Legacy telemetry only; do not build a recommendation UI around it. |
| PUT/DELETE | `/api/blog/posts/{postId}/like` | required | Idempotently like/unlike an eligible post. |
| PUT/DELETE | `/api/blog/posts/{postId}/bookmark` | required | Idempotently bookmark/unbookmark an eligible post. |
| PUT/DELETE | `/api/blog/mentors/{mentorId}/follow` | required | Follow/unfollow mentor articles. |
| PUT/DELETE | `/api/blog/categories/{categoryId}/follow` | required | Follow/unfollow categories. |
| GET | `/api/me/blog/follows` | required | `{ categories, mentors }`. |
| GET/POST | `/api/admin/blog/posts` | admin | List admin-visible posts or create a platform draft. |
| GET/PUT | `/api/admin/blog/posts/{postId}` | admin | Load or update an admin-visible post. |
| PATCH | `/api/admin/blog/posts/{postId}/moderation` | admin | Moderate mentor metadata only. |
| POST | `/api/admin/blog/posts/{postId}/publish`, `/archive` | admin | Publish/archive an admin-managed post. |
| POST | `/api/admin/blog/posts/{postId}/feature`, `/unfeature` | admin | Editorial placement only; never changes ranking formulas. |
| DELETE | `/api/admin/blog/posts/{postId}` | admin | Soft delete with `expectedVersion`. |
| POST | `/api/admin/blog/posts/{postId}/restore` | admin | Restore as `ARCHIVED`. |
| GET/PUT | `/api/admin/blog/categories` | admin | List or upsert blog categories. |
| GET/POST | `/api/admin/blog/tags` | admin | List or create blog tags. |
| PUT | `/api/admin/blog/tags/{tagId}` | admin | Update or deactivate a blog tag. |
| PUT | `/api/admin/blog/tags` | admin | Deprecated legacy tag upsert; new FE must use the ID path. |
| POST | `/api/admin/blog/assets/upload-intents`, `/api/admin/blog/assets/{intentId}/confirm` | admin | Public Blog-asset flow for platform articles. |

## Mentor authoring flow
1. Mentor must be `ACTIVE` and verified to create, edit, upload assets or publish.
2. FE optionally uploads cover/OG images with the asset intent flow.
3. FE sends `coverAssetId`/`ogAssetId`; it never sends an object key.
4. Backend stores canonical `contentMarkdown`. The editor must sanitize pasted HTML before converting it to Markdown.
5. Every update/publish/archive uses server `version`. Do not increment version locally.

```json
{
  "title": "Spring Boot Interview Guide",
  "excerpt": "A practical preparation guide.",
  "contentMarkdown": "# Start here\n...",
  "coverAssetId": "018f3abf-0a22-7152-9748-6cf000c47b6e",
  "visibility": "BOOKED_MEMBERS",
  "categoryIds": ["018f3abf-0a22-7152-9748-6cf000c47b6f"],
  "tagIds": [],
  "entitledServiceIds": ["018f3abf-0a22-7152-9748-6cf000c47000"]
}
```

`BOOKED_MEMBERS` must have at least one `entitledServiceId` before publish. Mentor service deactivation does not remove historical reader entitlement.

## Reader response and UI mapping
Reader cards expose `id`, `title`, `slug`, `excerpt`, resolved image URL, `author`, optional mentor `authorConversion`, taxonomy, engagement counts/state, `featured` and timestamps. Detail adds canonical Markdown, OG image and effective SEO fields.

`author` contains only `id`, `displayName`, `avatarUrl`, `authorType`. Do not depend on raw role arrays. `authorConversion` is the booking CTA data for mentor articles.

Use `featured` only for a badge/editorial block. Do not derive it from feature order or expiry, and do not treat it as trending priority.

## Follow, feed and notifications
- Maximum follows: 20 mentors and 20 categories. `BLOG_FOLLOW_LIMIT_REACHED` requires removing an existing follow before retrying.
- Feed prioritizes followed mentor/category posts, then latest eligible fallback.
- `PUBLIC` and `AUTHENTICATED` publications notify eligible mentor/category followers.
- `BOOKED_MEMBERS` publications notify only currently entitled users.
- Notification dedupe is server-owned; never create client notifications from feed results.

## Reader engagement and telemetry
- Like, bookmark and follow mutations are idempotent. Render the canonical `BlogEngagementMutationResponse` or `BlogFollowResponse` returned by the server rather than incrementing counts locally.
- `/api/me/blog/bookmarks`, `/api/me/blog/feed` and reader list use opaque cursors. Pass `nextCursor` back unchanged; do not derive page numbers or decode it.
- Reader telemetry endpoints (`view`, CTA click, booking-started and notification click) are best effort. Do not block navigation, booking or a rendered article if one fails.
- The deprecated `/recommendations` alias returns a `Deprecation: true` header and a `Link` header pointing to `/related`. New FE code must call `/related` directly.

## Admin boundary
Admin can fully write only `PLATFORM` articles. Mentor article moderation may change visibility, taxonomy, SEO overrides, feature state or archive/delete/restore; it cannot change mentor title, Markdown, author or premium service entitlements.

Admin responses contain `deleted`/`deletedAt`; `DELETED` is not a blog status. Deleted posts default out of the admin list and are available through `?deleted=true` only.

## FE anti-patterns
- Do not use removed `audienceType`, `MEMBERS_ONLY`, `MENTOR_ONLY` or tag-follow APIs.
- Do not put premium articles into generic reader discovery UI.
- Do not store raw HTML/editor JSON; submit Markdown only.
- Do not send storage keys or direct arbitrary image URLs in mentor post writes.
- On `BLOG_POST_VERSION_CONFLICT`, preserve local editor text, refetch detail and require explicit resubmission.

## Future RAG rule
RAG metadata must retain `postId`, author identity/type, visibility, entitled service IDs, status, deletion state, taxonomy and timestamps.

```text
Retrieval != authorization.
```

Authorization must filter inaccessible chunks before reranking or generation. Premium chunks must never reach an ineligible user or model response.
