package com.example.meetingroom.repository;

import com.example.meetingroom.entity.Reservation;
import com.example.meetingroom.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 【Repository】ReservationRepository
 *
 * 除了基本 CRUD，還定義了自訂 JPQL 查詢：
 *
 * 1. findConflictingReservations  - 檢查時間衝突（防止雙重預約）
 * 2. findApprovedByDate           - 查詢特定日期 APPROVED 的預約（圖C用）
 * 3. findByStatusAndMonth         - 依狀態和月份查詢（4.4 功能）
 * 4. findByRoomId                 - 依會議室查所有預約
 */
@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // ─────────────────────────────────────────────
    // 4.1 防止雙重預約：找出同一會議室在指定時段內已存在的 APPROVED 預約
    //
    // 時間衝突條件（兩段時間重疊的判斷）：
    //   新預約的開始 < 既有預約的結束  AND  新預約的結束 > 既有預約的開始
    // ─────────────────────────────────────────────
    @Query("""
        SELECT r FROM Reservation r
        WHERE r.room.id = :roomId
          AND r.status = 'APPROVED'
          AND r.startTime < :endTime
          AND r.endTime > :startTime
    """)
    List<Reservation> findConflictingReservations(
            @Param("roomId") Long roomId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    // ─────────────────────────────────────────────
    // 4.3 圖C：找出某一天所有 APPROVED 的預約，依開始時間排序
    //   JOIN FETCH 是為了避免 N+1 問題，一次把 room 和 user 都撈出來
    // ─────────────────────────────────────────────
    @Query("""
        SELECT r FROM Reservation r
        JOIN FETCH r.room
        JOIN FETCH r.user
        WHERE r.status = 'APPROVED'
          AND r.startTime >= :startOfDay
          AND r.startTime < :endOfDay
        ORDER BY r.startTime ASC, r.room.name ASC
    """)
    List<Reservation> findApprovedByDate(
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    // ─────────────────────────────────────────────
    // 4.4 依狀態和月份（年/月）查詢所有預約
    //   YEAR() / MONTH() 是 JPQL 的日期函數
    // ─────────────────────────────────────────────
    @Query("""
        SELECT r FROM Reservation r
        JOIN FETCH r.room
        JOIN FETCH r.user
        WHERE r.status = :status
          AND YEAR(r.startTime) = :year
          AND MONTH(r.startTime) = :month
        ORDER BY r.startTime ASC
    """)
    List<Reservation> findByStatusAndMonth(
            @Param("status") ReservationStatus status,
            @Param("year") int year,
            @Param("month") int month
    );

    // ─────────────────────────────────────────────
    // 依會議室 ID 查詢所有預約（含 JOIN，一次抓取使用者資料）
    // ─────────────────────────────────────────────
    @Query("""
        SELECT r FROM Reservation r
        JOIN FETCH r.room
        JOIN FETCH r.user
        WHERE r.room.id = :roomId
        ORDER BY r.startTime DESC
    """)
    List<Reservation> findByRoomId(@Param("roomId") Long roomId);

    // ─────────────────────────────────────────────
    // 4.4 查詢某月份所有狀態的預約（不限狀態）
    // ─────────────────────────────────────────────
    @Query("""
        SELECT r FROM Reservation r
        JOIN FETCH r.room
        JOIN FETCH r.user
        WHERE YEAR(r.startTime) = :year
          AND MONTH(r.startTime) = :month
        ORDER BY r.status, r.startTime ASC
    """)
    List<Reservation> findAllByMonth(
            @Param("year") int year,
            @Param("month") int month
    );
}
