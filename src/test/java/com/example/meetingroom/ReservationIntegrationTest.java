package com.example.meetingroom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Integration tests corresponding to the 19 assertions in functional-test.sh.
 * Execution order is enforced via @Order(N) to satisfy data dependencies.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
class ReservationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("meetingroom")
            .withUsername("admin")
            .withPassword("admin123");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // State shared across ordered tests
    String userToken;
    String adminToken;
    Long   newResId;
    String returnResponseBody;
    String reviewResponseBody;
    String dailyResponseBody;
    String monthlyResponseBody;

    // Seed data constants (matches DataInitializer insertion order)
    static final long   ROOM_ID       = 1L;   // room "0103", capacity 20
    static final int    ROOM_CAPACITY = 20;
    static final long   USER_ID       = 1L;   // huang@example.com, role USER
    static final long   REVIEWER_ID   = 8L;   // admin@example.com, role REVIEWER
    static final String TEST_DATE     = "2026-07-15";
    static final String DAILY_DATE    = "2026-01-20";
    static final int    MONTHLY_YEAR  = 2026;
    static final int    MONTHLY_MONTH = 1;

    // ── Setup: login once before all tests ────────────────────────────────────

    @BeforeAll
    void loginBothUsers() throws Exception {
        userToken  = obtainToken("huang@example.com", "user123");
        adminToken = obtainToken("admin@example.com",  "admin123");
    }

    private String obtainToken(String email, String password) throws Exception {
        String body = String.format(
                "{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andReturn();
        JsonNode node = objectMapper.readTree(r.getResponse().getContentAsString());
        if (node.has("token"))       return node.get("token").asText();
        if (node.has("accessToken")) return node.get("accessToken").asText();
        throw new IllegalStateException("No token field in login response: " + node);
    }

    private String buildPayload(String start, String end, String title, int attendees) {
        return String.format(
                "{\"roomId\":%d,\"userId\":%d,\"startTime\":\"%s\",\"endTime\":\"%s\"," +
                "\"meetingTitle\":\"%s\",\"attendees\":%d}",
                ROOM_ID, USER_ID, start, end, title, attendees);
    }

    // ── 4.1  Reservation + conflict checks ────────────────────────────────────

    @Test @Order(1)
    @DisplayName("RESERVE_OK - normal reservation returns 200/201")
    void testReserveOk() throws Exception {
        String payload = buildPayload(
                TEST_DATE + "T10:00:00", TEST_DATE + "T12:00:00", "FuncTest-Normal", 5);
        MvcResult r = mockMvc.perform(post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + userToken)
                .content(payload))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isIn(200, 201);
        newResId = objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test @Order(2)
    @DisplayName("RESERVE_EXACT_CONFLICT - identical slot returns 400/409")
    void testReserveExactConflict() throws Exception {
        String payload = buildPayload(
                TEST_DATE + "T10:00:00", TEST_DATE + "T12:00:00", "FuncTest-Normal", 5);
        MvcResult r = mockMvc.perform(post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + userToken)
                .content(payload))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isIn(400, 409);
    }

    @Test @Order(3)
    @DisplayName("RESERVE_OVERLAP_CONFLICT - overlapping slot 11:00-13:00 returns 400/409")
    void testReserveOverlapConflict() throws Exception {
        String payload = buildPayload(
                TEST_DATE + "T11:00:00", TEST_DATE + "T13:00:00", "OverlapTest", 5);
        MvcResult r = mockMvc.perform(post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + userToken)
                .content(payload))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isIn(400, 409);
    }

    @Test @Order(4)
    @DisplayName("RESERVE_CONTAIN_CONFLICT - containing slot 09:00-13:00 returns 400/409")
    void testReserveContainConflict() throws Exception {
        String payload = buildPayload(
                TEST_DATE + "T09:00:00", TEST_DATE + "T13:00:00", "ContainTest", 5);
        MvcResult r = mockMvc.perform(post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + userToken)
                .content(payload))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isIn(400, 409);
    }

    @Test @Order(5)
    @DisplayName("RESERVE_ADJACENT_OK - adjacent slot 12:00-13:00 must NOT be blocked")
    void testReserveAdjacentOk() throws Exception {
        String payload = buildPayload(
                TEST_DATE + "T12:00:00", TEST_DATE + "T13:00:00", "AdjacentSlot", 5);
        MvcResult r = mockMvc.perform(post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + userToken)
                .content(payload))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isIn(200, 201);
    }

    @Test @Order(6)
    @DisplayName("RESERVE_OVER_CAPACITY - attendees > room capacity returns 400")
    void testReserveOverCapacity() throws Exception {
        String payload = buildPayload(
                TEST_DATE + "T15:00:00", TEST_DATE + "T16:00:00",
                "OverCapacity", ROOM_CAPACITY + 100);
        MvcResult r = mockMvc.perform(post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + userToken)
                .content(payload))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }

    @Test @Order(7)
    @DisplayName("RESERVE_INVALID_TIME - endTime before startTime returns 400")
    void testReserveInvalidTime() throws Exception {
        String payload = buildPayload(
                TEST_DATE + "T16:00:00", TEST_DATE + "T15:00:00", "InvalidTime", 5);
        MvcResult r = mockMvc.perform(post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + userToken)
                .content(payload))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }

    @Test @Order(8)
    @DisplayName("RESERVE_NO_TOKEN - missing JWT returns 401/403")
    void testReserveNoToken() throws Exception {
        String payload = buildPayload(
                TEST_DATE + "T10:00:00", TEST_DATE + "T12:00:00", "NoToken", 5);
        MvcResult r = mockMvc.perform(post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isIn(401, 403);
    }

    // ── 4.2  Return mechanism + RBAC ───────────────────────────────────────────

    @Test @Order(9)
    @DisplayName("RETURN_REQUEST - user submits return request, expects 200/201")
    void testReturnRequest() throws Exception {
        assumeTrue(newResId != null, "Skipped: RESERVE_OK did not produce a reservation ID");
        String payload = String.format(
                "{\"reservationId\":%d,\"reviewerId\":%d,\"returnReason\":\"Postpone\"}",
                newResId, REVIEWER_ID);
        MvcResult r = mockMvc.perform(post("/api/reservations/return")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + userToken)
                .content(payload))
                .andReturn();
        returnResponseBody = r.getResponse().getContentAsString();
        assertThat(r.getResponse().getStatus()).isIn(200, 201);
    }

    @Test @Order(10)
    @DisplayName("RETURN_STATUS_PROCESSING - response body contains PROCESSING")
    void testReturnStatusProcessing() {
        assumeTrue(returnResponseBody != null, "Skipped: RETURN_REQUEST did not store a response");
        assertThat(returnResponseBody.toUpperCase()).contains("PROCESSING");
    }

    @Test @Order(11)
    @DisplayName("REVIEW_BY_USER_FORBIDDEN - regular user calling /review returns 401/403")
    void testReviewByUserForbidden() throws Exception {
        assumeTrue(newResId != null, "Skipped: no reservation ID available");
        String payload = String.format(
                "{\"reservationId\":%d,\"approved\":true,\"remark\":\"sneak\"}",
                newResId);
        MvcResult r = mockMvc.perform(post("/api/reservations/review")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + userToken)
                .content(payload))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isIn(401, 403);
    }

    @Test @Order(12)
    @DisplayName("REVIEW_APPROVE - reviewer approves return, expects 200/201")
    void testReviewApprove() throws Exception {
        assumeTrue(newResId != null, "Skipped: no reservation ID available");
        String payload = String.format(
                "{\"reservationId\":%d,\"approved\":true,\"remark\":\"Approved\"}",
                newResId);
        MvcResult r = mockMvc.perform(post("/api/reservations/review")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + adminToken)
                .content(payload))
                .andReturn();
        reviewResponseBody = r.getResponse().getContentAsString();
        assertThat(r.getResponse().getStatus()).isIn(200, 201);
    }

    @Test @Order(13)
    @DisplayName("REVIEW_STATUS_FINAL - response body contains REJECTED or APPROVED")
    void testReviewStatusFinal() {
        assumeTrue(reviewResponseBody != null, "Skipped: REVIEW_APPROVE did not store a response");
        String upper = reviewResponseBody.toUpperCase();
        assertThat(upper.contains("REJECTED") || upper.contains("APPROVED")).isTrue();
    }

    // ── 4.3  Daily approved list ───────────────────────────────────────────────

    @Test @Order(14)
    @DisplayName("DAILY_LIST_HTTP - GET /daily?date= returns 200")
    void testDailyListHttp() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/reservations/daily")
                .param("date", DAILY_DATE)
                .header("Authorization", "Bearer " + userToken))
                .andReturn();
        dailyResponseBody = r.getResponse().getContentAsString();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
    }

    @Test @Order(15)
    @DisplayName("DAILY_RESPONSE_IS_LIST - daily response is a JSON array")
    void testDailyResponseIsList() throws Exception {
        assumeTrue(dailyResponseBody != null, "Skipped: DAILY_LIST_HTTP did not store a response");
        JsonNode node = objectMapper.readTree(dailyResponseBody);
        assertThat(node.isArray()).isTrue();
    }

    // ── 4.4  Monthly grouped by status ────────────────────────────────────────

    @Test @Order(16)
    @DisplayName("MONTHLY_HTTP - GET /monthly?year=&month= returns 200")
    void testMonthlyHttp() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/reservations/monthly")
                .param("year",  String.valueOf(MONTHLY_YEAR))
                .param("month", String.valueOf(MONTHLY_MONTH))
                .header("Authorization", "Bearer " + userToken))
                .andReturn();
        monthlyResponseBody = r.getResponse().getContentAsString();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
    }

    @Test @Order(17)
    @DisplayName("MONTHLY_HAS_APPROVED - monthly response contains APPROVED")
    void testMonthlyHasApproved() {
        assumeTrue(monthlyResponseBody != null, "Skipped: MONTHLY_HTTP did not store a response");
        assertThat(monthlyResponseBody.toUpperCase()).contains("APPROVED");
    }

    @Test @Order(18)
    @DisplayName("MONTHLY_HAS_PROCESSING - monthly response contains PROCESSING")
    void testMonthlyHasProcessing() {
        assumeTrue(monthlyResponseBody != null, "Skipped: MONTHLY_HTTP did not store a response");
        assertThat(monthlyResponseBody.toUpperCase()).contains("PROCESSING");
    }

    @Test @Order(19)
    @DisplayName("MONTHLY_HAS_REJECTED - monthly response contains REJECTED")
    void testMonthlyHasRejected() {
        assumeTrue(monthlyResponseBody != null, "Skipped: MONTHLY_HTTP did not store a response");
        assertThat(monthlyResponseBody.toUpperCase()).contains("REJECTED");
    }
}
