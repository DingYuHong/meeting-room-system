package com.example.meetingroom.enums;

/**
 * 預約狀態：
 * PROCESSING - 審核中（申請退回後等待審核者決定）
 * APPROVED   - 已通過（預約成立 或 退回申請被核准）
 * REJECTED   - 已拒絕（退回申請被拒絕）
 */
public enum ReservationStatus {
    PROCESSING,
    APPROVED,
    REJECTED
}
