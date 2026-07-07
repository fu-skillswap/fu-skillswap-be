# API Contract Diff Cho FE

So sánh từ commit `2328efac2c5b95911915c68588684012b3c3e7ec` đến commit `12f558f270fb0c5b74d26da9341ca29670f3ee95`.

Mục tiêu file này: giúp FE biết endpoint nào mới, endpoint nào đổi request body, endpoint nào đổi response body, field nào bỏ, field nào thêm.

## Quy Ước Chung

Tất cả response thành công vẫn nằm trong wrapper:

```json
{
  "timestamp": "2026-07-05 09:00:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": {}
}
```

Endpoint tạo mới thường trả:

```json
{
  "timestamp": "2026-07-05 09:00:00",
  "status": 201,
  "code": "CREATED_0201",
  "message": "Tạo mới thành công",
  "data": {}
}
```

## Tổng Quan FE Cần Sửa

Các API mới cần tích hợp:

```text
GET    /api/me/matching-profile
GET    /api/me/matching-profile/questionnaire
PUT    /api/me/matching-profile
GET    /api/admin/mentoring-questionnaire/versions
GET    /api/admin/mentoring-questionnaire/versions/{versionId}
POST   /api/admin/mentoring-questionnaire/versions
POST   /api/admin/mentoring-questionnaire/activate
GET    /api/admin/mentoring-questionnaire/active
GET    /api/catalog/mentor-profile-options
GET    /api/me/mentor-projects
POST   /api/me/mentor-projects
PUT    /api/me/mentor-projects/{projectId}
PUT    /api/me/mentor-projects/{projectId}/picture
DELETE /api/me/mentor-projects/{projectId}
GET    /api/me/mentor-achievements
POST   /api/me/mentor-achievements
PUT    /api/me/mentor-achievements/{achievementId}
DELETE /api/me/mentor-achievements/{achievementId}
```

Các API cũ đổi contract:

```text
GET  /api/me/onboarding-status
GET  /api/me/mentor-profile
PUT  /api/me/mentor-profile
POST /api/me/mentor-verification/documents
GET  /api/mentors
GET  /api/mentors/recommendations
GET  /api/mentors/{mentorUserId}
GET  /api/mentors/{mentorUserId}/availability
GET  /api/mentors/{mentorUserId}/availability-slots
GET  /api/catalog/help-topics
```

Field cũ FE không nên gửi hoặc đọc nữa:

```text
supportingSubjects
teachingMode
sessionDuration
defaultSessionDuration
linkedinUrl
```

Field mới FE cần support:

```text
subjectResults
foundationSupportLevel
outputReviewSupportLevel
directionSupportLevel
featuredProjects
achievements
mentoringNeedsCompleted
```

## Phân Công Cho 2 FE

Nguyên tắc chia việc:

```text
1. Mỗi FE nhận một nhóm màn hình/API có luồng dữ liệu liền nhau.
2. Hạn chế một người vừa sửa mentee discovery vừa sửa mentor verification vì model dữ liệu khác nhau.
3. Không liệt kê các thay đổi backend-only không cần FE sửa.
```

### FE 1 - Vinh: Mentee Flow + Discovery

Scope chính:

```text
Mentee onboarding needs questionnaire
Mentee discovery/recommendation
Mentor public card/detail
Help topics hiển thị ở filter discovery
Availability price display nếu màn discovery đang show services[].priceScoin
```

API FE 1 cần xử lý:

|    Mức           | Method| Endpoint                       | Việc FE cần sửa |
|------------------|-------|--------------------------------|-----------------|
|   New            | `GET` | `/api/me/matching-profile`     | Đọc `currentActivationCompleted`, `latestAnswerCodes` để biết có cần hỏi 5 câu không. |
|   New            | `GET` | `/api/me/matching-profile/questionnaire` | Render form 5 câu radio theo `questions/options/displayOrder`. |
|   New            | `PUT` | `/api/me/matching-profile`     | Submit 5 answer code flat: `question1AnswerCode`..`question5AnswerCode`. |
| Changed          | `GET` | `/api/me/onboarding-status`    | Đọc thêm `mentoringNeedsCompleted`. |
| Changed          | `GET` | `/api/mentors`                 | Bỏ query `teachingMode`, đổi card sang `subjectResults`, support levels, projects, achievements. |
| Changed          | `GET` | `/api/mentors/recommendations` | Component recommendation card dùng shape mới của mentor card. |
| Changed          | `GET` | `/api/mentors/{mentorUserId}`  | Detail page bỏ field cũ, render field mới. |
| Changed data     | `GET` | `/api/catalog/help-topics`     | Không hard-code `TECH_*`, render 12 help topics mới từ API. |
| Semantic display | `GET` | `/api/mentors/{mentorUserId}/availability-slots` | Nếu hiển thị `services[].priceScoin`, hiểu đây là giá mentee phải trả, không phải giá gốc mentor đặt. |
| Deprecated semantic | `GET` | `/api/mentors/{mentorUserId}/availability` | Chỉ sửa nếu FE còn dùng endpoint deprecated này. |

FE 1 không cần làm:

```text
Mentor profile edit form
Mentor verification upload
Admin questionnaire management
Mentor project/achievement CRUD trong profile cá nhân
Payment order checkout vì request/response schema không đổi
```

Thứ tự nên làm:

```text
1. Onboarding status: thêm mentoringNeedsCompleted.
2. Mentee questionnaire: GET questionnaire -> render -> PUT submit.
3. Discovery card/recommendation: đổi model card.
4. Mentor detail: đổi model detail.
5. Help topic filter: bỏ assumption TECH_*.
6. Check price label trong availability nếu có hiển thị giá.
```

### FE 2 - Nhi: Mentor Profile + Verification + Admin Questionnaire

Scope chính:

```text
Mentor profile edit/settings
Mentor project/achievement management
Mentor verification document upload
Admin questionnaire version/activation management
Catalog options cho mentor support levels
```

API FE 2 cần xử lý:

| Mức | Method | Endpoint | Việc FE cần sửa |
|---|---|---|---|
| Changed | `GET` | `/api/me/mentor-profile` | Đọc response mới: `subjectResults`, 3 support levels, `featuredProjects`, `achievements`; bỏ field cũ. |
| Changed | `PUT` | `/api/me/mentor-profile` | Gửi request body mới, bỏ `supportingSubjects`, `teachingMode`, `sessionDuration`, `linkedinUrl`. |
| New | `GET` | `/api/catalog/mentor-profile-options` | Lấy label cho 3 nhóm support level 1..4. |
| New | `GET` | `/api/me/mentor-projects` | List dự án tiêu biểu. |
| New | `POST` | `/api/me/mentor-projects` | Tạo dự án tiêu biểu. |
| New | `PUT` | `/api/me/mentor-projects/{projectId}` | Cập nhật dự án tiêu biểu. |
| New | `PUT` | `/api/me/mentor-projects/{projectId}/picture` | Upload ảnh project bằng `multipart/form-data`. |
| New | `DELETE` | `/api/me/mentor-projects/{projectId}` | Xóa dự án tiêu biểu. |
| New | `GET` | `/api/me/mentor-achievements` | List học vấn/giải thưởng. |
| New | `POST` | `/api/me/mentor-achievements` | Tạo học vấn/giải thưởng. |
| New | `PUT` | `/api/me/mentor-achievements/{achievementId}` | Cập nhật học vấn/giải thưởng. |
| New | `DELETE` | `/api/me/mentor-achievements/{achievementId}` | Xóa học vấn/giải thưởng. |
| Changed | `POST` | `/api/me/mentor-verification/documents` | Đổi từ JSON metadata sang `multipart/form-data` gồm `documentType` + `file`. |
| New admin | `GET` | `/api/admin/mentoring-questionnaire/versions` | Admin list questionnaire versions. |
| New admin | `GET` | `/api/admin/mentoring-questionnaire/versions/{versionId}` | Admin xem detail version. |
| New admin | `POST` | `/api/admin/mentoring-questionnaire/versions` | Admin tạo version mới hoặc dùng default. |
| New admin | `POST` | `/api/admin/mentoring-questionnaire/activate` | Admin activate version. |
| New admin | `GET` | `/api/admin/mentoring-questionnaire/active` | Admin xem active activation. |

