package com.example.meetingroom.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 【DTO】ReviewRequest - 審核者決定退回是否通過
 *
 * approved = true  → 同意退回 → status 改為 REJECTED（預約取消）
 * approved = false → 拒絕退回 → status 改回 APPROVED（預約繼續）
 */
@Data
public class ReviewRequest {

    @NotNull
    private Long reservationId;

    @NotNull
    private Boolean approved; // true=核准退回 false=拒絕退回

    private String remark;
}
