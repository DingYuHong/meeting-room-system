package com.example.meetingroom.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReservationRequest {

    @NotNull(message = "會議室 ID 不能為空")
    private Long roomId;

    @NotNull(message = "使用者 ID 不能為空")
    private Long userId;

    @NotNull(message = "開始時間不能為空")
    private LocalDateTime startTime;

    @NotNull(message = "結束時間不能為空")
    private LocalDateTime endTime;

    @Size(max = 200, message = "會議主旨最多 200 個字元")
    private String meetingTitle;

    @Min(value = 1, message = "使用人數至少 1 人")
    @Max(value = 1000, message = "使用人數不得超過 1000 人")
    private Integer attendees;

    @Size(max = 500, message = "備註最多 500 個字元")
    private String remark;
}
