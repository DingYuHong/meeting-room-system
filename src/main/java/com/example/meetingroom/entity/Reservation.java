package com.example.meetingroom.entity;

import com.example.meetingroom.enums.ReservationStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 【Entity】Reservation - 預約記錄
 *
 * 對應資料庫的 reservation 表。
 * 欄位說明：
 *   id            - 主鍵，自動遞增
 *   reservationNo - 預約編號（如「MTGRES0099」）
 *   room          - 關聯的會議室（Foreign Key → room.id）
 *   user          - 預約人（Foreign Key → users.id）
 *   reviewer      - 審核者（Foreign Key → users.id，可為 null）
 *   startTime     - 開始時間
 *   endTime       - 結束時間
 *   status        - 狀態：APPROVED / PROCESSING / RETURN_APPROVED / RETURN_REJECTED
 *   meetingTitle  - 會議主旨
 *   attendees     - 使用人數
 *   returnReason  - 退回原因（退回時填寫）
 *   remark        - 備註
 *   createdAt     - 建立時間
 */
@Entity
@Table(name = "reservation")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 預約編號（系統產生，唯一）
    @Column(name = "reservation_no", unique = true, length = 20)
    private String reservationNo;

    // ★ Foreign Key：關聯到 Room（多個預約對應同一個會議室）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    // ★ Foreign Key：關聯到 User（預約人）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ★ Foreign Key：關聯到 User（審核者，可為 null）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    // 開始時間
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    // 結束時間
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    // 預約狀態（使用 enum 儲存為字串）
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.APPROVED;

    // 會議主旨
    @Column(name = "meeting_title", length = 200)
    private String meetingTitle;

    // 使用人數
    @Column(name = "attendees")
    private Integer attendees;

    // 退回原因（管理者填寫）
    @Column(name = "return_reason", length = 500)
    private String returnReason;

    // 備註
    @Column(length = 500)
    private String remark;

    // 建立時間（自動設定）
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
