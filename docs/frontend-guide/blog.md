# Frontend Integration Guide — Module Blog & Xuất Bản Bài Viết (Blog Platform)

> **Quy tắc ảnh & CDN**: Frontend sử dụng trực tiếp các URL hình ảnh nhận được từ backend trong `coverImageUrl`, `ogImageUrl` hoặc `author.avatarUrl`. Tuyệt đối không tự ghép nối prefix CDN, không tự trích xuất key/ID và không cần bất kỳ biến môi trường `.env` nào về CDN.

> **Chuẩn Envelope**: Tất cả phản hồi từ backend đều được bọc trong `ApiResponse<T>`. Vui lòng xem [identity.md](identity.md) để biết cấu trúc envelope, cơ chế refresh token và cách xử lý mã lỗi `429 Retry-After`.

---

## 1. Kiến Trúc Tổng Quan & Phân Quyền (Architecture & Security)

Module Blog phục vụ 2 mục đích chiến lược trên nền tảng SkillSwap:
1. **Dành cho Người đọc (Public Reader / Mentee)**: Trung tâm kiến thức học thuật SEO, giúp sinh viên tìm kiếm bài viết chất lượng, theo dõi tác giả/chủ đề yêu thích, và kết nối đặt lịch mentoring trực tiếp với tác giả.
2. **Dành cho Mentor (Tác giả viết bài)**: Công cụ xây dựng thương hiệu cá nhân (Personal Branding), tăng mức độ uy tín và chuyển đổi người đọc thành Mentee đặt lịch (`authorConversion`).

---

### 1.1 Ma Trận Phân Quyền (Access Control Matrix)

| Nhóm API / Thao tác | Khách vãng lai (Anonymous) | Role `MENTEE` | Role `MENTOR` (Đã duyệt) | Role `ADMIN` |
|---|:---:|:---:|:---:|:---:|
| Xem danh sách blog công khai, Trending, Featured |  |  |  |  |
| Xem chi tiết bài viết `PUBLIC` |  |  |  |  |
| Xem chi tiết bài viết `AUTHENTICATED` | ❌ 401 |  |  |  |
| Xem bài viết Premium `BOOKED_MEMBERS` | ❌ 401 |  (Nếu đã book dịch vụ) |  (Nếu là tác giả/đã book) |  |
| Ghi nhận lượt xem (Deduplicated View) |  (Kèm `sessionId`) |  |  |  |
| Thả Like / Lưu Bookmark bài viết | ❌ 401 |  |  |  |
| Theo dõi Danh mục / Mentor Author | ❌ 401 |  |  |  |
| Xem Bảng tin cá nhân hóa (Personalized Feed) | ❌ 401 |  |  | ❌ 403 |
| Soạn thảo, Cập nhật, Xuất bản bài viết (Mentor) | ❌ 401 | ❌ 403 |  | ❌ 403 |
| Upload ảnh bìa / ảnh nội dung bài viết | ❌ 401 | ❌ 403 |  |  |

---

### 1.2 Cấp Độ Hiển Thị Bài Viết (`BlogVisibility`)

| Giá trị Enum | Đối tượng được phép đọc nội dung chi tiết |
|---|---|
| `PUBLIC` | Mọi người dùng (kể cả khách chưa đăng nhập). Hỗ trợ tối đa cho Google Bot và SEO Indexing. |
| `AUTHENTICATED` | Bắt buộc phải đăng nhập tài khoản SkillSwap (`MENTEE`, `MENTOR`, `ADMIN`). |
| `BOOKED_MEMBERS` | **Bài viết độc quyền (Premium)**: Chỉ người dùng đã từng đặt lịch thành công (`CONFIRMED` hoặc `COMPLETED`) với ít nhất một dịch vụ trong danh sách `entitledServiceIds` của tác giả mới được đọc toàn văn. |

---

### 1.3 Cơ Chế Phân Trang Cursor (`CursorPageResponse<T>`)

Danh sách bài viết sử dụng **Cursor-Based Pagination** tối ưu cho Infinite Scroll:

