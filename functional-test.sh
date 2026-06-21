#!/usr/bin/env bash
# ============================================================
# 會議室預約系統 - 功能Test腳本
# 對應作業要求 4.1 / 4.2 / 4.3 / 4.4
# ============================================================
# 用法：
#   chmod +x functional-test.sh
#   ./functional-test.sh
#
# 環境變數（可選）：
#   BASE_URL          API 位址 (預設 http://localhost:8080)
#   USER_EMAIL        一般使用者帳號
#   USER_PASSWORD     一般使用者密碼
#   ADMIN_EMAIL       審核者帳號
#   ADMIN_PASSWORD    審核者密碼
#   ROOM_ID           Test用會議室 ID
#   ROOM_CAPACITY     該會議室容量
#   USER_ID           一般使用者 ID
#   REVIEWER_ID       審核者 ID
# ============================================================

set -u
trap 'echo "腳本中斷"; exit 130' INT

# ---------- 設定（請依實際 seed data 修改） ----------
BASE_URL="${BASE_URL:-http://localhost:8080}"
USER_EMAIL="${USER_EMAIL:-huang@example.com}"
USER_PASSWORD="${USER_PASSWORD:-user123}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@example.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123}"
ROOM_ID="${ROOM_ID:-1}"
ROOM_CAPACITY="${ROOM_CAPACITY:-20}"
USER_ID="${USER_ID:-1}"
REVIEWER_ID="${REVIEWER_ID:-8}"

# Test用未來日期（避免撞到 seed data）
TEST_DATE="2026-07-15"
DAILY_QUERY_DATE="2026-01-20"   # 4.3 要查 approved 的日期，依 seed data 改
MONTHLY_YEAR="2026"
MONTHLY_MONTH="1"

# ---------- 顏色與計數 ----------
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

PASS=0
FAIL=0
SKIP=0
FAILED_TESTS=()

BODY_FILE="/tmp/mrs_test_body.$$"
trap 'rm -f "$BODY_FILE"' EXIT

