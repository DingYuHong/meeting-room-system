package com.example.meetingroom.dto;

import com.example.meetingroom.entity.Reservation;
import com.example.meetingroom.enums.ReservationStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 【DTO】ReservationResponse - API 回傳的預約資料
 *
 * 將 Reservation Entity 轉換為適合前端顯示的格式，
 * 避免序列化時觸發 Lazy Loading 導致的問題。
 */
@Data
public class ReservationResponse {

    private Long id;
    private String reservationNo;
    private String roomName;
    private Integer roomCapacity;
    private String userName;
    private String userDepartment;
    private String userPhone;
    private String reviewerName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private ReservationStatus status;
    private String meetingTitle;
    private Integer attendees;
    private String returnReason;
    private String remark;
    private LocalDateTime createdAt;

    /**
     * 靜態工廠方法：將 Entity 轉為 Response DTO
     * 這樣 Controller/Service 呼叫 ReservationResponse.from(reservation) 就能轉換
     */
    public static ReservationResponse from(Reservation r) {
        ReservationResponse dto = new ReservationResponse();
        dto.setId(r.getId());
        dto.setReservationNo(r.getReservationNo());
        dto.setRoomName(r.getRoom().getName());
        dto.setRoomCapacity(r.getRoom().getCapacity());
        dto.setUserName(r.getUser().getUsername());
        dto.setUserDepartment(r.getUser().getDepartment());
        dto.setUserPhone(r.getUser().getPhone());
        if (r.getReviewer() != null) {
            dto.setReviewerName(r.getReviewer().getUsername());
        }
        dto.setStartTime(r.getStartTime());
        dto.setEndTime(r.getEndTime());
        dto.setStatus(r.getStatus());
        dto.setMeetingTitle(r.getMeetingTitle());
        dto.setAttendees(r.getAttendees());
        dto.setReturnReason(r.getReturnReason());
        dto.setRemark(r.getRemark());
        dto.setCreatedAt(r.getCreatedAt());
        return dto;
    }
}
