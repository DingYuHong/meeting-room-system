# 會議室預約系統 - 後端 Demo

## 專案架構總覽

```
meeting-room-system/
├── docker-compose.yml          ← Docker 容器編排設定
├── Dockerfile                  ← 建置 Spring Boot 映像
├── pom.xml                     ← Maven 相依套件設定
└── src/main/java/com/example/meetingroom/
    ├── MeetingRoomApplication.java   ← Spring Boot 入口
    ├── enums/
    │   └── ReservationStatus.java    ← 狀態列舉
    ├── entity/                       ← JPA ORM 物件（對應資料表）
    │   ├── Room.java
    │   ├── User.java
    │   └── Reservation.java
    ├── repository/                   ← 資料庫存取層
    │   ├── RoomRepository.java
    │   ├── UserRepository.java
    │   └── ReservationRepository.java
    ├── dto/                          ← API 資料傳輸物件
    │   ├── ReservationRequest.java
    │   ├── ReservationResponse.java
    │   ├── ReturnRequest.java
    │   └── ReviewRequest.java
    ├── service/                      ← 商業邏輯層
    │   ├── MeetingRoomService.java
    │   └── DataInitializer.java
    └── controller/
        └── ReservationController.java ← REST API 入口
```

---

## 步驟一：Docker Compose 說明

**docker-compose.yml** 定義了兩個服務：

| 服務 | 說明 |
|------|------|
| `postgres` | PostgreSQL 15 資料庫，Port 5432 |
| `webapp`   | Spring Boot 應用程式，Port 8080 |

`depends_on` + `healthcheck` 確保 PostgreSQL 完全啟動後，Spring Boot 才開始連線。

```bash
# 啟動所有服務
docker-compose up --build

# 背景執行
docker-compose up -d --build

# 停止
docker-compose down
```

---

## 步驟二：Database Schema 設計

根據畫面截圖，設計出以下三張主要資料表：

### `room` 表（會議室）
| 欄位 | 型別 | 說明 |
|------|------|------|
| id | BIGINT PK | 主鍵 |
| name | VARCHAR(50) UNIQUE | 會議室名稱 |
| capacity | INT | 最大容納人數 |
| location | VARCHAR(100) | 地點描述 |
| active | BOOLEAN | 是否啟用 |

### `users` 表（使用者）
| 欄位 | 型別 | 說明 |
|------|------|------|
| id | BIGINT PK | 主鍵 |
| username | VARCHAR(50) | 姓名 |
| email | VARCHAR(100) UNIQUE | 電子郵件 |
| employee_no | VARCHAR(20) | 員工編號 |
| phone | VARCHAR(20) | 電話 |
| department | VARCHAR(50) | 部門 |
| role | VARCHAR(20) | 角色：USER / REVIEWER |

### `reservation` 表（預約記錄）
| 欄位 | 型別 | 說明 |
|------|------|------|
| id | BIGINT PK | 主鍵 |
| reservation_no | VARCHAR(20) UNIQUE | 預約編號 |
| **room_id** | BIGINT FK → room.id | ★ 關聯會議室 |
| **user_id** | BIGINT FK → users.id | ★ 關聯預約人 |
| **reviewer_id** | BIGINT FK → users.id | ★ 關聯審核者 |
| start_time | TIMESTAMP | 開始時間 |
| end_time | TIMESTAMP | 結束時間 |
| status | VARCHAR(20) | PROCESSING/APPROVED/REJECTED |
| meeting_title | VARCHAR(200) | 會議主旨 |
| attendees | INT | 使用人數 |
| return_reason | VARCHAR(500) | 退回原因 |
| remark | VARCHAR(500) | 備註 |
| created_at | TIMESTAMP | 建立時間 |

---

## 步驟三：ORM Entity 與 Foreign Key

### Room.java
```java
@Entity
@Table(name = "room")
public class Room {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;       // 會議室名稱
    private Integer capacity;  // 最大容納人數
    private String location;   // 地點
    private Boolean active;    // 是否啟用
}
```

### User.java
```java
@Entity
@Table(name = "users")  // 避開 SQL 保留字 "user"
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;    // 姓名
    private String email;       // 電子郵件
    private String employeeNo;  // 員工編號
    private String phone;       // 聯絡電話
    private String department;  // 部門
    private String role;        // USER / REVIEWER
}
```

### Reservation.java（含 Foreign Key）
```java
@Entity
@Table(name = "reservation")
public class Reservation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ★ FK：多個預約對應同一個會議室
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    // ★ FK：多個預約對應同一個使用者（預約人）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ★ FK：多個預約可對應同一個審核者（可為 null）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;
    // ... 其他欄位
}
```

**關係說明：**
- `Room` ← 1 : N → `Reservation`（一個會議室有多筆預約）
- `User` ← 1 : N → `Reservation`（一個使用者有多筆預約）
- `User` ← 1 : N → `Reservation`（一個審核者審核多筆退回）

---

## 步驟四：API 文件

### 4.1 建立預約

```
POST /api/reservations
Content-Type: application/json

{
  "roomId": 1,
  "userId": 1,
  "startTime": "2026-02-01T10:00:00",
  "endTime": "2026-02-01T11:00:00",
  "meetingTitle": "季度檢討會議",
  "attendees": 8,
  "remark": "需要投影設備"
}
```

**防止雙重預約邏輯：**
```
新預約的開始時間 < 既有預約的結束時間
AND
新預約的結束時間 > 既有預約的開始時間
→ 代表時間重疊，拒絕預約
```

### 4.2 退回機制

**申請退回（使用者操作）：**
```
POST /api/reservations/return

{
  "reservationId": 5,
  "reviewerId": 8,
  "returnReason": "行程異動，需取消此次預約"
}
```
→ 狀態從 `APPROVED` 改為 `PROCESSING`

**審核退回（審核者操作）：**
```
POST /api/reservations/review

{
  "reservationId": 5,
  "approved": true,
  "remark": "同意退回"
}
```
→ `approved: true` → 狀態改為 `REJECTED`（預約取消）
→ `approved: false` → 狀態改回 `APPROVED`（預約維持）

### 4.3 查詢當日 APPROVED 預約（圖C）

```
GET /api/reservations/daily?date=2026-01-04
```

回傳當天所有 APPROVED 預約，按時間和會議室排序。

### 4.4 查詢每月預約狀況

```
GET /api/reservations/monthly?year=2026&month=1
```

回傳：
```json
{
  "APPROVED":    [ {...}, {...} ],
  "PROCESSING":  [ {...} ],
  "REJECTED":    [ {...} ]
}
```

---

## 技術重點解說

### @ManyToOne vs @OneToMany
本專案採用單向 `@ManyToOne`（在 Reservation 端），原因：
- 避免雙向關係的複雜性
- 查詢時使用 `JOIN FETCH` 控制 N+1 問題

### JPQL 時間衝突判斷
兩段時間重疊的數學條件：
```
A 和 B 重疊 ⟺ A.start < B.end AND A.end > B.start
```
只有當 APPROVED 狀態的預約才算「佔用」時段。

### @Transactional
所有修改資料的 Service 方法都加了 `@Transactional`，確保：
- 資料庫操作要嘛全部成功，要嘛全部回滾
- 避免「半途失敗」導致的資料不一致

### Lazy Loading & JOIN FETCH
Entity 設定 `FetchType.LAZY`（延遲載入），避免不必要的關聯查詢。
需要關聯資料時，Repository 使用 `JOIN FETCH` 明確指定一次載入。