FE 2 không cần làm:

```text
Mentee questionnaire flow
Discovery/recommendation card
Mentor public detail page
Payment order checkout vì request/response schema không đổi
```

Thứ tự nên làm:

```text
1. Mentor profile options: gọi /api/catalog/mentor-profile-options.
2. Mentor profile form: đổi request/response sang subjectResults + 3 support levels.
3. Mentor projects CRUD + upload picture.
4. Mentor achievements CRUD.
5. Verification upload: đổi sang FormData.
6. Admin questionnaire management nếu FE admin panel có màn này.
```

### Ranh Giới Tránh Đụng Nhau

FE 1 owner các component:

```text
Onboarding mentee needs prompt
Mentee questionnaire page/modal
Mentor discovery list
Mentor recommendation card
Mentor public detail
Discovery filter help topics
```

FE 2 owner các component:

```text
Mentor profile form/settings
Mentor subject results editor
Mentor support level selector
Mentor featured projects editor
Mentor achievements editor
Mentor verification document upload
Admin questionnaire management
```

Shared component cần thống nhất props:

```text
Mentor card model: FE 1 là owner chính.
Support level label: dùng data từ /api/catalog/mentor-profile-options, FE 2 là owner chính.
Help topic chip: dùng /api/catalog/help-topics, FE 1 dùng cho discovery, FE 2 dùng cho mentor profile.
```

## 1. Mentee Matching Profile

### 1.1 GET `/api/me/matching-profile`

Trạng thái câu hỏi nhu cầu mentoring của user hiện tại.

Auth: required.

Request body: không có.

Query params: không có.

Response body:

```json
{
  "timestamp": "2026-07-05 09:00:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": {
    "exists": true,
    "currentActivationCompleted": true,
    "latestAnsweredAt": "2026-07-05T09:00:00",
    "activeActivationId": "019f1111-1111-7111-8111-111111111111",
    "activeVersionId": "019f2222-2222-7222-8222-222222222222",
    "activeVersionNumber": 1,
    "foundationNeedLevel": 3,
    "outputReviewNeedLevel": 2,
    "directionNeedLevel": 4,
    "mentorFitCode": "MENTOR_FIT_SUBJECT_MATCH",
    "durationPreferenceCode": "DURATION_30",
    "latestAnswerCodes": {
      "Q1_FOUNDATION_LEVEL": "FOUNDATION_3",
      "Q2_OUTPUT_REVIEW_LEVEL": "OUTPUT_REVIEW_2",
      "Q3_DIRECTION_LEVEL": "DIRECTION_4",
      "Q4_MENTOR_FIT": "MENTOR_FIT_SUBJECT_MATCH",
      "Q5_DURATION_PREFERENCE": "DURATION_30"
    }
  }
}
```

Field notes:

| Field | Type | FE dùng để làm gì |
|---|---:|---|
| `exists` | boolean | User đã từng có answer bundle hay chưa. |
| `currentActivationCompleted` | boolean | Nếu `false`, FE nên hiển thị form 5 câu hỏi hiện tại. |
| `latestAnsweredAt` | datetime/null | Hiển thị thời điểm trả lời gần nhất nếu cần. |
| `activeActivationId` | UUID/null | ID activation hiện tại. FE thường không cần gửi lại. |
| `activeVersionId` | UUID/null | ID version câu hỏi hiện tại. |
| `activeVersionNumber` | number/null | Version number để debug/hiển thị admin. |
| `foundationNeedLevel` | number/null | Feature normalized từ Q1, giá trị 1..4. |
| `outputReviewNeedLevel` | number/null | Feature normalized từ Q2, giá trị 1..4. |
| `directionNeedLevel` | number/null | Feature normalized từ Q3, giá trị 1..4. |
| `mentorFitCode` | string/null | Feature từ Q4. |
| `durationPreferenceCode` | string/null | Feature từ Q5. |
| `latestAnswerCodes` | object | Map questionCode -> optionCode để FE prefill radio nếu cần. |

### 1.2 GET `/api/me/matching-profile/questionnaire`

Lấy bộ 5 câu hỏi đang active.

Auth: required.

Request body: không có.

Query params: không có.

Response body đầy đủ:

```json
{
  "timestamp": "2026-07-05 09:00:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": {
    "activationId": "019f1111-1111-7111-8111-111111111111",
    "versionId": "019f2222-2222-7222-8222-222222222222",
    "versionNumber": 1,
    "phase": "ACTIVE",
    "activatedAt": "2026-07-05T09:00:00",
    "questions": [
      {
        "code": "Q1_FOUNDATION_LEVEL",
        "type": "LEVEL",
        "questionText": "Khi gặp chỗ chưa hiểu, bạn thường cần mentor hỗ trợ tới mức nào?",
        "displayOrder": 1,
        "options": [
          {
            "code": "FOUNDATION_1",
            "label": "Mình thường tự xem lại là đủ",
            "scoreValue": 1,
            "displayOrder": 1
          },
          {
            "code": "FOUNDATION_2",
            "label": "Mình cần ai đó giải thích lại vài ý chính",
            "scoreValue": 2,
            "displayOrder": 2
          },
          {
            "code": "FOUNDATION_3",
            "label": "Mình cần người gỡ rõ phần mình đang hổng",
            "scoreValue": 3,
            "displayOrder": 3
          },
          {
            "code": "FOUNDATION_4",
            "label": "Mình rất cần mentor giúp mình hiểu lại từ gốc",
            "scoreValue": 4,
            "displayOrder": 4
          }
        ]
      },
      {
        "code": "Q2_OUTPUT_REVIEW_LEVEL",
        "type": "LEVEL",
        "questionText": "Khi có project, bài nộp, slide, CV hoặc sản phẩm cần góp ý, bạn thường cần mentor review tới mức nào?",
        "displayOrder": 2,
        "options": [
          {
            "code": "OUTPUT_REVIEW_1",
            "label": "Mình thường tự hoàn thiện được",
            "scoreValue": 1,
            "displayOrder": 1
          },
          {
            "code": "OUTPUT_REVIEW_2",
            "label": "Mình cần góp ý nhanh trước khi chốt",
            "scoreValue": 2,
            "displayOrder": 2
          },
          {
            "code": "OUTPUT_REVIEW_3",
            "label": "Mình cần review khá kỹ để biết phần nào chưa ổn",
            "scoreValue": 3,
            "displayOrder": 3
          },
          {
            "code": "OUTPUT_REVIEW_4",
            "label": "Mình rất cần mentor xem trực tiếp và chỉ rõ nên sửa gì",
            "scoreValue": 4,
            "displayOrder": 4
          }
        ]
      },
      {
        "code": "Q3_DIRECTION_LEVEL",
        "type": "LEVEL",
        "questionText": "Khi đang phân vân giữa nhiều hướng làm, học hoặc chuẩn bị cho bước tiếp theo, bạn thường cần mentor tới mức nào?",
        "displayOrder": 3,
        "options": [
          {
            "code": "DIRECTION_1",
            "label": "Mình thường tự quyết được",
            "scoreValue": 1,
            "displayOrder": 1
          },
          {
            "code": "DIRECTION_2",
            "label": "Mình cần thêm một góc nhìn để yên tâm hơn",
            "scoreValue": 2,
            "displayOrder": 2
          },
          {
            "code": "DIRECTION_3",
            "label": "Mình khá cần người đi trước giúp mình gỡ rối",
            "scoreValue": 3,
            "displayOrder": 3
          },
          {
            "code": "DIRECTION_4",
            "label": "Mình rất cần mentor giúp mình chốt bước tiếp theo",
            "scoreValue": 4,
            "displayOrder": 4
          }
        ]
      },
      {
        "code": "Q4_MENTOR_FIT",
        "type": "FIT",
        "questionText": "Nếu book một buổi mentor lúc đang kẹt, kiểu anh chị nào thường hợp với bạn nhất?",
        "displayOrder": 4,
        "options": [
          {
            "code": "MENTOR_FIT_SUBJECT_MATCH",
            "label": "Anh chị mạnh đúng môn hoặc đúng phần mình đang cần",
            "scoreValue": null,
            "displayOrder": 1
          },
          {
            "code": "MENTOR_FIT_SIMILAR_EXPERIENCE",
            "label": "Người từng gặp đúng kiểu vấn đề mình đang vướng",
            "scoreValue": null,
            "displayOrder": 2
          },
          {
            "code": "MENTOR_FIT_RECENT_ALUMNI",
            "label": "Alumni mới ra trường, gần với chuyện OJT, thực tập và đi làm",
            "scoreValue": null,
            "displayOrder": 3
          }
        ]
      },
      {
        "code": "Q5_DURATION_PREFERENCE",
        "type": "DURATION_PREFERENCE",
        "questionText": "Nếu chỉ book một buổi để gỡ việc, bạn thường thấy thời lượng nào hợp mình nhất?",
        "displayOrder": 5,
        "options": [
          {
            "code": "DURATION_15",
            "label": "15 phút",
            "scoreValue": 15,
            "displayOrder": 1
          },
          {
            "code": "DURATION_30",
            "label": "30 phút",
            "scoreValue": 30,
            "displayOrder": 2
          },
          {
            "code": "DURATION_60",
            "label": "60 phút",
            "scoreValue": 60,
            "displayOrder": 3
          },
          {
            "code": "DURATION_90",
            "label": "90 phút",
            "scoreValue": 90,
            "displayOrder": 4
          }
        ]
      }
    ]
  }
}
```

