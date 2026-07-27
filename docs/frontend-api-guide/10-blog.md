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
| GET | `/api/me/blog/library?serviceId=` | required | Premium articles unlocked for the selected booked service. |
| GET | `/api/me/blog/posts` | mentor | Mentor-owned authoring list. |
| POST | `/api/me/blog/posts` | eligible mentor | Create mentor draft. |
| GET/PUT | `/api/me/blog/posts/{postId}` | eligible mentor | Read/update own post. |
| POST | `/api/me/blog/posts/{postId}/publish` | eligible mentor | Publish with `expectedVersion`. |
| POST | `/api/me/blog/posts/{postId}/archive` | mentor owner | Archive with `expectedVersion`. |
| POST | `/api/me/blog/assets/upload-intents` | eligible mentor | Get public Blog image upload credential. |
| POST | `/api/me/blog/assets/{intentId}/confirm` | eligible mentor | Confirm uploaded image and receive `assetId` + public URL. |
| PUT/DELETE | `/api/blog/mentors/{mentorId}/follow` | required | Follow/unfollow mentor articles. |
| PUT/DELETE | `/api/blog/categories/{categoryId}/follow` | required | Follow/unfollow categories. |
| GET | `/api/me/blog/follows` | required | `{ categories, mentors }`. |
| PATCH | `/api/admin/blog/posts/{postId}/moderation` | admin | Moderate mentor metadata only. |
| DELETE | `/api/admin/blog/posts/{postId}` | admin | Soft delete with `expectedVersion`. |
| POST | `/api/admin/blog/posts/{postId}/restore` | admin | Restore as `ARCHIVED`. |

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
