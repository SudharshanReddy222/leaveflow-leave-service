package com.leaveflow.leave.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "employee_leave_guard")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeLeaveGuard {

    @Id
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    public EmployeeLeaveGuard(Long employeeId) {
        this.employeeId = employeeId;
    }
}