FE notes:

| Field | Type | FE render |
|---|---:|---|
| `questions[]` | array | Render theo `displayOrder`, không hard-code text. |
| `questions[].type` | enum | `LEVEL`, `FIT`, `DURATION_PREFERENCE`. |
| `options[]` | array | Render radio options theo `displayOrder`. |
| `options[].scoreValue` | number/null | FE không cần tính score, chỉ gửi `code`. |

### 1.3 PUT `/api/me/matching-profile`

Lưu 5 câu trả lời.

Auth: required.

Request body:

```json
{
  "phase": "ACTIVE",
  "question1AnswerCode": "FOUNDATION_3",
  "question2AnswerCode": "OUTPUT_REVIEW_2",
  "question3AnswerCode": "DIRECTION_4",
  "question4AnswerCode": "MENTOR_FIT_SUBJECT_MATCH",
  "question5AnswerCode": "DURATION_30"
}
```

Request field notes:

| Field | Required | FE lấy từ đâu |
|---|---:|---|
| `phase` | no | Có thể gửi `"ACTIVE"` để giữ compatibility. Backend không dùng để tách bộ câu hỏi. |
| `question1AnswerCode` | yes | Option code của `Q1_FOUNDATION_LEVEL`. |
| `question2AnswerCode` | yes | Option code của `Q2_OUTPUT_REVIEW_LEVEL`. |
| `question3AnswerCode` | yes | Option code của `Q3_DIRECTION_LEVEL`. |
| `question4AnswerCode` | yes | Option code của `Q4_MENTOR_FIT`. |
| `question5AnswerCode` | yes | Option code của `Q5_DURATION_PREFERENCE`. |

Response body:

```json
{
  "timestamp": "2026-07-05 09:00:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": {
    "exists": true,
    "currentActivationCompleted": true,
    "latestAnsweredAt": "2026-07-05T09:05:00",
    "activeActivationId": "019f1111-1111-7111-8111-111111111111",
    "activeVersionId": "019f2222-2222-7222-8222-222222222222",
    "activeVersionNumber": 1,
    "foundationNeedLevel": 3,
    "outputReviewNeedLevel": 2,
    "directionNeedLevel": 4,
    "mentorFitCode": "MENTOR_FIT_SUBJECT_MATCH",
    "durationPreferenceCode": "DURATION_30",
    "latestAnswerCodes": {
      "Q1_FOUNDATION_LEVEL": "FOUNDATION_3",
      "Q2_OUTPUT_REVIEW_LEVEL": "OUTPUT_REVIEW_2",
      "Q3_DIRECTION_LEVEL": "DIRECTION_4",
      "Q4_MENTOR_FIT": "MENTOR_FIT_SUBJECT_MATCH",
      "Q5_DURATION_PREFERENCE": "DURATION_30"
    }
  }
}
```

## 2. Admin Questionnaire

### 2.1 GET `/api/admin/mentoring-questionnaire/versions`

Auth: admin/system admin.

Request body: không có.

Response body:

```json
{
  "timestamp": "2026-07-05 09:00:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": [
    {
      "id": "019f2222-2222-7222-8222-222222222222",
      "versionNumber": 1,
      "active": true,
      "createdAt": "2026-07-05T09:00:00",
      "updatedAt": "2026-07-05T09:00:00"
    }
  ]
}
```

### 2.2 GET `/api/admin/mentoring-questionnaire/versions/{versionId}`

Auth: admin/system admin.

Request body: không có.

Response body: giống `GET /api/me/matching-profile/questionnaire`.

### 2.3 POST `/api/admin/mentoring-questionnaire/versions`

Auth: admin/system admin.

Request body có 2 cách gửi.

Cách 1: dùng bộ câu hỏi mặc định:

```json
{}
```

Cách 2: gửi custom questions:

```json
{
  "questions": [
    {
      "code": "Q1_FOUNDATION_LEVEL",
      "type": "LEVEL",
      "questionText": "Khi gặp chỗ chưa hiểu, bạn thường cần mentor hỗ trợ tới mức nào?",
      "options": [
        {
          "code": "FOUNDATION_1",
          "label": "Mình thường tự xem lại là đủ",
          "scoreValue": 1
        },
        {
          "code": "FOUNDATION_2",
          "label": "Mình cần ai đó giải thích lại vài ý chính",
          "scoreValue": 2
        }
      ]
    }
  ]
}
```

Request field notes:

| Field | Required | Note |
|---|---:|---|
| `questions` | no | Nếu null/rỗng, backend dùng bộ mặc định 5 câu. |
| `questions[].code` | yes nếu có questions | Nên giữ 5 semantic code cố định để matching ổn định. |
| `questions[].type` | yes nếu có questions | `LEVEL`, `FIT`, `DURATION_PREFERENCE`. |
| `questions[].questionText` | yes nếu có questions | Text admin chỉnh. |
| `questions[].options[].code` | yes | Option code FE sẽ gửi lại trong submit. |
| `questions[].options[].label` | yes | Label radio FE hiển thị. |
| `questions[].options[].scoreValue` | no | `LEVEL`: 1..4, `DURATION`: 15/30/60/90, `FIT`: null. |

Response body: giống `GET /api/me/matching-profile/questionnaire`.

### 2.4 POST `/api/admin/mentoring-questionnaire/activate`

Auth: admin/system admin.