```typescript
interface CursorPageResponse<T> {
  items: T[];                // Mảng bài viết của trang hiện tại
  nextCursor: string | null; // Token opaque để tải trang sau (null nếu đã hết dữ liệu)
  prevCursor: string | null;
  hasNext: boolean;          // true nếu còn bài viết ở trang sau
  hasPrev: boolean;
  limit: number;             // Số lượng bài trên mỗi trang (Mặc định: 20, Tối đa: 50)
}
```

> [!IMPORTANT]
> **Quy tắc Cursor**: `cursor` là chuỗi mã hóa an toàn (Opaque Token). Frontend **không tự giải mã, không chỉnh sửa** và chỉ truyền nguyên văn `nextCursor` vào query param `?cursor=${nextCursor}` khi bấm "Tải thêm bài viết" hoặc kích hoạt Infinite Scroll.

---

## 2. Danh Mục & Thẻ Phân Loại (Categories & Tags)

Trước khi hiển thị bộ lọc hoặc form viết bài, Frontend gọi các API này để lấy danh mục và thẻ tag hợp lệ.

### 2.1 Lấy danh sách Danh mục Blog (`GET /api/blog/categories`)
- **Endpoint**: `GET /api/blog/categories`
- **Quyền hạn**: Public
- **Response**: `ApiResponse<BlogCategoryResponse[]>`

```typescript
interface BlogCategoryResponse {
  id: string;          // UUID của danh mục
  code: string;        // Mã định danh (vd: "TECH", "CAREER", "ACADEMIC")
  name: string;        // Tên hiển thị (vd: "Công nghệ & Lập trình")
  slug: string;        // Đường dẫn thân thiện (vd: "cong-nghe-lap-trinh")
  description: string; // Mô tả ngắn
  active: boolean;
  displayOrder: number;
}
```

---

### 2.2 Lấy danh sách Thẻ Tag (`GET /api/blog/tags`)
- **Endpoint**: `GET /api/blog/tags`
- **Quyền hạn**: Public
- **Response**: `ApiResponse<BlogTagResponse[]>`

```typescript
interface BlogTagResponse {
  id: string;          // UUID của thẻ tag
  name: string;        // Tên tag (vd: "Spring Boot", "ReactJS", "Career Path")
  slug: string;        // Slug của tag (vd: "spring-boot")
  active: boolean;
}
```

---

## 3. Dành Cho Độc Giả: Đọc & Khám Phá Bài Viết (Reader APIs)

### 3.1 Cấu Trúc Dữ Liệu Thẻ Bài Viết (`BlogPostReaderCardResponse`)

```typescript
interface BlogPostReaderCardResponse {
  id: string;                                // UUID của bài viết
  title: string;                             // Tiêu đề bài viết
  slug: string;                              // URL slug chuẩn SEO (duy nhất)
  excerpt: string;                           // Đoạn trích dẫn ngắn mở đầu
  coverImageUrl: string | null;              // Ảnh bìa bài viết
  author: BlogAuthorResponse;                // Thông tin tác giả
  authorConversion: BlogAuthorConversionResponse; // Thẻ chuyển đổi đặt lịch với mentor
  categories: BlogCategoryResponse[];        // Danh sách danh mục gắn kèm
  tags: BlogTagResponse[];                    // Danh sách thẻ tag
  readingTimeMinutes: number;                // Thời gian đọc ước tính (phút)
  viewCount: number;                         // Tổng lượt xem
  likeCount: number;                         // Tổng lượt thích
  bookmarkCount: number;                     // Tổng lượt lưu bookmark
  likedByCurrentUser: boolean;               // true nếu user hiện tại đã bấm Like
  bookmarkedByCurrentUser: boolean;          // true nếu user hiện tại đã bấm Lưu
  featured: boolean;                         // true nếu là bài viết nổi bật
  publishedAt: string;                       // Thời điểm xuất bản (ISO format)
  lastPublishedAt: string;
  createdAt: string;
  updatedAt: string;
}

interface BlogAuthorResponse {
  id: string;                                // UUID của tác giả
  displayName: string;                       // Họ tên hiển thị
  avatarUrl: string | null;                  // Ảnh đại diện tác giả
  authorType: "MENTOR" | "PLATFORM";         // Loại tác giả
}

interface BlogAuthorConversionResponse {
  mentorUserId: string;                      // UUID tài khoản Mentor (dùng để chuyển sang booking)
  headline: string;                          // Tiêu đề chuyên môn của mentor
  verifiedMentor: boolean;                   // true nếu mentor đã được xác thực
  averageRating: number | null;              // Điểm đánh giá trung bình
  completedSessions: number;                 // Số buổi mentoring đã hoàn thành
  primaryCtaLabel: string;                   // Nhãn nút CTA (vd: "Đặt lịch Mentoring 1:1")
  profilePath: string;                       // Đường dẫn tới profile mentor (vd: "/mentors/123")
}
```

