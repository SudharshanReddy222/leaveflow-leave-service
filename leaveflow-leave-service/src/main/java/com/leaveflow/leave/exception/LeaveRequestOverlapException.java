package com.leaveflow.leave.exception;

public class LeaveRequestOverlapException extends RuntimeException {

    public LeaveRequestOverlapException(Long employeeId) {
        super("An active leave request already overlaps the requested dates for employee " + employeeId);
    }
}