Request body:

```json
{
  "versionId": "019f2222-2222-7222-8222-222222222222"
}
```

Response body:

```json
{
  "timestamp": "2026-07-05 09:00:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": {
    "activationId": "019f1111-1111-7111-8111-111111111111",
    "versionId": "019f2222-2222-7222-8222-222222222222",
    "versionNumber": 2,
    "activatedAt": "2026-07-05T09:00:00",
    "deactivatedAt": null
  }
}
```

### 2.5 GET `/api/admin/mentoring-questionnaire/active`

Auth: admin/system admin.

Request body: không có.

Response body:

```json
{
  "timestamp": "2026-07-05 09:00:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": {
    "activationId": "019f1111-1111-7111-8111-111111111111",
    "versionId": "019f2222-2222-7222-8222-222222222222",
    "versionNumber": 2,
    "activatedAt": "2026-07-05T09:00:00",
    "deactivatedAt": null
  }
}
```

## 3. Onboarding Status

### 3.1 GET `/api/me/onboarding-status`

Auth: required.

Request body: không có.

Response trước:

```json
{
  "data": {
    "studentProfileCompleted": true,
    "mentorProfileCompleted": false,
    "mentorVerificationStatus": "NOT_STARTED",
    "roles": ["MENTEE"],
    "nextRecommendedAction": "COMPLETE_STUDENT_PROFILE"
  }
}
```

Response sau:

```json
{
  "data": {
    "studentProfileCompleted": true,
    "mentorProfileCompleted": false,
    "mentoringNeedsCompleted": true,
    "mentorVerificationStatus": "NOT_STARTED",
    "roles": ["MENTEE"],
    "nextRecommendedAction": "COMPLETE_STUDENT_PROFILE"
  }
}
```

FE cần sửa:

| Field mới | Type | FE dùng để làm gì |
|---|---:|---|
| `mentoringNeedsCompleted` | boolean | Nếu `false`, FE có thể điều hướng user trả lời 5 câu hỏi nhu cầu mentoring. |

## 4. Mentor Profile

### 4.1 GET `/api/me/mentor-profile`

Auth: required.

Request body: không có.

Response trước:

```json
{
  "data": {
    "exists": true,
    "requiredFieldsCompleted": true,
    "userId": "019f3333-3333-7333-8333-333333333333",
    "email": "mentor@fpt.edu.vn",
    "displayName": "Nguyen Van B",
    "avatarUrl": "https://...",
    "mentorStatus": "ACTIVE",
    "headline": "Backend Developer | Spring Boot Mentor",
    "expertiseDescription": "Mình có kinh nghiệm xây dựng REST API.",
    "supportingSubjects": "PRJ301, SWP391, REST API",
    "isAvailable": true,
    "bookingSuspendedUntil": null,
    "lateCancellationPenaltyPoints": 0,
    "verifiedAt": "2026-07-05T09:00:00",
    "helpTopics": [
      {
        "id": "019f4444-4444-7444-8444-444444444444",
        "code": "HELP_STUDY_PLAN",
        "nameVi": "Lập kế hoạch học tập",
        "nameEn": "Study planning",
        "weight": 100
      }
    ],
    "linkedinUrl": "https://linkedin.com/in/example",
    "githubUrl": "https://github.com/example",
    "portfolioUrl": "https://example.dev",
    "phoneNumber": "0912345678",
    "teachingMode": "ONLINE",
    "sessionDuration": 60,
    "ratingAverage": 4.8,
    "reviewCount": 12,
    "completedSessions": 18,
    "createdAt": "2026-07-05T09:00:00",
    "updatedAt": "2026-07-05T09:00:00"
  }
}
```

Response sau:

```json
{
  "data": {
    "exists": true,
    "requiredFieldsCompleted": true,
    "userId": "019f3333-3333-7333-8333-333333333333",
    "email": "mentor@fpt.edu.vn",
    "displayName": "Nguyen Van B",
    "avatarUrl": "https://...",
    "mentorStatus": "ACTIVE",
    "headline": "Backend Developer | Spring Boot Mentor",
    "expertiseDescription": "Mình có kinh nghiệm xây dựng REST API.",
    "isAvailable": true,
    "bookingSuspendedUntil": null,
    "lateCancellationPenaltyPoints": 0,
    "verifiedAt": "2026-07-05T09:00:00",
    "helpTopics": [
      {
        "id": "019f4444-4444-7444-8444-444444444444",
        "code": "HELP_SUBJECT_SUPPORT",
        "nameVi": "Hỗ trợ môn học",
        "nameEn": "Subject support",
        "weight": 100
      }
    ],
    "subjectResults": [
      {
        "id": "019f5555-5555-7555-8555-555555555555",
        "subjectCode": "PRJ301",
        "subjectName": "Java Web Application Development",
        "scoreValue": 8.6,
        "displayOrder": 1
      }
    ],
    "foundationSupportLevel": 3,
    "outputReviewSupportLevel": 3,
    "directionSupportLevel": 2,
    "featuredProjects": [
      {
        "id": "019f6666-6666-7666-8666-666666666666",
        "title": "SWP391 Booking Platform",
        "pictureUrl": "https://...",
        "content": "Backend lead, REST API, PostgreSQL",
        "projectDescription": "Nền tảng đặt lịch mentoring cho sinh viên.",
        "liveDemoUrl": "https://demo.example.com",
        "displayOrder": 1,
        "createdAt": "2026-07-05T09:00:00",
        "updatedAt": "2026-07-05T09:00:00"
      }
    ],
    "achievements": [
      {
        "id": "019f7777-7777-7777-8777-777777777777",
        "title": "Top 10 Hackathon FPTU",
        "awardDescription": "Giải thưởng project innovation",
        "achievedAt": "2026-03-01",
        "productHeader": "Case study: Growth campaign",
        "productDescription": "Mô tả sản phẩm/case đi kèm thành tích",
        "demoUrl": "https://demo.example.com",
        "displayOrder": 1,
        "createdAt": "2026-07-05T09:00:00",
        "updatedAt": "2026-07-05T09:00:00"
      }
    ],
    "githubUrl": "https://github.com/example",
    "portfolioUrl": "https://example.dev",
    "phoneNumber": "0912345678",
    "ratingAverage": 4.8,
    "reviewCount": 12,
    "completedSessions": 18,
    "createdAt": "2026-07-05T09:00:00",
    "updatedAt": "2026-07-05T09:00:00"
  }
}
```

Field bị bỏ khỏi response:

```text
supportingSubjects
linkedinUrl
teachingMode
sessionDuration
```

Field mới trong response:

| Field | Type | FE render |
|---|---:|---|
| `subjectResults` | array | Danh sách môn - điểm. |
| `foundationSupportLevel` | number | Mức hỗ trợ lấy gốc 1..4. Lấy label từ `/api/catalog/mentor-profile-options`. |
| `outputReviewSupportLevel` | number | Mức review output 1..4. |
| `directionSupportLevel` | number | Mức định hướng 1..4. |
| `featuredProjects` | array | Dự án tiêu biểu optional. |
| `achievements` | array | Học vấn/giải thưởng optional. |

### 4.2 PUT `/api/me/mentor-profile`

Auth: required.

Request body trước:

```json
{
  "headline": "Backend Developer | Spring Boot Mentor",
  "expertiseDescription": "Mình có kinh nghiệm xây dựng REST API với Spring Boot.",
  "supportingSubjects": "Cơ sở dữ liệu, Lập trình Java, Kiến trúc API",
  "isAvailable": true,
  "helpTopicIds": [
    "019f4444-4444-7444-8444-444444444444"
  ],
  "teachingMode": "ONLINE",
  "sessionDuration": 60,
  "linkedinUrl": "https://www.linkedin.com/in/example",
  "githubUrl": "https://github.com/example",
  "portfolioUrl": "https://example.dev",
  "phoneNumber": "0912345678"
}
```