---

### 3.2 Danh Sách Bài Viết Công Khai (`GET /api/blog/posts`)
- **Endpoint**: `GET /api/blog/posts`
- **Quyền hạn**: Public (Đính kèm Token nếu đã đăng nhập để nhận đúng cờ `likedByCurrentUser` và `bookmarkedByCurrentUser`).
- **Query Parameters**:
  - `cursor` (string, optional): `nextCursor` từ response trước
  - `limit` (number, optional): Mặc định `20`, tối đa `50`
  - `categoryId` (UUID, optional): Lọc theo danh mục
  - `tagId` (UUID, optional): Lọc theo thẻ tag
  - `keyword` (string, optional): Tìm kiếm theo từ khóa trong tiêu đề, nội dung
- **Response**: `ApiResponse<CursorPageResponse<BlogPostReaderCardResponse>>`

---

### 3.3 Bài Viết Nổi Bật & Bài Viết Xu Hướng (Featured & Trending)

| Mục đích | Endpoint | Query Params | Response |
|---|---|---|---|
| Bài viết nổi bật (Featured) | `GET /api/blog/featured` | `limit` (default: 6) | `ApiResponse<BlogPostReaderCardResponse[]>` |
| Bài viết xu hướng (Trending) | `GET /api/blog/trending` | `limit` (default: 10) | `ApiResponse<BlogPostReaderCardResponse[]>` |
| Bài viết liên quan (Related) | `GET /api/blog/posts/{slug}/related` | `limit` (default: 6) | `ApiResponse<BlogPostReaderCardResponse[]>` |

> [!TIP]
> API Trending sử dụng thuật toán tính điểm tương tác (lượt đọc, like, bookmark gần đây) kết hợp bộ nhớ đệm cache hiệu năng cao. Sử dụng API này cho thanh Sidebar hoặc mục "Đọc nhiều nhất" trên trang chủ Blog.

---

### 3.4 Xem Chi Tiết Bài Viết Toàn Văn (`GET /api/blog/posts/{slug}`)
- **Endpoint**: `GET /api/blog/posts/{slug}`
- **Header**: `Authorization: Bearer <accessToken>` (Tùy chọn với `PUBLIC`, bắt buộc với `AUTHENTICATED` và `BOOKED_MEMBERS`).
- **Response**: `ApiResponse<BlogPostReaderDetailResponse>`

```typescript
interface BlogPostReaderDetailResponse extends BlogPostReaderCardResponse {
  contentMarkdown: string;  // Nội dung toàn văn bài viết ở định dạng Markdown
  ogImageUrl: string | null;// Ảnh OpenGraph dùng cho thẻ chia sẻ mạng xã hội
  seoTitle: string | null;  // Tiêu đề tối ưu SEO
  seoDescription: string | null; // Thẻ Meta Description
  canonicalUrl: string | null;   // URL chuẩn SEO
}
```

---

## 4. Tương Tác, Theo Dõi & Telemetry Chuyển Đổi (Engagement & Telemetry)

