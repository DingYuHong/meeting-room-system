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
     * 4.1 作業要求方法簽名：reserveRoom
     *
     * 核心預約邏輯：時間驗證、容量檢查、衝突檢查、儲存。
     * 使用悲觀寫鎖（FOR UPDATE）防止並發競態。
     * createReservation(DTO) 內部直接委派給此方法，避免邏輯重複。
     */
    @Transactional
    public Reservation reserveRoom(Long roomId, Long userId,
                                   LocalDateTime start, LocalDateTime end,
                                   Integer attendees,
                                   String meetingTitle, String remark) {
        // 1. 悲觀寫鎖取得會議室
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RuntimeException("找不到會議室 ID: " + roomId));

        if (!room.getActive()) {
            throw new IllegalArgumentException("會議室 [" + room.getName() + "] 已停用，無法預約");
        }

        // 2. 取得使用者
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("找不到使用者 ID: " + userId));

        // 3. 時間驗證
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("結束時間必須晚於開始時間");
        }

        // 4. 容量驗證
        if (attendees != null && attendees > room.getCapacity()) {
            throw new IllegalArgumentException(
                String.format("預約人數 %d 超過會議室 [%s] 容量上限 %d 人",
                    attendees, room.getName(), room.getCapacity())
            );
        }

        // 5. 時間衝突檢查（在持有 Room 鎖的情況下執行，保證原子性）
        List<Reservation> conflicts = reservationRepository
                .findConflictingReservations(roomId, start, end);
        if (!conflicts.isEmpty()) {
            throw new RuntimeException(
                String.format("會議室 [%s] 在 %s ~ %s 時段已被預約",
                    room.getName(), start, end)
            );
        }

        // 6. 建立預約並儲存（先儲存以取得 DB 自動產生的 ID）
        Reservation reservation = new Reservation();
        reservation.setRoom(room);
        reservation.setUser(user);
        reservation.setStartTime(start);
        reservation.setEndTime(end);
        reservation.setAttendees(attendees);
        reservation.setMeetingTitle(meetingTitle);
        reservation.setRemark(remark);
        reservation.setStatus(ReservationStatus.APPROVED);

        // 7. 用 DB 自動遞增 ID 生成預約編號，確保唯一性
        Reservation saved = reservationRepository.save(reservation);
        saved.setReservationNo(String.format("MTGRES%05d", saved.getId()));
        return reservationRepository.save(saved);
    }

    /**
     * 建立預約（完整 DTO 版本，由 REST Controller 呼叫）。
     * 直接委派給 reserveRoom，保證行為一致、避免邏輯重複。
     */
    @Transactional
    public ReservationResponse createReservation(ReservationRequest request) {
        Reservation saved = reserveRoom(
            request.getRoomId(),
            request.getUserId(),
            request.getStartTime(),
            request.getEndTime(),
            request.getAttendees(),
            request.getMeetingTitle(),
            request.getRemark()
        );
        return ReservationResponse.from(saved);
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
     * approved = true  → 同意取消 → RETURN_APPROVED（預約被取消）
     * approved = false → 駁回退回 → RETURN_REJECTED（預約維持有效，但保留 audit 紀錄）
     */
    @Transactional
    public ReservationResponse reviewReturn(ReviewRequest request) {
        Reservation reservation = reservationRepository.findById(request.getReservationId())
                .orElseThrow(() -> new RuntimeException("找不到預約記錄"));

        if (reservation.getStatus() != ReservationStatus.PROCESSING) {
            throw new IllegalStateException("只有 PROCESSING 狀態的預約才能審核");
        }

        if (request.getApproved()) {
            reservation.setStatus(ReservationStatus.RETURN_APPROVED);
        } else {
            reservation.setStatus(ReservationStatus.RETURN_REJECTED);
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
