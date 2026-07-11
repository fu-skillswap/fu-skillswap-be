# Dependency Rules

## Rules chot
- `shared..` khong duoc phu thuoc `modules..`
- `infrastructure..` khong duoc phu thuoc business service cu the trong `modules..`, tru adapter/runtime case hop le
- `controller` khong goi `repository` truc tiep
- `repository` cua module A khong duoc module B goi truc tiep
- `modules.admin..` chi duoc consume business modules, business modules khong duoc phu thuoc nguoc vao `modules.admin..`
- Cac module business khong duoc tao dependency vong tron

## Giao tiep cheo module
Uu tien theo thu tu sau:
1. shared primitive
2. service facade ro rang
3. event / outbox eventual consistency
4. port/interface neu can tach adapter

Khong cho phep:
- goi chui repository module khac
- transaction chaining qua nhieu services module khac ma khong co policy ro rang
- native query join table cheo module cho logic business thong thuong neu khong co debt record ro rang

## Database schema isolation
- Forward convention:
  - entity cua module nao nen map vao bang co prefix/ownership ro rang cua module do
  - vi du: `identity_*`, `payment_*`, `forum_*`, `admin_*`
- Legacy tables dang ton tai chua dong nhat se duoc freeze thanh debt.
- Moi bang/entity moi sau Phase 1 phai theo naming convention da chot.

## ArchUnit freeze policy
- Dung `FreezingArchRule` de baseline cac violation kien truc hien tai.
- Freeze store duoc commit vao repo.
- CI duoc phep pass voi debt hien tai.
- Moi violation moi hoac debt bi mo rong se fail ngay.

## Cach cap nhat freeze store khi refactor hop le
- Chi refreeze khi co ly do kien truc ro rang va da cap nhat docs nay.
- Khong refreeze de che vi pham moi.
- Moi lan refreeze phai duoc review nhu mot thay doi kien truc, khong xem nhu thay doi test thong thuong.
