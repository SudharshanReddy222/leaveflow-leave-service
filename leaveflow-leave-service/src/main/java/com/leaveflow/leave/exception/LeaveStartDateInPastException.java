package com.leaveflow.leave.exception;

import java.time.LocalDate;

public class LeaveStartDateInPastException extends RuntimeException {

    public LeaveStartDateInPastException(LocalDate startDate) {
        super("Leave start date cannot be in the past: " + startDate);
    }
}
