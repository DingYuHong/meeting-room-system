package com.example.meetingroom.controller;

import com.example.meetingroom.dto.*;
import com.example.meetingroom.entity.Reservation;
import com.example.meetingroom.service.MeetingRoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 【Controller】ReservationController - REST API 入口
 *
 * URL 前綴：/api/reservations
 *
 * API 清單：
 *   POST   /api/reservations            - 4.1 建立預約
 *   GET    /api/reservations/room/{id}  - 查詢會議室的所有預約
 *   POST   /api/reservations/return     - 4.2 申請退回
 *   POST   /api/reservations/review     - 4.2 審核退回
 *   GET    /api/reservations/daily      - 4.3 查詢當日 APPROVED 預約
 *   GET    /api/reservations/monthly    - 4.4 查詢當月各狀態預約
 */
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final MeetingRoomService meetingRoomService;

    // ─────────────────────────────────────────────
    // 4.1 建立預約
    // POST /api/reservations
    // Body: { roomId, userId, startTime, endTime, meetingTitle, attendees, remark }
    // ─────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<?> createReservation(@Valid @RequestBody ReservationRequest request) {
        try {
            ReservationResponse response = meetingRoomService.createReservation(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────
    // 查詢特定會議室的所有預約
    // GET /api/reservations/room/{roomId}
    // ─────────────────────────────────────────────
    @GetMapping("/room/{roomId}")
    public ResponseEntity<?> getByRoom(@PathVariable Long roomId) {
        List<Reservation> reservations = meetingRoomService.getReservationsByRoomId(roomId);
        List<ReservationResponse> responses = reservations.stream()
                .map(ReservationResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    // ─────────────────────────────────────────────
    // 4.2 使用者申請退回
    // POST /api/reservations/return
    // Body: { reservationId, reviewerId, returnReason }
    // ─────────────────────────────────────────────
    @PostMapping("/return")
    public ResponseEntity<?> applyReturn(@Valid @RequestBody ReturnRequest request) {
        try {
            ReservationResponse response = meetingRoomService.applyReturn(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────
    // 4.2 審核者審核退回（僅限 REVIEWER 角色）
    // POST /api/reservations/review
    // Body: { reservationId, approved, remark }
    // ─────────────────────────────────────────────
    @PreAuthorize("hasRole('REVIEWER')")
    @PostMapping("/review")
    public ResponseEntity<?> reviewReturn(@Valid @RequestBody ReviewRequest request) {
        try {
            ReservationResponse response = meetingRoomService.reviewReturn(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────
    // 4.3 查詢某日 APPROVED 預約（對應圖C）
    // GET /api/reservations/daily?date=2026-01-04
    // ─────────────────────────────────────────────
    @GetMapping("/daily")
    public ResponseEntity<?> getDailyApproved(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<ReservationResponse> responses = meetingRoomService.getApprovedReservationsByDate(date);
        return ResponseEntity.ok(responses);
    }

    // ─────────────────────────────────────────────
    // 4.4 查詢每月各狀態預約狀況
    // GET /api/reservations/monthly?year=2026&month=1
    // 回傳：{ "APPROVED": [...], "PROCESSING": [...], "RETURN_APPROVED": [...], "RETURN_REJECTED": [...] }
    // ─────────────────────────────────────────────
    @GetMapping("/monthly")
    public ResponseEntity<?> getMonthlyStatus(
            @RequestParam int year,
            @RequestParam int month) {
        Map<String, List<ReservationResponse>> result =
                meetingRoomService.getMonthlyReservationsByStatus(year, month);
        return ResponseEntity.ok(result);
    }
}
