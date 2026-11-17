package com.leaveflow.leave.exception;

public class LeaveRequestNotFoundException extends RuntimeException {

    public LeaveRequestNotFoundException(Long leaveRequestId) {
        super("Leave request " + leaveRequestId + " was not found");
    }
}