# ---------- 小工具 ----------
section() {
  echo ""
  echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo -e "${BLUE}  $1${NC}"
  echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

# call_api METHOD PATH [JSON_BODY] [TOKEN]
# 回傳 HTTP status code，response body 寫到 $BODY_FILE
call_api() {
  local method="$1"
  local path="$2"
  local data="${3:-}"
  local token="${4:-}"

  local -a args=(-s -o "$BODY_FILE" -w "%{http_code}" -X "$method"
                 -H "Content-Type: application/json")
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$data" ]] && args+=(-d "$data")
  args+=("${BASE_URL}${path}")

  curl "${args[@]}" 2>/dev/null
}

# assert_in NAME ACTUAL EXPECTED1 [EXPECTED2 ...]
assert_in() {
  local name="$1"; shift
  local actual="$1"; shift
  local expected_list="$*"

  for exp in "$@"; do
    if [[ "$actual" == "$exp" ]]; then
      printf "  ${GREEN}✓ PASS${NC} | %-35s (HTTP %s)\n" "$name" "$actual"
      ((PASS++))
      return 0
    fi
  done
  printf "  ${RED}✗ FAIL${NC} | %-35s (expect [%s], got %s)\n" "$name" "$expected_list" "$actual"
  if [[ -s "$BODY_FILE" ]]; then
    local snippet
    snippet=$(head -c 250 "$BODY_FILE")
    printf "         ${YELLOW}body:${NC} %s\n" "$snippet"
  fi
  ((FAIL++))
  FAILED_TESTS+=("$name")
  return 1
}

# assert_body_contains NAME PATTERN
assert_body_contains() {
  local name="$1"
  local pattern="$2"
  if grep -q "$pattern" "$BODY_FILE" 2>/dev/null; then
    printf "  ${GREEN}✓ PASS${NC} | %-35s (body contains '%s')\n" "$name" "$pattern"
    ((PASS++))
  else
    printf "  ${RED}✗ FAIL${NC} | %-35s (body missing '%s')\n" "$name" "$pattern"
    printf "         ${YELLOW}body:${NC} %s\n" "$(head -c 250 "$BODY_FILE")"
    ((FAIL++))
    FAILED_TESTS+=("$name")
  fi
}

extract_json() {
  local key="$1"
  # 先試字串型 "key":"value"
  local v
  v=$(grep -o "\"${key}\":\"[^\"]*\"" "$BODY_FILE" 2>/dev/null | head -1 | cut -d'"' -f4)
  if [[ -z "$v" ]]; then
    # 再試數字型 "key":123
    v=$(grep -o "\"${key}\":[0-9]\+" "$BODY_FILE" 2>/dev/null | head -1 | cut -d: -f2)
  fi
  echo "$v"
}

# ============================================================
# Phase 0: Health Check
# ============================================================
section "Phase 0 · 服務健康檢查"

echo "BASE_URL = $BASE_URL"
HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/auth/login" 2>/dev/null || echo "000")
if [[ "$HEALTH" == "000" ]]; then
  echo -e "${RED}✗ 無法連線到 $BASE_URL${NC}"
  echo "  請確認 docker-compose 已啟動：docker-compose ps"
  exit 2
fi
echo -e "${GREEN}✓ 服務有回應 (HTTP $HEALTH)${NC}"

# ============================================================
# Phase 1: 取得 Token
# ============================================================
section "Phase 1 · 登入取得 JWT"

call_api POST "/api/auth/login" \
  "{\"email\":\"$USER_EMAIL\",\"password\":\"$USER_PASSWORD\"}" > /tmp/status1
USER_STATUS=$(cat /tmp/status1)
USER_TOKEN=$(extract_json token)
[[ -z "$USER_TOKEN" ]] && USER_TOKEN=$(extract_json accessToken)

if [[ -n "$USER_TOKEN" ]]; then
  echo -e "  ${GREEN}✓${NC} 一般使用者登入成功 (HTTP $USER_STATUS)"
else
  echo -e "  ${RED}✗${NC} 一般使用者登入失敗 (HTTP $USER_STATUS)"
  echo "    body: $(head -c 200 "$BODY_FILE")"
  echo -e "  ${YELLOW}→ 請檢查腳本最上方的 USER_EMAIL / USER_PASSWORD 是否符合 seed data${NC}"
  exit 3
fi

call_api POST "/api/auth/login" \
  "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" > /tmp/status2
ADMIN_STATUS=$(cat /tmp/status2)
ADMIN_TOKEN=$(extract_json token)
[[ -z "$ADMIN_TOKEN" ]] && ADMIN_TOKEN=$(extract_json accessToken)

if [[ -n "$ADMIN_TOKEN" ]]; then
  echo -e "  ${GREEN}✓${NC} 審核者登入成功 (HTTP $ADMIN_STATUS)"
else
  echo -e "  ${YELLOW}!${NC} 審核者登入失敗，4.2 的 REVIEW Test會 skip"
  ADMIN_TOKEN=""
fi

# ============================================================
# 4.1 預約功能 + 衝突檢查
# ============================================================
section "4.1 · 預約功能 + 衝突檢查"

# (a) 正常預約
PAYLOAD_OK="{
  \"roomId\":$ROOM_ID, \"userId\":$USER_ID,
  \"startTime\":\"${TEST_DATE}T10:00:00\",
  \"endTime\":\"${TEST_DATE}T12:00:00\",
  \"meetingTitle\":\"FuncTest-Normal\",
  \"contactPhone\":\"#123\",
  \"department\":\"TestDept\",
  \"attendees\":5
}"
S=$(call_api POST "/api/reservations" "$PAYLOAD_OK" "$USER_TOKEN")
assert_in "RESERVE_OK" "$S" "200" "201"
NEW_RES_ID=$(extract_json id)
echo "    → 新建預約 id = ${NEW_RES_ID:-N/A}"

# (b) 完全相同時段衝突
S=$(call_api POST "/api/reservations" "$PAYLOAD_OK" "$USER_TOKEN")
assert_in "RESERVE_EXACT_CONFLICT" "$S" "400" "409"

# (c) 時段「重疊」(11:00~13:00 vs 10:00~12:00)
PAYLOAD_OVERLAP="{
  \"roomId\":$ROOM_ID, \"userId\":$USER_ID,
  \"startTime\":\"${TEST_DATE}T11:00:00\",
  \"endTime\":\"${TEST_DATE}T13:00:00\",
  \"meetingTitle\":\"OverlapTest\",
  \"contactPhone\":\"#123\", \"department\":\"TestDept\", \"attendees\":5
}"
S=$(call_api POST "/api/reservations" "$PAYLOAD_OVERLAP" "$USER_TOKEN")
assert_in "RESERVE_OVERLAP_CONFLICT" "$S" "400" "409"