Request body sau:

```json
{
  "headline": "Backend Developer | Spring Boot Mentor",
  "expertiseDescription": "Mình có kinh nghiệm xây dựng REST API với Spring Boot.",
  "isAvailable": true,
  "helpTopicIds": [
    "019f4444-4444-7444-8444-444444444444"
  ],
  "subjectResults": [
    {
      "subjectCode": "PRJ301",
      "subjectName": "Java Web Application Development",
      "scoreValue": 8.6
    },
    {
      "subjectCode": "SWP391",
      "subjectName": "Application Development Project",
      "scoreValue": 9.0
    }
  ],
  "foundationSupportLevel": 3,
  "outputReviewSupportLevel": 3,
  "directionSupportLevel": 2,
  "githubUrl": "https://github.com/example",
  "portfolioUrl": "https://example.dev",
  "phoneNumber": "0912345678"
}
```

Request field notes:

| Field | Required | Validation | Note cho FE |
|---|---:|---|---|
| `headline` | yes | max 200 | Tiêu đề mentor. |
| `expertiseDescription` | yes | max 1000 | Mô tả chuyên môn. |
| `isAvailable` | no | boolean | Null khi tạo mới sẽ default true. |
| `helpTopicIds` | yes | non-empty, max 20 | Chọn từ `/api/catalog/help-topics`. |
| `subjectResults` | yes | non-empty, max 20 | Thay cho `supportingSubjects`. |
| `subjectResults[].subjectCode` | yes | max 80 | Mã môn hoặc tên ngắn nếu không có code chuẩn. |
| `subjectResults[].subjectName` | no | max 200 | Tên môn hiển thị. |
| `subjectResults[].scoreValue` | yes | 0.0..10.0 | Điểm môn. |
| `foundationSupportLevel` | yes | 1..4 | Label lấy từ catalog options. |
| `outputReviewSupportLevel` | yes | 1..4 | Label lấy từ catalog options. |
| `directionSupportLevel` | yes | 1..4 | Label lấy từ catalog options. |
| `githubUrl` | no | string | Optional. |
| `portfolioUrl` | no | string | Optional. |
| `phoneNumber` | yes | VN phone regex | Bắt đầu 03/05/07/08/09, 10 số. |

Field không gửi nữa:

```text
supportingSubjects
teachingMode
sessionDuration
linkedinUrl
```

Response body sau khi lưu: giống `GET /api/me/mentor-profile` bản sau.

## 5. Mentor Profile Options

### 5.1 GET `/api/catalog/mentor-profile-options`

API mới để FE render radio/select label cho 3 support level.

Auth: không bắt buộc theo controller hiện tại.

Request body: không có.

Response body đầy đủ:

```json
{
  "timestamp": "2026-07-05 09:00:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": {
    "foundationSupportLevels": [
      {
        "value": 1,
        "label": "Gợi ý nhanh để mentee tự ôn lại"
      },
      {
        "value": 2,
        "label": "Giải thích lại các ý chính khi mentee bị vướng"
      },
      {
        "value": 3,
        "label": "Gỡ rõ phần mentee đang hổng và cho hướng luyện lại"
      },
      {
        "value": 4,
        "label": "Kèm mentee hiểu lại từ gốc với lộ trình ngắn"
      }
    ],
    "outputReviewSupportLevels": [
      {
        "value": 1,
        "label": "Góp ý nhanh trước khi mentee chốt bài"
      },
      {
        "value": 2,
        "label": "Review các điểm chính trong bài nộp/project/CV/report"
      },
      {
        "value": 3,
        "label": "Review kỹ và chỉ ra phần chưa ổn cần sửa"
      },
      {
        "value": 4,
        "label": "Xem trực tiếp, góp ý sâu và hướng dẫn cách cải thiện"
      }
    ],
    "directionSupportLevels": [
      {
        "value": 1,
        "label": "Chia sẻ góc nhìn nhanh khi mentee phân vân"
      },
      {
        "value": 2,
        "label": "Giúp mentee so sánh lựa chọn học, ngành hoặc việc làm"
      },
      {
        "value": 3,
        "label": "Gỡ rối định hướng/OJT/career bằng kinh nghiệm thực tế"
      },
      {
        "value": 4,
        "label": "Giúp mentee chốt bước tiếp theo và kế hoạch hành động"
      }
    ]
  }
}
```

FE notes:

```text
Không hard-code label 1..4 trong FE.
Form mentor profile chỉ gửi value number, không gửi label.
```

## 6. Mentor Featured Projects

### 6.1 GET `/api/me/mentor-projects`

Auth: required, không cho ADMIN/SYSTEM_ADMIN.

Request body: không có.

Response body:

```json
{
  "timestamp": "2026-07-05 09:00:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": [
    {
      "id": "019f6666-6666-7666-8666-666666666666",
      "title": "SWP391 Booking Platform",
      "pictureUrl": "https://...",
      "content": "Backend lead, REST API, PostgreSQL",
      "projectDescription": "Nền tảng đặt lịch mentoring cho sinh viên.",
      "liveDemoUrl": "https://demo.example.com",
      "displayOrder": 1,
      "createdAt": "2026-07-05T09:00:00",
      "updatedAt": "2026-07-05T09:00:00"
    }
  ]
}
```

### 6.2 POST `/api/me/mentor-projects`

Auth: required, không cho ADMIN/SYSTEM_ADMIN.

Request body:

```json
{
  "title": "SWP391 Booking Platform",
  "content": "Backend lead, REST API, PostgreSQL",
  "projectDescription": "Nền tảng đặt lịch mentoring cho sinh viên.",
  "liveDemoUrl": "https://demo.example.com"
}
```

Request field notes:

| Field | Required | Validation | Note |
|---|---:|---|---|
| `title` | yes | max 200 | Tên dự án. |
| `content` | no | max 2000 | Nội dung nổi bật, vai trò, tech/case. |
| `projectDescription` | no | max 2000 | Mô tả dự án. |
| `liveDemoUrl` | no | string | Link demo optional. |

Response body:

```json
{
  "timestamp": "2026-07-05 09:00:00",
  "status": 201,
  "code": "CREATED_0201",
  "message": "Tạo mới thành công",
  "data": {
    "id": "019f6666-6666-7666-8666-666666666666",
    "title": "SWP391 Booking Platform",
    "pictureUrl": null,
    "content": "Backend lead, REST API, PostgreSQL",
    "projectDescription": "Nền tảng đặt lịch mentoring cho sinh viên.",
    "liveDemoUrl": "https://demo.example.com",
    "displayOrder": 1,
    "createdAt": "2026-07-05T09:00:00",
    "updatedAt": "2026-07-05T09:00:00"
  }
}
```

### 6.3 PUT `/api/me/mentor-projects/{projectId}`

Auth: required, không cho ADMIN/SYSTEM_ADMIN.

Path params:

```text
projectId: UUID
```

Request body: giống `POST /api/me/mentor-projects`.

Response body:

```json
{
  "timestamp": "2026-07-05 09:00:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": {
    "id": "019f6666-6666-7666-8666-666666666666",
    "title": "SWP391 Booking Platform Updated",
    "pictureUrl": "https://...",
    "content": "Updated content",
    "projectDescription": "Updated description",
    "liveDemoUrl": "https://demo.example.com",
    "displayOrder": 1,
    "createdAt": "2026-07-05T09:00:00",
    "updatedAt": "2026-07-05T09:10:00"
  }
}
```

### 6.4 PUT `/api/me/mentor-projects/{projectId}/picture`

