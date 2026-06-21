# 會議室預約系統 Meeting Room Reservation System

Spring Boot + PostgreSQL 後端 demo，以 Docker Compose 部署。

---

## 技術棧

| 層級 | 技術 |
|------|------|
| 框架 | Spring Boot 3.2 |
| ORM | Spring Data JPA / Hibernate |
| 資料庫 | PostgreSQL 15 |
| 安全 | Spring Security + JWT (JJWT 0.12) |
| 驗證 | Jakarta Bean Validation |
| 容器 | Docker Compose |
| 建置 | Maven |

---

## 快速啟動

```bash
# 啟動 PostgreSQL + Spring Boot
docker-compose up --build

# API 運行在 http://localhost:8080
```

> 首次啟動自動建表並塞入假資料（16 間會議室、8 位使用者、12 筆預約）。

---

## Database Schema（作業第 2 點）

### `room` — 會議室
| 欄位 | 型別 | 說明 |
|------|------|------|
| id | BIGINT PK | 主鍵 |
| name | VARCHAR(50) UNIQUE | 會議室名稱（如：會議室0304）|
| capacity | INTEGER | 最大容納人數 |
| location | VARCHAR(100) | 樓層描述（1F / 3F / 6F / 7F）|
| active | BOOLEAN | 是否啟用 |

### `users` — 使用者
| 欄位 | 型別 | 說明 |
|------|------|------|
| id | BIGINT PK | 主鍵 |
| username | VARCHAR(50) | 姓名 |
| email | VARCHAR(100) UNIQUE | 電子郵件 |
| employee_no | VARCHAR(20) | 員工編號 |
| phone | VARCHAR(20) | 聯絡電話 |
| department | VARCHAR(50) | 部門 |
| role | VARCHAR(20) | `USER` / `REVIEWER` |
| password | VARCHAR(100) | BCrypt 雜湊密碼 |

### `reservation` — 預約記錄
| 欄位 | 型別 | 說明 |
|------|------|------|
| id | BIGINT PK | 主鍵 |
| reservation_no | VARCHAR(20) UNIQUE | 預約編號（MTGRES00001）|
| room_id | BIGINT FK → room | 會議室 |
| user_id | BIGINT FK → users | 預約人 |
| reviewer_id | BIGINT FK → users | 審核者（nullable）|
| start_time | TIMESTAMP | 開始時間 |
| end_time | TIMESTAMP | 結束時間 |
| status | VARCHAR(20) | `APPROVED` / `PROCESSING` / `REJECTED` |
| meeting_title | VARCHAR(200) | 會議主旨 |
| attendees | INTEGER | 使用人數 |
| return_reason | VARCHAR(500) | 退回原因 |
| remark | VARCHAR(500) | 備註 |
| created_at | TIMESTAMP | 建立時間 |

---

## ORM 與 Foreign Key（作業第 3 點）

```
Room ←──┐
         │ @ManyToOne (room_id FK)
         ├── Reservation ──→ User (user_id FK)
         │                └──→ User (reviewer_id FK, nullable)
User ←──┘
```

`Room`、`User`、`Reservation` 三個 `@Entity` 均依作業範本建立，並自行補充必要欄位。

---

## API 文件

所有 `/api/reservations/**` 端點需帶 JWT Token：
```
Authorization: Bearer <token>
```

### 登入取得 Token

**POST /api/auth/login**
```json
Request:  { "email": "huang@example.com", "password": "user123" }
Response: { "token": "eyJ...", "userId": 1, "username": "黃維善", "role": "USER" }
```

預設帳號：
- 一般使用者：任意 `*@example.com`，密碼 `user123`
- 審核者：`admin@example.com`，密碼 `admin123`

---

### 4.1 建立預約

**POST /api/reservations**

確保同一個會議室在同一時間段內不能被重複預約（悲觀鎖 + JPQL 衝突查詢）。

