package com.leaveflow.leave.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "leave_balances",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_leave_balances_employee_type",
                columnNames = {"employee_id", "leave_type"}),
        indexes = @Index(name = "ix_leave_balances_employee_id", columnList = "employee_id"))
@Getter
@Setter
@NoArgsConstructor
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 20)
    private LeaveType leaveType;

    @Column(name = "total_days", nullable = false)
    private Integer totalDays;

    @Column(name = "used_days", nullable = false)
    private Integer usedDays;

    @Column(name = "available_days", nullable = false)
    private Integer availableDays;

    public LeaveBalance(
            Long employeeId,
            LeaveType leaveType,
            Integer totalDays,
            Integer usedDays,
            Integer availableDays) {
        this.employeeId = employeeId;
        this.leaveType = leaveType;
        this.totalDays = totalDays;
        this.usedDays = usedDays;
        this.availableDays = availableDays;
    }
}