Auth: required, không cho ADMIN/SYSTEM_ADMIN.

Content-Type: `multipart/form-data`.

Path params:

```text
projectId: UUID
```

Request body:

```text
file: MultipartFile
```

Response body:

```json
{
  "timestamp": "2026-07-05 09:00:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": {
    "id": "019f6666-6666-7666-8666-666666666666",
    "title": "SWP391 Booking Platform",
    "pictureUrl": "https://r2.example.com/mentor-projects/image.jpg",
    "content": "Backend lead, REST API, PostgreSQL",
    "projectDescription": "Nền tảng đặt lịch mentoring cho sinh viên.",
    "liveDemoUrl": "https://demo.example.com",
    "displayOrder": 1,
    "createdAt": "2026-07-05T09:00:00",
    "updatedAt": "2026-07-05T09:12:00"
  }
}
```

### 6.5 DELETE `/api/me/mentor-projects/{projectId}`

Auth: required, không cho ADMIN/SYSTEM_ADMIN.

Path params:

```text
projectId: UUID
```

Request body: không có.

Response body:

```json
{
  "timestamp": "2026-07-05 09:00:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": null
}
```

## 7. Mentor Achievements

### 7.1 GET `/api/me/mentor-achievements`

Auth: required, không cho ADMIN/SYSTEM_ADMIN.

Request body: không có.

Response body:

```json
{
  "timestamp": "2026-07-05 09:00:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": [
    {
      "id": "019f7777-7777-7777-8777-777777777777",
      "title": "Top 10 Hackathon FPTU",
      "awardDescription": "Giải thưởng project innovation",
      "achievedAt": "2026-03-01",
      "productHeader": "Case study: Growth campaign",
      "productDescription": "Mô tả sản phẩm/case đi kèm thành tích",
      "demoUrl": "https://demo.example.com",
      "displayOrder": 1,
      "createdAt": "2026-07-05T09:00:00",
      "updatedAt": "2026-07-05T09:00:00"
    }
  ]
}
```

### 7.2 POST `/api/me/mentor-achievements`

Auth: required, không cho ADMIN/SYSTEM_ADMIN.

Request body:

```json
{
  "title": "Top 10 Hackathon FPTU",
  "awardDescription": "Giải thưởng project innovation",
  "achievedAt": "2026-03-01",
  "productHeader": "Case study: Growth campaign",
  "productDescription": "Mô tả sản phẩm/case đi kèm thành tích",
  "demoUrl": "https://demo.example.com"
}
```

Request field notes:

| Field | Required | Validation | Note |
|---|---:|---|---|
| `title` | yes | max 200 | Tên học vấn/giải thưởng. |
| `awardDescription` | no | max 2000 | Mô tả thành tích. |
| `achievedAt` | no | date `yyyy-MM-dd` | Thời gian đạt được. |
| `productHeader` | no | max 200 | Header sản phẩm/case đi kèm. |
| `productDescription` | no | max 2000 | Mô tả sản phẩm/case. |
| `demoUrl` | no | string | Link demo optional. |

Response body:

```json
{
  "timestamp": "2026-07-05 09:00:00",
  "status": 201,
  "code": "CREATED_0201",
  "message": "Tạo mới thành công",
  "data": {
    "id": "019f7777-7777-7777-8777-777777777777",
    "title": "Top 10 Hackathon FPTU",
    "awardDescription": "Giải thưởng project innovation",
    "achievedAt": "2026-03-01",
    "productHeader": "Case study: Growth campaign",
    "productDescription": "Mô tả sản phẩm/case đi kèm thành tích",
    "demoUrl": "https://demo.example.com",
    "displayOrder": 1,
    "createdAt": "2026-07-05T09:00:00",
    "updatedAt": "2026-07-05T09:00:00"
  }
}
```

### 7.3 PUT `/api/me/mentor-achievements/{achievementId}`

Auth: required, không cho ADMIN/SYSTEM_ADMIN.

Path params:

```text
achievementId: UUID
```

Request body: giống `POST /api/me/mentor-achievements`.

Response body:

```json
{
  "timestamp": "2026-07-05 09:00:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": {
    "id": "019f7777-7777-7777-8777-777777777777",
    "title": "Top 10 Hackathon FPTU Updated",
    "awardDescription": "Updated description",
    "achievedAt": "2026-03-01",
    "productHeader": "Updated case",
    "productDescription": "Updated product description",
    "demoUrl": "https://demo.example.com",
    "displayOrder": 1,
    "createdAt": "2026-07-05T09:00:00",
    "updatedAt": "2026-07-05T09:10:00"
  }
}
```

### 7.4 DELETE `/api/me/mentor-achievements/{achievementId}`

Auth: required, không cho ADMIN/SYSTEM_ADMIN.

Path params:

```text
achievementId: UUID
```

Request body: không có.

Response body:

```json
{
  "timestamp": "2026-07-05 09:00:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": null
}
```

## 8. Mentor Verification Upload

### 8.1 POST `/api/me/mentor-verification/documents`

Endpoint cũ giữ method/path nhưng đổi request body từ JSON metadata sang multipart upload file thật.

Auth: required, không cho ADMIN/SYSTEM_ADMIN.

Request trước:

```http
POST /api/me/mentor-verification/documents
Content-Type: application/json
```

```json
{
  "documentType": "FPTU_AFFILIATION_PROOF",
  "fileUrl": "https://res.cloudinary.com/demo/image/upload/v12345/proof.jpg",
  "publicId": "mentor-verification/user-123/proof_abc123",
  "originalFilename": "proof.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 245678
}
```

Request sau:

```http
POST /api/me/mentor-verification/documents
Content-Type: multipart/form-data
```

```text
documentType: FPTU_AFFILIATION_PROOF | EXPERTISE_PROOF
file: MultipartFile
```

FE cần sửa:

```text
Không upload file lên Cloudinary/R2 từ FE rồi gửi metadata nữa.
FE gửi thẳng MultipartFile cho backend.
Backend tự upload R2 và lưu metadata.
```

Response body sau:

```json
{
  "timestamp": "2026-07-05 09:00:00",
  "status": 201,
  "code": "CREATED_0201",
  "message": "Tạo mới thành công",
  "data": {
    "requestId": "019f8888-8888-7888-8888-888888888888",
    "mentorUserId": "019f3333-3333-7333-8333-333333333333",
    "status": "DRAFT",
    "submitNote": null,
    "reviewNote": null,
    "rejectionReason": null,
    "revisionCount": 0,
    "submittedAt": null,
    "termsAcceptedAt": null,
    "termsVersion": null,
    "reviewedAt": null,
    "createdAt": "2026-07-05T09:00:00",
    "updatedAt": "2026-07-05T09:00:00",
    "documents": [
      {
        "id": "019f9999-9999-7999-8999-999999999999",
        "documentType": "FPTU_AFFILIATION_PROOF",
        "fileUrl": "https://r2.example.com/mentor-verification/proof.pdf",
        "publicId": "mentor-verification/user-123/proof.pdf",
        "originalFilename": "proof.pdf",
        "contentType": "application/pdf",
        "sizeBytes": 245678,
        "reviewStatus": "PENDING",
        "reviewNote": null,
        "createdAt": "2026-07-05T09:00:00",
        "updatedAt": "2026-07-05T09:00:00"
      }
    ],
    "timeline": [],
    "checklist": {
      "mentorProfileCompleted": true,
      "hasFptuAffiliationProof": true,
      "hasExpertiseProof": false,
      "termsAccepted": false,
      "canSubmit": false
    },
    "allowedActions": {
      "canEdit": true,
      "canSubmit": false,
      "canWithdraw": true
    }
  }
}
```

