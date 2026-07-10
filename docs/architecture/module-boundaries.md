# Module Boundaries

## Muc tieu
Tai lieu nay chot ranh gioi ky thuat cua project SkillSwap de cac phase sau chi sua trong dung boundary lien quan.

## Lop nen
- `shared`
  - Chua primitive dung chung: response envelope, exception, cursor, outbox, idempotency, persistence helper, rate limit, utility.
  - Khong duoc phu thuoc vao `modules`.
- `infrastructure`
  - Chua runtime adapters va system wiring: config, security, filter, realtime, websocket, storage.
  - Khong chua business rule dac thu theo module, tru runtime adapter can thiet.
- `modules`
  - Chua business subdomains:
    - `identity`
    - `academic`
    - `catalog`
    - `matching`
    - `mentor`
    - `booking`
    - `payment`
    - `session`
    - `conversation`
    - `notification`
    - `forum`
    - `feedback`
    - `admin`
    - `system`

## Ownership theo lop
- `controller`
  - Nam trong module so huu API.
  - Khong goi repository truc tiep.
- `service`
  - Chua business rule va orchestration.
  - Duoc goi shared primitive va infrastructure adapters hop le.
- `repository`
  - Chi duoc module so huu aggregate su dung truc tiep.
- `event`
  - Dung cho giao tiep eventual consistency giua modules hoac giua business va infrastructure.
- `config`
  - Thuoc `infrastructure.config` hoac `shared.config` neu la primitive dung chung. Phase 1 se tap trung ownership ve `infrastructure.config`.

## Cac boundary quan trong
- `identity`
  - So huu dang nhap, refresh token, current user, Google auth, Google Calendar connect/sync.
- `academic` + `catalog`
  - So huu du lieu hoc thuat, campus, program, specialization, help topics.
- `matching`
  - So huu questionnaire, matching profile, normalized mentee features.
- `mentor`
  - So huu mentor profile, mentor services, mentor verification.
  - Side-effect reactive sang `booking`, `discovery`, `notification` phai di qua event noi bo, khong goi truc tiep service consumer.
- `booking` + `session` + `payment`
  - So huu booking lifecycle, session state, payment, wallet, payout flow.
- `conversation` + `notification`
  - So huu REST source of truth cho chat/notification va push realtime thong qua outbox.
- `forum`
  - So huu forum posts, comments, reactions, reports.
- `admin`
  - La consumer/workbench cho business modules. Business modules khong phu thuoc nguoc vao `admin`.

## Debt list hien tai
- Legacy coupling giua mot so business modules van ton tai do lich su phat trien.
- Naming bang database chua dong nhat theo module prefix.
- Architecture freeze store se ghi nhan debt hien tai; moi violation moi deu khong duoc phep phat sinh.
