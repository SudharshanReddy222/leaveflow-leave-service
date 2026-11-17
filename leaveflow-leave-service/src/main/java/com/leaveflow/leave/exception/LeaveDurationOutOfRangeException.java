package com.leaveflow.leave.exception;

public class LeaveDurationOutOfRangeException extends RuntimeException {

    public LeaveDurationOutOfRangeException() {
        super("Leave duration exceeds the supported integer range");
    }
}