# (d) 包含 (09:00~13:00 完全涵蓋 10:00~12:00)
PAYLOAD_CONTAIN="{
  \"roomId\":$ROOM_ID, \"userId\":$USER_ID,
  \"startTime\":\"${TEST_DATE}T09:00:00\",
  \"endTime\":\"${TEST_DATE}T13:00:00\",
  \"meetingTitle\":\"ContainTest\",
  \"contactPhone\":\"#123\", \"department\":\"TestDept\", \"attendees\":5
}"
S=$(call_api POST "/api/reservations" "$PAYLOAD_CONTAIN" "$USER_TOKEN")
assert_in "RESERVE_CONTAIN_CONFLICT" "$S" "400" "409"

# (e) AdjacentSlot不應該擋 (12:00~13:00, 上一筆是 ~12:00)
PAYLOAD_ADJ="{
  \"roomId\":$ROOM_ID, \"userId\":$USER_ID,
  \"startTime\":\"${TEST_DATE}T12:00:00\",
  \"endTime\":\"${TEST_DATE}T13:00:00\",
  \"meetingTitle\":\"AdjacentSlot\",
  \"contactPhone\":\"#123\", \"department\":\"TestDept\", \"attendees\":5
}"
S=$(call_api POST "/api/reservations" "$PAYLOAD_ADJ" "$USER_TOKEN")
assert_in "RESERVE_ADJACENT_OK" "$S" "200" "201"

# (f) 超過容量
OVER=$((ROOM_CAPACITY + 100))
PAYLOAD_OVER="{
  \"roomId\":$ROOM_ID, \"userId\":$USER_ID,
  \"startTime\":\"${TEST_DATE}T15:00:00\",
  \"endTime\":\"${TEST_DATE}T16:00:00\",
  \"meetingTitle\":\"OverCapacity\",
  \"contactPhone\":\"#123\", \"department\":\"TestDept\", \"attendees\":$OVER
}"
S=$(call_api POST "/api/reservations" "$PAYLOAD_OVER" "$USER_TOKEN")
assert_in "RESERVE_OVER_CAPACITY" "$S" "400"

# (g) endTime <= startTime
PAYLOAD_BAD_TIME="{
  \"roomId\":$ROOM_ID, \"userId\":$USER_ID,
  \"startTime\":\"${TEST_DATE}T16:00:00\",
  \"endTime\":\"${TEST_DATE}T15:00:00\",
  \"meetingTitle\":\"InvalidTime\",
  \"contactPhone\":\"#123\", \"department\":\"TestDept\", \"attendees\":5
}"
S=$(call_api POST "/api/reservations" "$PAYLOAD_BAD_TIME" "$USER_TOKEN")
assert_in "RESERVE_INVALID_TIME" "$S" "400"

# (h) 沒帶 token
S=$(call_api POST "/api/reservations" "$PAYLOAD_OK" "")
assert_in "RESERVE_NO_TOKEN" "$S" "401" "403"

# ============================================================
# 4.2 退回機制
# ============================================================
section "4.2 · 退回機制 + 權限"

if [[ -z "${NEW_RES_ID:-}" ]]; then
  echo -e "  ${YELLOW}! 沒有可用的預約 ID，跳過 4.2 Test${NC}"
  ((SKIP+=3))
else
  # (a) 使用者申請退回
  RETURN_PAYLOAD="{
    \"reservationId\":$NEW_RES_ID,
    \"reviewerId\":$REVIEWER_ID,
    \"returnReason\":\"Postpone\"
  }"
  S=$(call_api POST "/api/reservations/return" "$RETURN_PAYLOAD" "$USER_TOKEN")
  assert_in "RETURN_REQUEST" "$S" "200" "201"
  assert_body_contains "RETURN_STATUS_PROCESSING" "PROCESSING\|processing"

  # (b) 一般使用者打 review 應該被擋
  REVIEW_PAYLOAD="{
    \"reservationId\":$NEW_RES_ID,
    \"approved\":true,
    \"remark\":\"sneak\"
  }"
  S=$(call_api POST "/api/reservations/review" "$REVIEW_PAYLOAD" "$USER_TOKEN")
  assert_in "REVIEW_BY_USER_FORBIDDEN" "$S" "401" "403"

  # (c) 審核者通過退回
  if [[ -n "$ADMIN_TOKEN" ]]; then
    S=$(call_api POST "/api/reservations/review" "$REVIEW_PAYLOAD" "$ADMIN_TOKEN")
    assert_in "REVIEW_APPROVE" "$S" "200" "201"
    assert_body_contains "REVIEW_STATUS_FINAL" "REJECTED\|APPROVED\|rejected\|approved"
  else
    echo -e "  ${YELLOW}- SKIP${NC} | REVIEW_APPROVE (沒有 admin token)"
    ((SKIP++))
  fi