### 4.1 Thả / Bỏ Like Bài Viết (Idempotent Reaction)
- **Thả Like**: `PUT /api/blog/posts/{postId}/like`
- **Bỏ Like**: `DELETE /api/blog/posts/{postId}/like`
- **Header**: `Authorization: Bearer <accessToken>`
- **Response Payload (`BlogEngagementMutationResponse`)**:

```typescript
interface BlogEngagementMutationResponse {
  postId: string;
  likedByCurrentUser: boolean;      // Trạng thái like mới
  bookmarkedByCurrentUser: boolean; // Trạng thái bookmark mới
  likeCount: number;                // Tổng số like cập nhật
  bookmarkCount: number;            // Tổng số bookmark cập nhật
}
```

---

### 4.2 Lưu / Bỏ Lưu Bookmark Bài Viết
- **Lưu Bookmark**: `PUT /api/blog/posts/{postId}/bookmark`
- **Bỏ Lưu**: `DELETE /api/blog/posts/{postId}/bookmark`
- **Header**: `Authorization: Bearer <accessToken>`
- **Danh sách bài viết đã bookmark của tôi**: `GET /api/me/blog/bookmarks?cursor=...&limit=20`

---

### 4.3 Theo Dõi Danh Mục & Mentor Tác Giả (Follow System)

| Thao tác | Endpoint | Method |
|---|---|---|
| Theo dõi Danh mục | `/api/blog/categories/{categoryId}/follow` | `PUT` (Follow) / `DELETE` (Unfollow) |
| Theo dõi Mentor | `/api/blog/mentors/{mentorId}/follow` | `PUT` (Follow) / `DELETE` (Unfollow) |
| Danh sách đang theo dõi của tôi | `/api/me/blog/follows` | `GET` |
| Bảng tin bài viết cá nhân hóa | `/api/me/blog/feed?cursor=...` | `GET` |

> [!NOTE]
> Bảng tin `/api/me/blog/feed` sẽ tự động ưu tiên tổng hợp các bài viết mới từ các Danh mục và Mentor mà người dùng đã nhấn Theo dõi.

---

### 4.4 Ghi Nhận Lượt Xem & Đo Lường Chuyển Đổi (Telemetry Tracking)

Để đảm bảo số liệu chính xác và không bị spam ảo, Frontend cần gọi các endpoint telemetry sau:

#### A. Ghi nhận lượt xem bài viết (`POST /api/blog/posts/{postId}/view`)
- **Endpoint**: `POST /api/blog/posts/{postId}/view`
- **Thời điểm gọi**: Gọi sau khi người dùng ở lại trang bài viết tối thiểu 5-10 giây.
- **Request Body**:
```typescript
interface BlogViewRequest {
  sessionId: string; // Chuỗi UUID tạo ngẫu nhiên per browser tab/session (vd: crypto.randomUUID())
}
```
- **Hạn mức**: Rate limit tối đa **120 lượt xem / 1 phút / IP**. Backend tự động chống trùng lặp lượt xem ảo từ cùng một session.

#### B. Ghi nhận sự kiện Click nút Đặt Lịch với Mentor (`POST /api/blog/posts/{postId}/author-cta-click`)
- **Endpoint**: `POST /api/blog/posts/{postId}/author-cta-click`
- **Thời điểm gọi**: Khi người đọc bấm vào nút "Đặt lịch ngay", "Xem hồ sơ Mentor" hoặc banner dịch vụ của tác giả đặt trong bài viết.
- **Request Body**: `{ "sessionId": string, "ctaType": "AUTHOR_PROFILE" | "BOOKING_DIRECT" }`

---

## 5. Dành Cho Mentor: Quản Lý & Xuất Bản Bài Viết (Authoring APIs)

Tất cả các endpoint trong mục này yêu cầu Bearer Token với Role `MENTOR` (đã hoàn thành xác thực hồ sơ).

### 5.1 Luồng Tải Lên Hình Ảnh Bài Viết (Image Upload Flow)

