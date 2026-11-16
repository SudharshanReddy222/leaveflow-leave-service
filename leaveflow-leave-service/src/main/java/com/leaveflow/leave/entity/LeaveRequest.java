package com.leaveflow.leave.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Checks;
import org.hibernate.annotations.ColumnDefault;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Checks({
        @Check(
                name = "ck_leave_requests_leave_type",
                constraints = "leave_type IN ('CASUAL', 'SICK', 'EARNED')"),
        @Check(
                name = "ck_leave_requests_status",
                constraints = "status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')"),
        @Check(name = "ck_leave_requests_employee_id", constraints = "employee_id > 0"),
        @Check(
                name = "ck_leave_requests_processed_by",
                constraints = "processed_by IS NULL OR processed_by > 0"),
        @Check(name = "ck_leave_requests_dates", constraints = "end_date >= start_date"),
        @Check(name = "ck_leave_requests_number_of_days", constraints = "number_of_days > 0")
})
@Table(
        name = "leave_requests",
        indexes = {
                @Index(name = "ix_leave_requests_employee_id", columnList = "employee_id"),
                @Index(name = "ix_leave_requests_status", columnList = "status"),
                @Index(
                        name = "ix_leave_requests_overlap",
                        columnList = "employee_id, status, start_date, end_date")
        })
@Getter
@Setter
@NoArgsConstructor
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 20)
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "number_of_days", nullable = false)
    private Integer numberOfDays;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @ColumnDefault("'PENDING'")
    private LeaveStatus status = LeaveStatus.PENDING;

    @Column(name = "processed_by")
    private Long processedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    public LeaveRequest(
            Long employeeId,
            LeaveType leaveType,
            LocalDate startDate,
            LocalDate endDate,
            Integer numberOfDays,
            String reason) {
        this.employeeId = employeeId;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.numberOfDays = numberOfDays;
        this.reason = reason;
    }

    @PrePersist
    void setCreationTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}
