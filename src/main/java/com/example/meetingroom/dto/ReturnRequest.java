package com.example.meetingroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReturnRequest {

    @NotNull(message = "預約 ID 不能為空")
    private Long reservationId;

    @NotNull(message = "審核者 ID 不能為空")
    private Long reviewerId;

    @NotBlank(message = "退回原因不能為空")
    @Size(max = 500, message = "退回原因最多 500 個字元")
    private String returnReason;
}
