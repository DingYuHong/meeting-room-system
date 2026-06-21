package com.example.meetingroom.service;

import com.example.meetingroom.entity.Reservation;
import com.example.meetingroom.entity.Room;
import com.example.meetingroom.entity.User;
import com.example.meetingroom.enums.ReservationStatus;
import com.example.meetingroom.repository.ReservationRepository;
import com.example.meetingroom.repository.RoomRepository;
import com.example.meetingroom.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 【資料初始化】DataInitializer
 *
 * 實作 CommandLineRunner → Spring Boot 啟動後自動執行 run() 方法
 *
 * 作業要求：「會議室 和 使用者 自行塞假資料」
 * 這裡根據截圖中看到的資料來建立假資料。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // 只在資料庫為空時才初始化（避免重複執行）
        if (roomRepository.count() > 0) {
            log.info("資料已存在，跳過初始化");
            return;
        }

        log.info("開始初始化假資料...");
        initRooms();
        initUsers();
        initReservations();
        log.info("假資料初始化完成！");
    }

    // ─── 初始化會議室 ───────────────────────────────
    private void initRooms() {
        List<Room> rooms = List.of(
            createRoom("會議室0103", 20, "1F"),
            createRoom("會議室0104", 15, "1F"),
            createRoom("會議室0105", 10, "1F"),
            createRoom("會議室0106", 10, "1F"),
            createRoom("會議室0107", 8,  "1F"),
            createRoom("會議室0108", 12, "1F"),
            createRoom("會議室0109", 8,  "1F"),
            createRoom("會議室0110", 6,  "1F"),
            createRoom("會議室0304", 15, "3F"),
            createRoom("會議室0310", 15, "3F"),
            createRoom("會議室0323", 20, "3F"),
            createRoom("會議室0601", 30, "6F"),
            createRoom("會議室0602", 30, "6F"),
            createRoom("會議室0603", 25, "6F"),
            createRoom("會議室0609", 15, "6F"),
            createRoom("會議室0704", 10, "7F")
        );
        roomRepository.saveAll(rooms);
        log.info("✓ 已建立 {} 間會議室", rooms.size());
    }

    // ─── 初始化使用者 ───────────────────────────────
    private void initUsers() {
        // 測試用初始密碼：一般使用者 "user123"，審核者 "admin123"
        String userPwd  = passwordEncoder.encode("user123");
        String adminPwd = passwordEncoder.encode("admin123");

        List<User> users = List.of(
            createUser("黃維善", "huang@example.com", "10010001", "#123", "資訊室", "USER",     userPwd),
            createUser("梁忠善", "liang@example.com", "10010001", "#123", "總務部", "USER",     userPwd),
            createUser("張曉彤", "zhang@example.com", "10010002", "#456", "會計部", "USER",     userPwd),
            createUser("陳俊彥", "chen@example.com",  "10010003", "#789", "總務部", "USER",     userPwd),
            createUser("陳優凱", "chen2@example.com", "10010004", "#321", "資訊部", "USER",     userPwd),
            createUser("許析元", "xu@example.com",    "10010005", "#654", "總務部", "USER",     userPwd),
            createUser("曾慶凱", "zeng@example.com",  "10010006", "#987", "人資部", "USER",     userPwd),
            createUser("管理者", "admin@example.com", "90000001", "#100", "管理部", "REVIEWER", adminPwd)
        );
        userRepository.saveAll(users);
        log.info("✓ 已建立 {} 位使用者", users.size());
    }

    // ─── 初始化預約資料 ─────────────────────────────
    private void initReservations() {
        // 取得 Room 和 User（依名稱查詢）
        Room room0304 = roomRepository.findByName("會議室0304").orElseThrow();
        Room room0310 = roomRepository.findByName("會議室0310").orElseThrow();
        Room room0323 = roomRepository.findByName("會議室0323").orElseThrow();

        User user1 = userRepository.findById(1L).orElseThrow();
        User user2 = userRepository.findById(2L).orElseThrow();
        User user3 = userRepository.findById(3L).orElseThrow();
        User reviewer = userRepository.findByEmail("admin@example.com").orElseThrow();

        List<Reservation> reservations = List.of(
            // 會議室0304 的預約（2026-01-20）
            createReservation("MTGRES0090", room0310, user1,
                "2026-01-19T10:00", "2026-01-19T11:00", ReservationStatus.APPROVED, "客戶訪談", 5),
            createReservation("MTGRES0091", room0310, user3,
                "2026-01-19T11:00", "2026-01-19T12:00", ReservationStatus.APPROVED, "部門例會", 5),
            createReservation("MTGRES0092", room0310, user2,
                "2026-01-19T13:00", "2026-01-19T15:00", ReservationStatus.APPROVED, "客戶訪談", 5),
            createReservation("MTGRES0093", room0310, user1,
                "2026-01-19T15:00", "2026-01-19T16:00", ReservationStatus.APPROVED, "客戶訪談", 5),
            createReservation("MTGRES0094", room0310, user3,
                "2026-01-19T16:00", "2026-01-19T17:00", ReservationStatus.APPROVED, "客戶訪談", 5),
            createReservation("MTGRES0095", room0304, user2,
                "2026-01-20T10:00", "2026-01-20T11:00", ReservationStatus.APPROVED, "客戶訪談", 5),
            createReservation("MTGRES0096", room0304, user1,
                "2026-01-20T11:00", "2026-01-20T12:00", ReservationStatus.APPROVED, "部門例會", 5),
            createReservation("MTGRES0097", room0304, user3,
                "2026-01-20T13:00", "2026-01-20T15:00", ReservationStatus.APPROVED, "客戶訪談", 5),
            createReservation("MTGRES0098", room0304, user2,
                "2026-01-20T15:00", "2026-01-20T16:00", ReservationStatus.APPROVED, "客戶訪談", 5),
            createReservation("MTGRES0099", room0304, user1,
                "2026-01-20T16:00", "2026-01-20T17:00", ReservationStatus.APPROVED, "客戶訪談", 5),

            // PROCESSING 狀態（退回審核中）
            createReservationWithReturn("MTGRES0088", room0323, user2,
                "2026-01-20T10:00", "2026-01-20T12:00",
                ReservationStatus.PROCESSING, reviewer, "管理者福請", 8),
            // RETURN_APPROVED 狀態（同意退回 → 預約已取消）
            createReservationWithReturn("MTGRES0089", room0323, user3,
                "2026-01-20T13:00", "2026-01-20T14:00",
                ReservationStatus.RETURN_APPROVED, reviewer, "時段重複申請", 6),
            // RETURN_REJECTED 狀態（駁回退回 → 預約維持，保留 audit）
            createReservationWithReturn("MTGRES0100", room0323, user2,
                "2026-01-20T15:00", "2026-01-20T16:00",
                ReservationStatus.RETURN_REJECTED, reviewer, "退回理由不充分", 4)
        );

        reservationRepository.saveAll(reservations);
        log.info("✓ 已建立 {} 筆預約資料", reservations.size());
    }

    // ─── 工廠方法 ───────────────────────────────────

    private Room createRoom(String name, int capacity, String location) {
        Room r = new Room();
        r.setName(name);
        r.setCapacity(capacity);
        r.setLocation(location);
        r.setActive(true);
        return r;
    }

    private User createUser(String username, String email, String empNo,
                            String phone, String dept, String role, String encodedPassword) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setEmployeeNo(empNo);
        u.setPhone(phone);
        u.setDepartment(dept);
        u.setRole(role);
        u.setPassword(encodedPassword);
        return u;
    }

    private Reservation createReservation(String no, Room room, User user,
                                           String start, String end,
                                           ReservationStatus status,
                                           String title, int attendees) {
        Reservation r = new Reservation();
        r.setReservationNo(no);
        r.setRoom(room);
        r.setUser(user);
        r.setStartTime(LocalDateTime.parse(start));
        r.setEndTime(LocalDateTime.parse(end));
        r.setStatus(status);
        r.setMeetingTitle(title);
        r.setAttendees(attendees);
        return r;
    }

    private Reservation createReservationWithReturn(String no, Room room, User user,
                                                     String start, String end,
                                                     ReservationStatus status,
                                                     User reviewer, String returnReason,
                                                     int attendees) {
        Reservation r = createReservation(no, room, user, start, end, status, "客戶訪談", attendees);
        r.setReviewer(reviewer);
        r.setReturnReason(returnReason);
        return r;
    }
}
