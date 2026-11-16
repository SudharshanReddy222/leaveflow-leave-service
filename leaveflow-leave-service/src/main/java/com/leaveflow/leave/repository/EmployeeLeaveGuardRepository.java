package com.leaveflow.leave.repository;

import com.leaveflow.leave.entity.EmployeeLeaveGuard;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeLeaveGuardRepository extends JpaRepository<EmployeeLeaveGuard, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select guard from EmployeeLeaveGuard guard where guard.employeeId = :employeeId")
    Optional<EmployeeLeaveGuard> findByEmployeeIdForUpdate(@Param("employeeId") Long employeeId);
}
