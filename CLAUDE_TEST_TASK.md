# Claude CLI 任務：執行會議室預約系統功能測試

## 你的目標
針對本專案 (Spring Boot + PostgreSQL 會議室預約系統) 執行完整的功能測試，並在發現問題時自動修復。請依照下列步驟，逐步完成。**每完成一個 phase 都要先回報結果再進入下一步**，不要全部跑完才講。

---

## Phase 1：環境檢查與啟動

1. 確認專案根目錄有 `docker-compose.yml`、`Dockerfile`、`pom.xml` (或 `build.gradle`)。
2. 執行 `docker-compose down -v` 清掉舊狀態，再 `docker-compose up -d --build` 啟動。
3. 用 `docker-compose ps` 確認所有容器都是 `Up` 狀態。
4. 用 `docker-compose logs app` 等到看見 `Started ... in X.X seconds`。若有 stack trace，先停下來修好再繼續。
5. 確認服務有在聽：`curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/auth/login` 應回 400/401/405 其中之一（代表 endpoint 存在）。

**Phase 1 通過條件**：容器全部 healthy，且 8080 port 有回應。

---

## Phase 2：跑功能測試腳本

1. 把同目錄下的 `functional-test.sh` 標記為可執行：`chmod +x functional-test.sh`
2. 先檢查腳本最上方的 `SEED_*` 變數，是否符合本專案 `data.sql` / `DataInitializer` 裡實際塞的 seed 帳號與 ID。**如果不符請改成正確的值再執行**，不要硬跑。
3. 執行 `./functional-test.sh`，把完整輸出貼回給我。
4. 把腳本回報的 `PASS / FAIL` 統計整理成表格給我。

**測試項目對應作業要求：**

| 作業項目 | 測試名稱 | 預期行為 |
|---|---|---|
| 4.1 | `RESERVE_OK` | 正常預約 → 201/200 |
| 4.1 | `RESERVE_EXACT_CONFLICT` | 完全相同時段 → 400/409 |
| 4.1 | `RESERVE_OVERLAP_CONFLICT` | **時段重疊** → 400/409（最容易被漏掉） |
| 4.1 | `RESERVE_OVER_CAPACITY` | 人數超過 capacity → 400 |
| 4.1 | `RESERVE_NO_TOKEN` | 沒帶 JWT → 401 |
| 4.2 | `RETURN_REQUEST` | 使用者申請退回 → status = PROCESSING |
| 4.2 | `REVIEW_BY_USER_FORBIDDEN` | 一般使用者打 /review → 403 |
| 4.2 | `REVIEW_APPROVE` | 審核者通過 → status = REJECTED 或 APPROVED |
| 4.3 | `DAILY_LIST` | 回傳 array、依 startTime 升冪、只包含 APPROVED |
| 4.4 | `MONTHLY_GROUPED` | 回傳分成 PROCESSING / APPROVED / REJECTED 三組 |

---

## Phase 3：分析失敗項目

對於每一個 FAIL：
1. 從 `docker-compose logs app --tail=200` 找出對應的 stack trace 或業務 log。
2. 對應到 source code（通常在 `src/main/java/.../service` 或 `controller`）。
3. **指出問題的根本原因**（不是症狀），列出檔名 + 行號 + 一句話描述。

特別注意這些常見 bug：
- **時段重疊判斷錯誤**：conflict query 必須用 `existing.start < new.end AND existing.end > new.start`，只用 `=` 比對會放行重疊區間。
- **N+1 查詢**：4.3、4.4 的 JOIN 沒寫好可能導致一次查詢觸發大量 SQL。
- **時區問題**：`LocalDateTime` 跟 PG 的 `timestamp with time zone` 混用會出包。
- **權限檢查只看登入沒看 role**：要驗 `@PreAuthorize("hasRole('ADMIN')")` 是否真的有掛上去。

---

## Phase 4：修復並重測

1. 針對每個確認的 bug 提出 patch（直接改 code）。
2. `docker-compose restart app` 或 `--build` 後重跑 `./functional-test.sh`。
3. 重複 Phase 3 ~ Phase 4 直到所有測試 PASS。

---

## Phase 5：補自動化測試

所有 manual test 通過後：
1. 在 `src/test/java` 下新增 `ReservationIntegrationTest`，用 `@SpringBootTest` + `MockMvc` + Testcontainers 把上述 10 個 case 寫成 JUnit。
2. 跑 `./mvnw test` 確認通過。
3. 在 README 加一段 "Testing" 說明怎麼跑這些測試。

---

## 回報格式
每個 Phase 結束時，用這個格式回報：

```
## Phase X 完成

### 執行結果
- ...

### 發現的問題
- [檔案:行號] 問題描述

### 下一步
- ...
```

開始吧。
