package com.leaveflow.leave.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Check(name = "ck_employee_leave_guard_employee_id", constraints = "employee_id > 0")
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