fi

# ============================================================
# 4.3 當日 approved 列表（對應圖 C）
# ============================================================
section "4.3 · 當日 approved 預約列表"

S=$(call_api GET "/api/reservations/daily?date=$DAILY_QUERY_DATE" "" "$USER_TOKEN")
assert_in "DAILY_LIST_HTTP" "$S" "200"

# 檢查回傳是 array
if grep -q "^\[" "$BODY_FILE" || python -c "import json,sys; d=json.load(open('$BODY_FILE')); sys.exit(0 if isinstance(d,list) or 'data' in d else 1)" 2>/dev/null; then
  echo -e "  ${GREEN}✓ PASS${NC} | DAILY_RESPONSE_IS_LIST"
  ((PASS++))
else
  echo -e "  ${RED}✗ FAIL${NC} | DAILY_RESPONSE_IS_LIST (回傳不是 array)"
  ((FAIL++))
  FAILED_TESTS+=("DAILY_RESPONSE_IS_LIST")
fi

# 檢查只有 APPROVED 而且依時間排序
python <<PYEOF
import json,sys
try:
  d=json.load(open("$BODY_FILE"))
  arr = d if isinstance(d,list) else d.get("data",[])
  if not isinstance(arr, list):
    print("  \033[33m! WARN\033[0m | 無法解析為列表，跳過排序檢查")
    sys.exit(0)

  statuses = [str(x.get("status","")).upper() for x in arr]
  bad = [s for s in statuses if s and s != "APPROVED"]
  if bad:
    print(f"  \033[31m✗ FAIL\033[0m | DAILY_ONLY_APPROVED (出現非 APPROVED: {bad})")
    sys.exit(1)
  else:
    print(f"  \033[32m✓ PASS\033[0m | DAILY_ONLY_APPROVED ({len(arr)} 筆全為 APPROVED 或空)")

  times = [x.get("startTime") for x in arr if x.get("startTime")]
  if times == sorted(times):
    print(f"  \033[32m✓ PASS\033[0m | DAILY_SORTED_BY_TIME")
  else:
    print(f"  \033[31m✗ FAIL\033[0m | DAILY_SORTED_BY_TIME (沒依 startTime 升冪)")
    sys.exit(1)
except Exception as e:
  print(f"  \033[33m! WARN\033[0m | DAILY 解析失敗：{e}")
PYEOF

# ============================================================
# 4.4 月份分組統計
# ============================================================
section "4.4 · 月份預約分組 (PROCESSING / APPROVED / REJECTED)"

S=$(call_api GET "/api/reservations/monthly?year=$MONTHLY_YEAR&month=$MONTHLY_MONTH" "" "$USER_TOKEN")
assert_in "MONTHLY_HTTP" "$S" "200"
assert_body_contains "MONTHLY_HAS_APPROVED"   "APPROVED\|approved"
assert_body_contains "MONTHLY_HAS_PROCESSING" "PROCESSING\|processing"
assert_body_contains "MONTHLY_HAS_REJECTED"   "REJECTED\|rejected"

# ============================================================
# Summary
# ============================================================
TOTAL=$((PASS + FAIL))
section "Test結果摘要"
echo -e "  ${GREEN}PASS: $PASS${NC}"
echo -e "  ${RED}FAIL: $FAIL${NC}"
[[ $SKIP -gt 0 ]] && echo -e "  ${YELLOW}SKIP: $SKIP${NC}"
echo -e "  總計: $TOTAL"

if (( FAIL > 0 )); then
  echo ""
  echo -e "${RED}失敗清單：${NC}"
  for t in "${FAILED_TESTS[@]}"; do
    echo "    - $t"
  done
  echo ""
  echo -e "${YELLOW}建議：${NC}"
  echo "  1. 查看 app log：docker-compose logs app --tail=200"
  echo "  2. 直接查 DB：docker-compose exec postgres psql -U postgres -d meetingroom"
  echo "  3. 把本腳本完整輸出貼給 Claude CLI，依 CLAUDE_TEST_TASK.md Phase 3 處理"
  exit 1
fi

echo -e "${GREEN}✓ 全部通過${NC}"
exit 0
