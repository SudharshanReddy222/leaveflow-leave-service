package com.leaveflow.leave.exception;

public class SelfApprovalNotAllowedException extends RuntimeException {

    public SelfApprovalNotAllowedException(Long employeeId) {
        super("Employee " + employeeId + " cannot approve their own leave request");
    }
}
