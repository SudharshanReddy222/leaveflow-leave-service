package com.leaveflow.leave.repository;

import com.leaveflow.leave.entity.LeaveRequest;
import com.leaveflow.leave.entity.LeaveStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findAllByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    @Query("select request.employeeId from LeaveRequest request where request.id = :leaveRequestId")
    Optional<Long> findEmployeeIdById(@Param("leaveRequestId") Long leaveRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from LeaveRequest request where request.id = :leaveRequestId")
    Optional<LeaveRequest> findByIdForUpdate(@Param("leaveRequestId") Long leaveRequestId);

    @Query("""
            select case when count(request) > 0 then true else false end
            from LeaveRequest request
            where request.employeeId = :employeeId
              and request.status in :statuses
              and request.startDate <= :requestedEndDate
              and request.endDate >= :requestedStartDate
            """)
    boolean existsOverlappingRequest(
            @Param("employeeId") Long employeeId,
            @Param("statuses") Collection<LeaveStatus> statuses,
            @Param("requestedStartDate") LocalDate requestedStartDate,
            @Param("requestedEndDate") LocalDate requestedEndDate);
}
