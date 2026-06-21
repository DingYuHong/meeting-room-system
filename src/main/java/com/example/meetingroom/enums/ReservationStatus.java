package com.example.meetingroom.enums;

/**
 * 預約狀態 State Machine。
 *
 * <pre>
 * 新預約
 *   └─→ APPROVED （預約有效）
 *
 * APPROVED ─使用者申請退回─→ PROCESSING （退回申請審核中）
 *                              │
 *                              ├─審核者同意取消─→ RETURN_APPROVED （預約已取消）
 *                              │
 *                              └─審核者駁回退回─→ RETURN_REJECTED （預約維持，但保留 audit）
 * </pre>
 *
 * 設計說明：
 * - 不存在「衝突被拒」的 status，因為時段衝突會直接拋 exception 回 HTTP 400，
 *   失敗的請求不會寫入 reservation 表。
 * - RETURN_APPROVED / RETURN_REJECTED 都是終態。
 * - 沿用 PDF 要求的 PROCESSING / APPROVED 兩個原始值，並以 RETURN_ 前綴
 *   明確標示退回流程的兩種結果，避免 APPROVED 與 REJECTED 一字多義的問題。
 */
public enum ReservationStatus {
    APPROVED,
    PROCESSING,
    RETURN_APPROVED,
    RETURN_REJECTED
}