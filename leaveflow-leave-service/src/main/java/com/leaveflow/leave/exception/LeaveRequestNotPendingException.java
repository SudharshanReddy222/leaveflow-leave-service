package com.leaveflow.leave.exception;

import com.leaveflow.leave.entity.LeaveStatus;

public class LeaveRequestNotPendingException extends RuntimeException {

    public LeaveRequestNotPendingException(Long leaveRequestId, LeaveStatus currentStatus) {
        super("Leave request " + leaveRequestId + " cannot be processed from status " + currentStatus);
    }
}