Tương tự cơ chế upload an toàn của nền tảng, ảnh bài viết được tải lên qua **Upload Intent 2 bước**:

```text
1. Mentor chọn file ảnh (PNG/JPEG/WebP <= 10MB)
 ➔ FE gọi POST /api/me/blog/assets/upload-intents
 ➔ Nhận uploadUrl và intentId
2. Browser gửi HTTP PUT file trực tiếp lên uploadUrl
3. FE gọi POST /api/me/blog/assets/{intentId}/confirm
 ➔ Nhận PublicAssetResponse (gồm assetId và publicUrl)
4. Sử dụng assetId cho coverAssetId / ogAssetId hoặc chèn publicUrl vào Markdown ![ảnh](publicUrl)
```

```typescript
// Bước 1: Khởi tạo intent
POST /api/me/blog/assets/upload-intents
Body: { "filename": "cover.png", "contentType": "image/png", "sizeBytes": 1048576 }
Response: { "uploadIntentId": string, "uploadUrl": string, "requiredHeaders": Record<string, string> }

// Bước 3: Xác nhận hoàn tất upload
POST /api/me/blog/assets/{intentId}/confirm
Response: { "id": string, "publicUrl": string, "contentType": string, "sizeBytes": number }
```

> [!WARNING]
> **Quy tắc bảo vệ bản quyền ảnh**: Khi lưu bài viết, backend kiểm tra toàn bộ các link ảnh `![alt](url)` có trong nội dung Markdown. Tất cả ảnh chèn vào bài viết bắt buộc phải được upload qua tài khoản của chính mentor đó; không chấp nhận chèn link ảnh tùy tiện từ nguồn không xác thực.

---

### 5.2 Quản Lý Bài Viết Của Tôi (Draft & CRUD)

| Thao tác | Endpoint | Request Body |
|---|---|---|
| Lấy danh sách bài viết của tôi | `GET /api/me/blog/posts` | Không có |
| Xem chi tiết bài viết của tôi | `GET /api/me/blog/posts/{postId}` | Không có |
| Tạo bản nháp mới (Create Draft) | `POST /api/me/blog/posts` | `MentorBlogPostCreateRequest` |
| Cập nhật bản nháp (Update Draft) | `PUT /api/me/blog/posts/{postId}` | `MentorBlogPostUpdateRequest` |
| Xuất bản bài viết (Publish) | `POST /api/me/blog/posts/{postId}/publish` | `BlogExpectedVersionRequest` |
| Lưu trữ bài viết (Archive) | `POST /api/me/blog/posts/{postId}/archive` | `BlogExpectedVersionRequest` |

```typescript
interface MentorBlogPostCreateRequest {
  title: string;                        // Bắt buộc. Tối đa 220 ký tự
  excerpt?: string;                     // Đoạn trích tóm tắt
  contentMarkdown?: string;             // Nội dung định dạng Markdown
  coverAssetId?: string;                // UUID asset ảnh bìa (lấy từ bước upload)
  ogAssetId?: string;                   // UUID asset ảnh OpenGraph chia sẻ
  visibility?: "PUBLIC" | "AUTHENTICATED" | "BOOKED_MEMBERS"; // Mặc định PUBLIC
  categoryIds?: string[];               // Danh sách UUID danh mục
  tagIds?: string[];                    // Danh sách UUID thẻ tag
  entitledServiceIds?: string[];        // Bắt buộc nếu visibility = BOOKED_MEMBERS
}

interface MentorBlogPostUpdateRequest extends MentorBlogPostCreateRequest {
  expectedVersion: number;              // Optimistic locking version (bắt buộc)
}

interface BlogExpectedVersionRequest {
  expectedVersion: number;              // Optimistic locking version (bắt buộc)
}
```