Quota:

```text
FPTU_AFFILIATION_PROOF: tối đa 1 file
EXPERTISE_PROOF: tối đa 3 file
```

Supported file:

```text
image/jpeg
image/jpg
image/png
application/pdf
```

## 9. Mentor Discovery

### 9.1 GET `/api/mentors`

Auth: required.

Request body: không có.

Query params trước:

```text
page: number, default 0
size: number, default 12
sortBy: string, default relevance
direction: ASC | DESC, default DESC
keyword: string
tagIds: UUID[]
campusId: UUID
specializationId: UUID
teachingMode: ONLINE | OFFLINE | HYBRID
```

Query params sau:

```text
page: number, default 0
size: number, default 12
sortBy: string, default relevance
direction: ASC | DESC, default DESC
keyword: string
tagIds: UUID[]
campusId: UUID
specializationId: UUID
```

FE cần sửa:

```text
Bỏ filter teachingMode khỏi UI/query string.
Keyword search giờ match thêm subjectResults, project, achievement.
```

Response trước:

```json
{
  "data": {
    "content": [
      {
        "mentorUserId": "019f3333-3333-7333-8333-333333333333",
        "displayName": "Nguyễn Văn A",
        "avatarUrl": "https://...",
        "headline": "Senior Backend Developer",
        "expertiseDescription": "Có 2 năm kinh nghiệm làm Java Spring Boot.",
        "supportingSubjects": "PRJ301, SWP391",
        "isAvailable": true,
        "ratingAverage": 4.8,
        "reviewCount": 12,
        "completedSessions": 15,
        "teachingMode": "ONLINE",
        "verifiedAt": "2026-07-05T09:00:00",
        "campusId": "019faaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa",
        "campusName": "FPT University Hồ Chí Minh",
        "programId": "019fbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb",
        "programName": "Công nghệ thông tin",
        "specializationId": "019fcccc-cccc-7ccc-8ccc-cccccccccccc",
        "specializationName": "Kỹ thuật phần mềm",
        "matchScore": 87.5,
        "helpTopicTags": []
      }
    ],
    "page": 0,
    "size": 12,
    "totalElements": 1,
    "totalPages": 1,
    "last": true
  }
}
```

Response sau:

```json
{
  "data": {
    "content": [
      {
        "mentorUserId": "019f3333-3333-7333-8333-333333333333",
        "displayName": "Nguyễn Văn A",
        "avatarUrl": "https://...",
        "headline": "Senior Backend Developer",
        "expertiseDescription": "Có 2 năm kinh nghiệm làm Java Spring Boot.",
        "subjectResults": [
          {
            "id": "019f5555-5555-7555-8555-555555555555",
            "subjectCode": "PRJ301",
            "subjectName": "Java Web Application Development",
            "scoreValue": 8.6,
            "displayOrder": 1
          }
        ],
        "foundationSupportLevel": 3,
        "outputReviewSupportLevel": 3,
        "directionSupportLevel": 2,
        "featuredProjects": [
          {
            "id": "019f6666-6666-7666-8666-666666666666",
            "title": "SWP391 Booking Platform",
            "pictureUrl": "https://...",
            "content": "Backend lead, REST API, PostgreSQL",
            "projectDescription": "Nền tảng đặt lịch mentoring cho sinh viên.",
            "liveDemoUrl": "https://demo.example.com",
            "displayOrder": 1,
            "createdAt": "2026-07-05T09:00:00",
            "updatedAt": "2026-07-05T09:00:00"
          }
        ],
        "achievements": [
          {
            "id": "019f7777-7777-7777-8777-777777777777",
            "title": "Top 10 Hackathon FPTU",
            "awardDescription": "Giải thưởng project innovation",
            "achievedAt": "2026-03-01",
            "productHeader": "Case study: Growth campaign",
            "productDescription": "Mô tả sản phẩm/case đi kèm thành tích",
            "demoUrl": "https://demo.example.com",
            "displayOrder": 1,
            "createdAt": "2026-07-05T09:00:00",
            "updatedAt": "2026-07-05T09:00:00"
          }
        ],
        "isAvailable": true,
        "ratingAverage": 4.8,
        "reviewCount": 12,
        "completedSessions": 15,
        "verifiedAt": "2026-07-05T09:00:00",
        "campusId": "019faaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa",
        "campusName": "FPT University Hồ Chí Minh",
        "programId": "019fbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb",
        "programName": "Công nghệ thông tin",
        "specializationId": "019fcccc-cccc-7ccc-8ccc-cccccccccccc",
        "specializationName": "Kỹ thuật phần mềm",
        "matchScore": 87.5,
        "helpTopicTags": []
      }
    ],
    "page": 0,
    "size": 12,
    "totalElements": 1,
    "totalPages": 1,
    "last": true
  }
}
```

Field bị bỏ khỏi card:

```text
supportingSubjects
teachingMode
```

Field thêm vào card:

```text
subjectResults
foundationSupportLevel
outputReviewSupportLevel
directionSupportLevel
featuredProjects
achievements
```

### 9.2 GET `/api/mentors/recommendations`

Auth: required.

Request body: không có.

Query params:

```text
limit: number, default 12
```

Response body trước dùng card cũ trong `mentor`.

Response body sau dùng card mới trong `mentor`:

```json
{
  "data": [
    {
      "mentor": {
        "mentorUserId": "019f3333-3333-7333-8333-333333333333",
        "displayName": "Nguyễn Văn A",
        "avatarUrl": "https://...",
        "headline": "Senior Backend Developer",
        "expertiseDescription": "Có 2 năm kinh nghiệm làm Java Spring Boot.",
        "subjectResults": [],
        "foundationSupportLevel": 3,
        "outputReviewSupportLevel": 3,
        "directionSupportLevel": 2,
        "featuredProjects": [],
        "achievements": [],
        "isAvailable": true,
        "ratingAverage": 4.8,
        "reviewCount": 12,
        "completedSessions": 15,
        "verifiedAt": "2026-07-05T09:00:00",
        "campusId": "019faaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa",
        "campusName": "FPT University Hồ Chí Minh",
        "programId": "019fbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb",
        "programName": "Công nghệ thông tin",
        "specializationId": "019fcccc-cccc-7ccc-8ccc-cccccccccccc",
        "specializationName": "Kỹ thuật phần mềm",
        "matchScore": 95.5,
        "helpTopicTags": []
      },
      "matchScore": 95.5,
      "matchReasons": [
        "Khớp nhu cầu mentoring hiện tại",
        "Mentor có đánh giá cao"
      ]
    }
  ]
}
```

FE cần sửa:

```text
Component recommendation card nên dùng cùng model với discovery card mới.
Không đọc mentor.supportingSubjects hoặc mentor.teachingMode nữa.
```

### 9.3 GET `/api/mentors/{mentorUserId}`

Auth: required.

Request body: không có.

Path params:

```text
mentorUserId: UUID
```

Response trước:

```json
{
  "data": {
    "mentorUserId": "019f3333-3333-7333-8333-333333333333",
    "displayName": "Nguyễn Văn A",
    "avatarUrl": "https://...",
    "headline": "Senior Backend Developer",
    "bio": "Bio từ student profile",
    "expertiseDescription": "Mô tả chuyên môn",
    "supportingSubjects": "PRJ301, SWP391",
    "isAvailable": true,
    "bookingSuspendedUntil": null,
    "ratingAverage": 4.8,
    "reviewCount": 12,
    "completedSessions": 15,
    "teachingMode": "ONLINE",
    "defaultSessionDuration": 60,
    "verifiedAt": "2026-07-05T09:00:00",
    "campusId": "019faaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa",
    "campusName": "FPT University Hồ Chí Minh",
    "programId": "019fbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb",
    "programName": "Công nghệ thông tin",
    "specializationId": "019fcccc-cccc-7ccc-8ccc-cccccccccccc",
    "specializationName": "Kỹ thuật phần mềm",
    "semester": 5,
    "alumni": false,
    "portfolioUrl": "https://example.dev",
    "linkedinUrl": "https://linkedin.com/in/example",
    "githubUrl": "https://github.com/example",
    "helpTopicTags": [],
    "services": [],
    "canRequestBooking": true,
    "hasCompletedProfile": true,
    "hasActiveServices": true
  }
}
```