```json
Request:
{
  "roomId": 9,
  "userId": 1,
  "startTime": "2026-02-01T10:00:00",
  "endTime": "2026-02-01T12:00:00",
  "meetingTitle": "季度業績檢討",
  "attendees": 8,
  "remark": "需要投影機"
}

Response 200: { "reservationNo": "MTGRES00013", "status": "APPROVED", ... }
Response 400: { "error": "會議室 [會議室0304] 在 ... 時段已被預約" }
Response 400: { "error": "預約人數 16 超過會議室 [會議室0304] 容量上限 15 人" }
Response 401: 未帶 Token
```

**GET /api/reservations/room/{roomId}** — 查詢特定會議室所有預約（含 JOIN 使用者資訊）

---

### 4.2 退回機制

**POST /api/reservations/return** — 申請退回（狀態 → PROCESSING）
```json
{
  "reservationId": 13,
  "reviewerId": 8,
  "returnReason": "會議延期，需取消此次預約"
}
```

**POST /api/reservations/review** — 審核（僅限 REVIEWER 角色）
```json
{ "reservationId": 13, "approved": true, "remark": "同意退回" }
```
- `approved: true`  → **REJECTED**（同意退回，預約取消）
- `approved: false` → **APPROVED**（拒絕退回，預約繼續）

Response 403：一般使用者 Token 嘗試呼叫此端點

---

### 4.3 依日期查詢 APPROVED 預約（對應圖 C）

**GET /api/reservations/daily?date=2026-01-20**

查詢當日所有通過的預約，依開始時間、會議室名稱排序。

```json
Response:
[
  { "reservationNo": "MTGRES0095", "roomName": "會議室0304", "startTime": "2026-01-20T10:00:00", ... },
  { "reservationNo": "MTGRES0096", "roomName": "會議室0304", "startTime": "2026-01-20T11:00:00", ... }
]
```

---

### 4.4 依月份查詢各狀態預約

**GET /api/reservations/monthly?year=2026&month=1**

依 Status 找出每個月會議室預約狀況（rejected、approved、processing）。

```json
Response:
{
  "APPROVED":   [ { "reservationNo": "MTGRES0090", ... }, ... ],
  "PROCESSING": [ { "reservationNo": "MTGRES0088", ... } ],
  "REJECTED":   [ { "reservationNo": "MTGRES0089", ... } ]
}
```

---

## 輸入驗證

| 欄位 | 規則 |
|------|------|
| meetingTitle | 最多 200 字元 |
| remark | 最多 500 字元 |
| attendees | 1 ~ 1000 |
| returnReason | 必填、最多 500 字元 |

驗證失敗一律回傳 `400 Bad Request`：`{ "error": "欄位: 說明" }`

---

## 安全性

| 情境 | 結果 |
|------|------|
| 無 Token 呼叫 API | `401 Unauthorized` |
| 偽造 / 過期 Token | `401 Unauthorized` |
| USER 呼叫 `/review` | `403 Forbidden` |

---

## 專案結構

```
src/main/java/com/example/meetingroom/
├── config/
│   └── SecurityConfig.java           # Spring Security + RBAC
├── controller/
│   ├── AuthController.java           # POST /api/auth/login
│   └── ReservationController.java    # 預約相關 API
├── dto/                              # Request / Response DTO
├── entity/
│   ├── Room.java                     # 會議室 ORM
│   ├── User.java                     # 使用者 ORM
│   └── Reservation.java              # 預約記錄（含 FK）
├── enums/
│   └── ReservationStatus.java        # PROCESSING / APPROVED / REJECTED
├── exception/
│   └── GlobalExceptionHandler.java   # 統一 400 錯誤格式
├── repository/
│   ├── RoomRepository.java           # 含悲觀寫鎖查詢
│   └── ReservationRepository.java    # 衝突查詢 / 日期 / 月份查詢
├── security/
│   ├── JwtUtil.java                  # Token 產生 / 驗證
│   └── JwtAuthenticationFilter.java  # Bearer Token 解析
└── service/
    ├── MeetingRoomService.java       # 核心邏輯（4.1 reserveRoom ~ 4.4）
    └── DataInitializer.java          # 啟動塞假資料
```