> [!IMPORTANT]
> **Quy tắc Xuất bản & Slug Khóa cứng**:
> 1. Khi tạo bài viết, backend tự động sinh `slug` thân thiện từ tiêu đề (ví dụ: `kinh-nghiem-intern-backend-fpt`).
> 2. Sau khi bấm **Publish**, `slug` sẽ được **khóa cứng vĩnh viễn (`slugLocked = true`)** để bảo toàn link SEO cho Google và các liên kết đã chia sẻ ra ngoài mạng xã hội.
> 3. Cập nhật hoặc xuất bản bài viết bắt buộc phải gửi kèm `expectedVersion` để tránh tình trạng ghi đè nội dung khi mở cùng lúc nhiều tab.

---

## 6. Bảng Mã Lỗi Chi Tiết & Hướng Dẫn Xử Lý (Error Codes Reference)

| HTTP Status | Error Code | Ý nghĩa & Hướng xử lý cho Frontend |
|---|---|---|
| `400` | `VAL_3001` | Tiêu đề quá 220 ký tự, thiếu nội dung khi xuất bản, hoặc bài viết `BOOKED_MEMBERS` nhưng chưa chọn dịch vụ áp dụng. |
| `400` | `BAD_REQUEST` | Nội dung Markdown chứa link ảnh không thuộc quyền sở hữu của mentor. |
| `401` | `AUTH_1001` | Chưa đăng nhập khi thực hiện like, bookmark, follow hoặc viết bài. |
| `403` | `ACCESS_DENIED` | Tài khoản chưa được duyệt làm Mentor hoặc không phải tác giả của bài viết. |
| `404` | `NOT_FOUND` | Bài viết không tồn tại, đã bị tác giả xóa hoặc thu hồi về trạng thái lưu trữ. |
| `409` | `BLOG_POST_VERSION_CONFLICT` | Xung đột phiên bản khi chỉnh sửa bài viết. Tải lại dữ liệu mới nhất trước khi cho mentor sửa tiếp. |
| `429` | `SYS_0010` | Thao tác ghi nhận lượt xem hoặc reaction quá nhanh. Đọc `retryAfterSeconds` để đếm ngược. |

---

## 7. Code Mẫu Thực Chiến Next.js App Router (Production-Ready)

### 7.1 Trang Chi Tiết Bài Viết & Tự Động Ghi Nhận Lượt Xem (`app/blog/[slug]/page.tsx`)

