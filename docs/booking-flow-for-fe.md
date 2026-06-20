# Booking Flow For FE

## 1. Queue model

- Mỗi availability slot có thể nhận nhiều booking request cùng lúc.
- Tối đa `3` request ở trạng thái `PENDING` cho một slot.
- Tạo booking mới chỉ tạo request chờ duyệt, chưa khóa slot ngay.

## 2. Ý nghĩa của `slot.isBooked`

- `slot.isBooked = true` chỉ có nghĩa là mentor đã `ACCEPTED` một booking cho slot đó.
- `slot.isBooked = false` không có nghĩa slot chưa có ai request, mà chỉ có nghĩa slot chưa có booking nào được accept.

## 3. Availability response

API: `GET /api/mentors/{mentorUserId}/availability`

Các field cũ được giữ nguyên. Backend bổ sung thêm:

- `pendingRequestCount`: số request `PENDING` hiện tại của slot
- `maxPendingRequests`: giới hạn tối đa request `PENDING` cho slot, hiện tại cố định là `3`
- `remainingRequestSlots`: số suất request còn lại, tính bằng `max(0, maxPendingRequests - pendingRequestCount)`

Ví dụ:

```json
{
  "slotId": "019f1234-aaaa-bbbb-cccc-1234567890ab",
  "startTime": "2026-06-22T14:00:00",
  "endTime": "2026-06-22T15:00:00",
  "timezone": "Asia/Ho_Chi_Minh",
  "durationMinutes": 60,
  "teachingMode": "ONLINE",
  "recurring": true,
  "pendingRequestCount": 2,
  "maxPendingRequests": 3,
  "remainingRequestSlots": 1
}
```

## 4. Khi nào slot không còn xuất hiện trong availability

Frontend sẽ không thấy slot nếu:

- slot inactive
- slot ở quá khứ
- slot đã có booking được accept (`slot.isBooked = true`)
- `pendingRequestCount` đã đạt `3`
- mentor đang bị suspended theo business rule hiện tại

## 5. Booking status cho mentee

- `PENDING`: đang chờ mentor duyệt
- `ACCEPTED`: mentor đã chấp nhận request
- `REJECTED`: mentor từ chối request
- `CANCELLED_BY_MENTEE`: mentee tự hủy
- `CANCELLED_BY_MENTOR`: mentor hủy sau khi đã accept
- `COMPLETED`: buổi mentoring đã hoàn tất

### Auto reject khi mentor chọn request khác

Khi mentor accept một booking trong cùng slot:

- booking được chọn -> `ACCEPTED`
- các booking `PENDING` còn lại của cùng slot -> `REJECTED`
- `rejectReason` sẽ là:

```text
Mentor đã từ chối booking của bạn vì đã có lịch trình khác.
```

Frontend nên hiểu đây là trường hợp mentor đã chọn request khác cho chính slot đó.

## 6. Gợi ý UI cho mentor

- Ở màn mentor xử lý booking, nên group các request theo `slotId` hoặc theo `startTime/endTime`.
- Với một slot có nhiều request `PENDING`, mentor chỉ được chọn accept một request.
- Sau khi accept, FE nên refetch lại danh sách request và availability của mentor đó.

## 7. Gợi ý UI cho mentee

- `PENDING` không phải là đặt lịch thành công, chỉ là gửi yêu cầu.
- Một slot có thể vẫn còn hiển thị cho người khác nếu `remainingRequestSlots > 0`.
- Nếu request bị `REJECTED` với lý do auto reject ở trên, FE nên hiển thị message theo hướng:
  - mentor đã chọn lịch trình khác cho slot này
  - mentee cần chọn slot khác
