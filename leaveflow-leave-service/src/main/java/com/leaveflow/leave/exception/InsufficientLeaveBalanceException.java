package com.leaveflow.leave.exception;

import com.leaveflow.leave.entity.LeaveType;

public class InsufficientLeaveBalanceException extends RuntimeException {

    public InsufficientLeaveBalanceException(
            Long employeeId, LeaveType leaveType, int requestedDays, int availableDays) {
        super("Employee " + employeeId + " has " + availableDays + " available "
                + leaveType + " leave days but " + requestedDays + " are required");
    }
}
