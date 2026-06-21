package com.example.meetingroom.service;

import com.example.meetingroom.dto.*;
import com.example.meetingroom.entity.Reservation;
import com.example.meetingroom.entity.Room;
import com.example.meetingroom.entity.User;
import com.example.meetingroom.enums.ReservationStatus;
import com.example.meetingroom.repository.ReservationRepository;
import com.example.meetingroom.repository.RoomRepository;
import com.example.meetingroom.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetingRoomService {

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    // ════════════════════════════════════════════════
    // 4.1 預約功能
    // ════════════════════════════════════════════════

    /**
     * 建立預約。
     *
     * 使用悲觀寫鎖（FOR UPDATE）鎖住 Room 列，確保同一間會議室
     * 的並發預約只有一筆能通過衝突檢查，防止 Race Condition。
     */
    @Transactional
    public ReservationResponse createReservation(ReservationRequest request) {
        // 1. 以悲觀寫鎖取得會議室，阻斷其他並發事務對同一間房間的預約
        Room room = roomRepository.findByIdForUpdate(request.getRoomId())
                .orElseThrow(() -> new RuntimeException("找不到會議室"));

        if (!room.getActive()) {
            throw new IllegalArgumentException("會議室 [" + room.getName() + "] 已停用，無法預約");
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("找不到使用者"));

        // 2. 時間驗證
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new IllegalArgumentException("結束時間必須晚於開始時間");
        }

        // 3. 人數限制
        if (request.getAttendees() != null && request.getAttendees() > room.getCapacity()) {
            throw new IllegalArgumentException(
                String.format("預約人數 %d 超過會議室 [%s] 容量上限 %d 人",
                    request.getAttendees(), room.getName(), room.getCapacity())
            );
        }

        // 4. 時間衝突檢查（在持有 Room 鎖的情況下執行，保證原子性）
        List<Reservation> conflicts = reservationRepository
                .findConflictingReservations(request.getRoomId(), request.getStartTime(), request.getEndTime());
        if (!conflicts.isEmpty()) {
            throw new RuntimeException(
                String.format("會議室 [%s] 在 %s ~ %s 時段已被預約",
                    room.getName(), request.getStartTime(), request.getEndTime())
            );
        }

        // 5. 建立預約並儲存（先儲存以取得 DB 自動產生的 ID）
        Reservation reservation = new Reservation();
        reservation.setRoom(room);
        reservation.setUser(user);
        reservation.setStartTime(request.getStartTime());
        reservation.setEndTime(request.getEndTime());
        reservation.setMeetingTitle(request.getMeetingTitle());
        reservation.setAttendees(request.getAttendees());
        reservation.setRemark(request.getRemark());
        reservation.setStatus(ReservationStatus.APPROVED);

        // 6. 用 DB 自動遞增 ID 生成預約編號，確保唯一性（避免 count() 並發重複問題）
        Reservation saved = reservationRepository.save(reservation);
        saved.setReservationNo(String.format("MTGRES%05d", saved.getId()));
        return ReservationResponse.from(reservationRepository.save(saved));
    }

    /**
     * 依會議室 ID 查詢所有預約
     */
    public List<Reservation> getReservationsByRoomId(Long roomId) {
        return reservationRepository.findByRoomId(roomId);
    }

    // ════════════════════════════════════════════════
    // 4.2 退回機制
    // ════════════════════════════════════════════════

    /**
     * 使用者申請退回預約。
     * 驗證審核者帳號確實具備 REVIEWER 角色。
     */
    @Transactional
    public ReservationResponse applyReturn(ReturnRequest request) {
        Reservation reservation = reservationRepository.findById(request.getReservationId())
                .orElseThrow(() -> new RuntimeException("找不到預約記錄"));

        if (reservation.getStatus() != ReservationStatus.APPROVED) {
            throw new IllegalStateException("只有 APPROVED 狀態的預約才能申請退回");
        }

        User reviewer = userRepository.findById(request.getReviewerId())
                .orElseThrow(() -> new RuntimeException("找不到審核者"));

        // 確認審核者身份
        if (!"REVIEWER".equals(reviewer.getRole())) {
            throw new IllegalArgumentException("指定的使用者 [" + reviewer.getUsername() + "] 沒有審核者權限");
        }

        reservation.setReviewer(reviewer);
        reservation.setReturnReason(request.getReturnReason());
        reservation.setStatus(ReservationStatus.PROCESSING);

        return ReservationResponse.from(reservationRepository.save(reservation));
    }

    /**
     * 審核者審核退回申請。
     *
     * approved = true  → 同意退回 → REJECTED（預約取消）
     * approved = false → 拒絕退回 → APPROVED（預約恢復）
     */
    @Transactional
    public ReservationResponse reviewReturn(ReviewRequest request) {
        Reservation reservation = reservationRepository.findById(request.getReservationId())
                .orElseThrow(() -> new RuntimeException("找不到預約記錄"));

        if (reservation.getStatus() != ReservationStatus.PROCESSING) {
            throw new IllegalStateException("只有 PROCESSING 狀態的預約才能審核");
        }

        if (request.getApproved()) {
            reservation.setStatus(ReservationStatus.REJECTED);
        } else {
            reservation.setStatus(ReservationStatus.APPROVED);
        }

        if (request.getRemark() != null) {
            reservation.setRemark(request.getRemark());
        }

        return ReservationResponse.from(reservationRepository.save(reservation));
    }

    // ════════════════════════════════════════════════
    // 4.3 查詢某日 APPROVED 預約
    // ════════════════════════════════════════════════

    public List<ReservationResponse> getApprovedReservationsByDate(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(23, 59, 59);

        return reservationRepository
                .findApprovedByDate(startOfDay, endOfDay)
                .stream()
                .map(ReservationResponse::from)
                .collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════
    // 4.4 依狀態查詢每月預約狀況
    // ════════════════════════════════════════════════

    public Map<String, List<ReservationResponse>> getMonthlyReservationsByStatus(int year, int month) {
        List<Reservation> all = reservationRepository.findAllByMonth(year, month);

        return all.stream()
                .map(ReservationResponse::from)
                .collect(Collectors.groupingBy(r -> r.getStatus().name()));
    }
}
