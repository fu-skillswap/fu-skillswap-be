# Transaction Policy

## Nguyen tac tong quat
- Monolith nay uu tien consistency ro rang nhung khong cho phep transaction day chuyen cheo qua nhieu modules neu co the tranh.
- Mac dinh uu tien eventual consistency qua event/outbox cho side effects khong can dong bo ngay.

## Propagation rule
- `REQUIRED`
  - La default cho service business trong cung flow nghiep vu
  - Dung khi can cung commit/rollback voi aggregate chinh
- `REQUIRES_NEW`
  - Chi dung khi co chu dich tach side-effect khoi transaction chinh
  - Vi du:
    - audit trail
    - admin note
    - outbox-like side effect doc lap
  - Khong duoc dung de “chua loi tam thoi” cho coupling business xau

## Facade cheo module
- Module A goi facade module B duoc phep neu:
  - responsibility cua B la ro rang
  - propagation expectation duoc biet truoc
  - khong tao lock chain khong can thiet
- Neu side effect khong can phan hoi ngay:
  - uu tien event/outbox thay vi goi facade dong bo
  - listener consumer chay `AFTER_COMMIT` va duoc xem la `best effort`
  - consumer listener phai tu `try/catch + error log` day du, khong duoc nem exception nguoc lai producer

## Cam
- Distributed transaction tu phat trong monolith
- Service chaining qua nhieu module roi cung dung mot transaction lon ma khong co ly do ro rang
- Mo transaction moi chi de “vuot qua” boundary kien truc
- Event cascade hai chieu giua producer domain va consumer domain trong cung chuoi nghiep vu

## Rule thuc thi cho Phase 1
- Khong doi transaction semantics business hang loat.
- Phase 1 chi:
  - document current policy
  - them guardrail review points
  - danh dau noi nao dang co risk om transaction de dua sang phase business tuong ung
