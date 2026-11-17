package com.leaveflow.leave.dto;

import com.leaveflow.leave.entity.LeaveStatus;
import com.leaveflow.leave.entity.LeaveType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LeaveResponse {

    private Long id;
    private Long employeeId;
    private LeaveType leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer numberOfDays;
    private String reason;
    private LeaveStatus status;
    private Long processedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