Response sau:

```json
{
  "data": {
    "mentorUserId": "019f3333-3333-7333-8333-333333333333",
    "displayName": "Nguyễn Văn A",
    "avatarUrl": "https://...",
    "headline": "Senior Backend Developer",
    "bio": "Bio từ student profile",
    "expertiseDescription": "Mô tả chuyên môn",
    "subjectResults": [
      {
        "id": "019f5555-5555-7555-8555-555555555555",
        "subjectCode": "PRJ301",
        "subjectName": "Java Web Application Development",
        "scoreValue": 8.6,
        "displayOrder": 1
      }
    ],
    "foundationSupportLevel": 3,
    "outputReviewSupportLevel": 3,
    "directionSupportLevel": 2,
    "featuredProjects": [],
    "achievements": [],
    "isAvailable": true,
    "bookingSuspendedUntil": null,
    "ratingAverage": 4.8,
    "reviewCount": 12,
    "completedSessions": 15,
    "verifiedAt": "2026-07-05T09:00:00",
    "campusId": "019faaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa",
    "campusName": "FPT University Hồ Chí Minh",
    "programId": "019fbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb",
    "programName": "Công nghệ thông tin",
    "specializationId": "019fcccc-cccc-7ccc-8ccc-cccccccccccc",
    "specializationName": "Kỹ thuật phần mềm",
    "semester": 5,
    "alumni": false,
    "portfolioUrl": "https://example.dev",
    "githubUrl": "https://github.com/example",
    "helpTopicTags": [],
    "services": [],
    "canRequestBooking": true,
    "hasCompletedProfile": true,
    "hasActiveServices": true
  }
}
```

Field bị bỏ khỏi detail:

```text
supportingSubjects
teachingMode
defaultSessionDuration
linkedinUrl
```

Field thêm vào detail:

```text
subjectResults
foundationSupportLevel
outputReviewSupportLevel
directionSupportLevel
featuredProjects
achievements
```

## 10. Availability Price Display

### 10.1 GET `/api/mentors/{mentorUserId}/availability`

Endpoint deprecated nhưng vẫn tồn tại.

Request body: không có.

Response body schema không đổi.

Response body có field liên quan:

```json
{
  "data": [
    {
      "slotId": "019faaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa",
      "startTime": "2026-07-05T10:00:00",
      "endTime": "2026-07-05T11:00:00",
      "timezone": "Asia/Ho_Chi_Minh",
      "durationMinutes": 60,
      "teachingMode": "ONLINE",
      "pendingRequestCount": 0,
      "acceptedSlotCount": 0,
      "maxPendingRequests": 3,
      "remainingRequestSlots": 3,
      "services": [
        {
          "serviceId": "019fbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb",
          "title": "Review project",
          "durationMinutes": 60,
          "isFree": false,
          "priceScoin": 110000
        }
      ]
    }
  ]
}
```

Semantic thay đổi:

```text
Trước: services[].priceScoin = giá service gốc mentor đặt.
Sau: services[].priceScoin = giá mentee phải trả, đã cộng mentee surcharge nếu service không free.
```

### 10.2 GET `/api/mentors/{mentorUserId}/availability-slots`

Request body: không có.

Response body schema giống mục 10.1.

Semantic thay đổi giống mục 10.1:

```text
data[].services[].priceScoin đã là giá hiển thị cho mentee sau surcharge.
```

## 11. Help Topics Catalog

### 11.1 GET `/api/catalog/help-topics`

Endpoint không đổi request/response schema.

Request body: không có.

Response body schema:

```json
{
  "data": [
    {
      "id": "019f4444-4444-7444-8444-444444444444",
      "code": "HELP_SUBJECT_SUPPORT",
      "nameVi": "Hỗ trợ môn học",
      "nameEn": "Subject support",
      "weight": 100
    }
  ]
}
```

Data thay đổi: bỏ bộ `TECH_*`, dùng 12 help topics mới.

Danh sách code mới:

```text
HELP_SUBJECT_SUPPORT: Hỗ trợ môn học
HELP_MAJOR_DIRECTION: Định hướng ngành/chuyên ngành
HELP_CAREER_PATH: Định hướng nghề nghiệp
HELP_OJT_INTERNSHIP: Giải đáp OJT/thực tập
HELP_CV_REVIEW: Đánh giá CV
HELP_INTERVIEW: Luyện phỏng vấn
HELP_CAPSTONE_THESIS: Hỗ trợ đồ án/khóa luận
HELP_LANGUAGE_SUPPORT: Hỗ trợ ngoại ngữ
HELP_CAMPUS_LIFE: Thích nghi FPTU & campus life
HELP_INFORMATION: Cung cấp thông tin
HELP_QA: Giải đáp thắc mắc
HELP_PROJECT_CASE_FEEDBACK: Góp ý dự án/case study
```

FE cần sửa:

```text
Không giả định help topic chỉ là IT/TECH.
Nếu FE có hard-code TECH_* thì phải bỏ.
Render theo API trả về.
```

## 12. Checklist FE Migration

Mentee:

```text
1. Gọi GET /api/me/onboarding-status.
2. Nếu mentoringNeedsCompleted=false, gọi GET /api/me/matching-profile/questionnaire.
3. Render 5 câu hỏi radio theo questions/options/displayOrder.
4. Submit PUT /api/me/matching-profile với 5 answer code flat.
5. Sau submit, đọc currentActivationCompleted=true để cho user đi tiếp.
```

Mentor profile:

```text
1. Bỏ input supportingSubjects dạng text.
2. Thêm input subjectResults dạng list môn - điểm.
3. Bỏ teachingMode.
4. Bỏ sessionDuration.
5. Bỏ linkedinUrl.
6. Thêm 3 nhóm radio/select support level.
7. Gọi GET /api/catalog/mentor-profile-options để lấy label 1..4.
8. Gửi PUT /api/me/mentor-profile theo request body mới.
```

Mentor optional profile:

```text
1. Thêm UI quản lý Dự án tiêu biểu bằng /api/me/mentor-projects.
2. Upload ảnh project bằng multipart PUT /api/me/mentor-projects/{projectId}/picture.
3. Thêm UI quản lý Học vấn & Giải thưởng bằng /api/me/mentor-achievements.
4. Đây là optional, không bắt buộc để complete mentor profile.
```

Verification:

```text
1. Bỏ flow FE upload file lên storage rồi gửi metadata.
2. Đổi sang FormData gửi documentType + file.
3. Giữ quota hiển thị: FPTU proof 1 file, expertise proof tối đa 3 file.
```

Discovery:

```text
1. Bỏ filter teachingMode.
2. Bỏ render supportingSubjects.
3. Render subjectResults, support levels, featuredProjects, achievements.
4. Recommendation card dùng cùng response shape mới của MentorDiscoveryCardResponse.
```

Availability:

```text
1. services[].priceScoin trên availability là giá mentee thấy sau surcharge.
2. Nếu FE có label "giá gốc mentor đặt", không dùng priceScoin này để hiển thị như giá gốc nữa.
3. Payment order checkout không cần sửa FE trong đợt này vì request/response schema không đổi.
```
