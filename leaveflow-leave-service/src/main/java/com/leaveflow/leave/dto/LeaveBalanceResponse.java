package com.leaveflow.leave.dto;

import com.leaveflow.leave.entity.LeaveType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LeaveBalanceResponse {

    private Long employeeId;
    private LeaveType leaveType;
    private Integer totalDays;
    private Integer usedDays;
    private Integer availableDays;
}