```tsx
'use client';

import React, { useState, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { apiClient } from '@/lib/api-client';

export default function BlogPostDetailPage() {
  const { slug } = useParams();
  const router = useRouter();
  const [post, setPost] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  // 1. Tải chi tiết bài viết
  useEffect(() => {
    async function loadDetail() {
      try {
        const res: any = await apiClient.get(`/api/blog/posts/${slug}`);
        setPost(res.data?.data || res.data);
      } catch (err: any) {
        console.error('Lỗi khi tải bài viết:', err);
      } finally {
        setLoading(false);
      }
    }
    if (slug) loadDetail();
  }, [slug]);

  // 2. Ghi nhận lượt xem an toàn sau 5 giây đọc bài
  useEffect(() => {
    if (!post?.id) return;

    const timer = setTimeout(async () => {
      try {
        await apiClient.post(`/api/blog/posts/${post.id}/view`, {
          sessionId: crypto.randomUUID(),
        });
      } catch (err) {
        // Lỗi telemetry không ảnh hưởng trải nghiệm đọc
      }
    }, 5000);

    return () => clearTimeout(timer);
  }, [post?.id]);

  // 3. Xử lý Thả / Bỏ Like
  const handleToggleLike = async () => {
    if (!post) return;
    const currentLiked = post.likedByCurrentUser;

    // Optimistic UI Update
    setPost((prev: any) => ({
      ...prev,
      likedByCurrentUser: !currentLiked,
      likeCount: currentLiked ? prev.likeCount - 1 : prev.likeCount + 1,
    }));

    try {
      if (currentLiked) {
        await apiClient.delete(`/api/blog/posts/${post.id}/like`);
      } else {
        await apiClient.put(`/api/blog/posts/${post.id}/like`);
      }
    } catch (err) {
      // Revert lại nếu lỗi
      setPost((prev: any) => ({
        ...prev,
        likedByCurrentUser: currentLiked,
        likeCount: currentLiked ? prev.likeCount : prev.likeCount,
      }));
    }
  };

  // 4. Xử lý Click Đặt lịch với Mentor (Tracking Conversion)
  const handleMentorBookingClick = async () => {
    if (!post?.authorConversion?.mentorUserId) return;

    try {
      await apiClient.post(`/api/blog/posts/${post.id}/author-cta-click`, {
        sessionId: crypto.randomUUID(),
        ctaType: 'BOOKING_DIRECT',
      });
    } catch (e) {
      // Ignore
    }

    router.push(`/mentors/${post.authorConversion.mentorUserId}`);
  };

  if (loading) return <div className="p-8 text-center">Đang tải bài viết...</div>;
  if (!post) return <div className="p-8 text-center">Không tìm thấy bài viết</div>;

  return (
    <article className="max-w-4xl mx-auto px-4 py-8 space-y-6">
      {/* Tiêu đề & Metadata */}
      <div className="space-y-3">
        <div className="flex gap-2">
          {post.categories?.map((cat: any) => (
            <span key={cat.id} className="text-xs bg-blue-50 text-blue-600 px-2.5 py-1 rounded-full font-medium">
              {cat.name}
            </span>
          ))}
        </div>
        <h1 className="text-3xl font-extrabold text-gray-900">{post.title}</h1>
        <div className="flex items-center gap-3 text-sm text-gray-500">
          <span>Tác giả: <strong>{post.author.displayName}</strong></span>
          <span>•</span>
          <span>{post.readingTimeMinutes} phút đọc</span>
          <span>•</span>
          <span>{post.viewCount} lượt xem</span>
        </div>
      </div>

      {/* Ảnh bìa */}
      {post.coverImageUrl && (
        <img
          src={post.coverImageUrl}
          alt={post.title}
          className="w-full h-80 object-cover rounded-2xl border"
        />
      )}

      {/* Nội dung Markdown */}
      <div className="prose max-w-none text-gray-800 leading-relaxed whitespace-pre-line">
        {post.contentMarkdown}
      </div>

      {/* Like & Tương tác */}
      <div className="flex items-center gap-4 py-4 border-t border-b">
        <button
          onClick={handleToggleLike}
          className={`flex items-center gap-2 px-4 py-2 rounded-lg font-medium transition ${
            post.likedByCurrentUser ? 'bg-red-50 text-red-600' : 'bg-gray-100 hover:bg-gray-200 text-gray-700'
          }`}
        >
          {post.likedByCurrentUser ? '❤️ Đã thích' : '🤍 Thích'} ({post.likeCount})
        </button>
      </div>

      {/* Card Chuyển đổi Mentor Booking (Conversion Card) */}
      {post.authorConversion?.mentorUserId && (
        <aside className="bg-gradient-to-r from-blue-50 to-indigo-50 border border-blue-100 rounded-2xl p-6 flex flex-col md:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-4">
            <img
              src={post.author.avatarUrl || '/default-avatar.png'}
              alt={post.author.displayName}
              className="w-14 h-14 rounded-full object-cover border-2 border-white shadow"
            />
            <div>
              <h4 className="font-bold text-gray-900 text-lg">{post.author.displayName}</h4>
              <p className="text-sm text-gray-600">{post.authorConversion.headline}</p>
              {post.authorConversion.averageRating && (
                <p className="text-xs text-amber-600 font-semibold mt-1">
                  ⭐ {post.authorConversion.averageRating} ({post.authorConversion.completedSessions} buổi học hoàn thành)
                </p>
              )}
            </div>
          </div>

          <button
            onClick={handleMentorBookingClick}
            className="px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl shadow-sm transition whitespace-nowrap"
          >
            {post.authorConversion.primaryCtaLabel || 'Đặt lịch với Mentor'}
          </button>
        </aside>
      )}
    </article>
  );
}
```
