package com.leaveflow.leave.service;

import com.leaveflow.leave.dto.ApplyLeaveRequest;
import com.leaveflow.leave.dto.LeaveResponse;
import com.leaveflow.leave.entity.EmployeeLeaveGuard;
import com.leaveflow.leave.entity.LeaveBalance;
import com.leaveflow.leave.entity.LeaveRequest;
import com.leaveflow.leave.entity.LeaveStatus;
import com.leaveflow.leave.exception.InsufficientLeaveBalanceException;
import com.leaveflow.leave.exception.LeaveDurationOutOfRangeException;
import com.leaveflow.leave.exception.LeaveRequestNotFoundException;
import com.leaveflow.leave.exception.LeaveRequestNotPendingException;
import com.leaveflow.leave.exception.LeaveRequestOverlapException;
import com.leaveflow.leave.exception.LeaveStartDateInPastException;
import com.leaveflow.leave.exception.SelfApprovalNotAllowedException;
import com.leaveflow.leave.repository.EmployeeLeaveGuardRepository;
import com.leaveflow.leave.repository.LeaveBalanceRepository;
import com.leaveflow.leave.repository.LeaveRequestRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class LeaveService {

    private static final List<LeaveStatus> OVERLAPPING_STATUSES =
            List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeLeaveGuardRepository employeeLeaveGuardRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;

    @Transactional
    public LeaveResponse applyForLeave(
            @NotNull @Positive Long employeeId,
            @Valid @NotNull ApplyLeaveRequest request) {
        if (request.getStartDate().isBefore(LocalDate.now())) {
            throw new LeaveStartDateInPastException(request.getStartDate());
        }

        int numberOfDays = calculateInclusiveDuration(request.getStartDate(), request.getEndDate());

        employeeLeaveGuardRepository.insertIfAbsent(employeeId);
        EmployeeLeaveGuard guard = employeeLeaveGuardRepository.findByEmployeeIdForUpdate(employeeId)
                .orElseThrow(() -> new IllegalStateException(
                        "Employee leave guard row was not available after ensuring it exists"));

        if (leaveRequestRepository.existsOverlappingRequest(
                guard.getEmployeeId(),
                OVERLAPPING_STATUSES,
                request.getStartDate(),
                request.getEndDate())) {
            throw new LeaveRequestOverlapException(employeeId);
        }

        LeaveRequest leaveRequest = new LeaveRequest(
                employeeId,
                request.getLeaveType(),
                request.getStartDate(),
                request.getEndDate(),
                numberOfDays,
                request.getReason());

        return toResponse(leaveRequestRepository.saveAndFlush(leaveRequest));
    }

    @Transactional
    public LeaveResponse approveLeaveRequest(
            @NotNull @Positive Long leaveRequestId,
            @NotNull @Positive Long managerEmployeeId) {
        LeaveRequest leaveRequest = lockPendingRequest(leaveRequestId);
        if (leaveRequest.getEmployeeId().equals(managerEmployeeId)) {
            throw new SelfApprovalNotAllowedException(managerEmployeeId);
        }

        LeaveBalance balance = leaveBalanceRepository
                .findByEmployeeIdAndLeaveType(leaveRequest.getEmployeeId(), leaveRequest.getLeaveType())
                .orElseThrow(() -> new InsufficientLeaveBalanceException(
                        leaveRequest.getEmployeeId(),
                        leaveRequest.getLeaveType(),
                        leaveRequest.getNumberOfDays(),
                        0));
        if (balance.getAvailableDays() < leaveRequest.getNumberOfDays()) {
            throw new InsufficientLeaveBalanceException(
                    leaveRequest.getEmployeeId(),
                    leaveRequest.getLeaveType(),
                    leaveRequest.getNumberOfDays(),
                    balance.getAvailableDays());
        }

        balance.setUsedDays(Math.addExact(balance.getUsedDays(), leaveRequest.getNumberOfDays()));
        balance.setAvailableDays(balance.getAvailableDays() - leaveRequest.getNumberOfDays());
        leaveBalanceRepository.saveAndFlush(balance);

        leaveRequest.setStatus(LeaveStatus.APPROVED);
        leaveRequest.setProcessedBy(managerEmployeeId);
        return toResponse(leaveRequestRepository.saveAndFlush(leaveRequest));
    }

    @Transactional
    public LeaveResponse rejectLeaveRequest(
            @NotNull @Positive Long leaveRequestId,
            @NotNull @Positive Long managerEmployeeId) {
        LeaveRequest leaveRequest = lockPendingRequest(leaveRequestId);
        leaveRequest.setStatus(LeaveStatus.REJECTED);
        leaveRequest.setProcessedBy(managerEmployeeId);
        return toResponse(leaveRequestRepository.saveAndFlush(leaveRequest));
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> getAllLeaveRequests() {
        return leaveRequestRepository.findAll().stream()
                .map(LeaveService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LeaveResponse getLeaveRequestById(@NotNull @Positive Long leaveRequestId) {
        return leaveRequestRepository.findById(leaveRequestId)
                .map(LeaveService::toResponse)
                .orElseThrow(() -> new LeaveRequestNotFoundException(leaveRequestId));
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> getLeaveRequestsByEmployeeId(@NotNull @Positive Long employeeId) {
        return leaveRequestRepository.findAllByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .map(LeaveService::toResponse)
                .toList();
    }

    private static int calculateInclusiveDuration(LocalDate startDate, LocalDate endDate) {
        long duration = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (duration > Integer.MAX_VALUE) {
            throw new LeaveDurationOutOfRangeException();
        }
        return (int) duration;
    }

    private LeaveRequest lockPendingRequest(Long leaveRequestId) {
        Long employeeId = leaveRequestRepository.findEmployeeIdById(leaveRequestId)
                .orElseThrow(() -> new LeaveRequestNotFoundException(leaveRequestId));

        employeeLeaveGuardRepository.insertIfAbsent(employeeId);
        EmployeeLeaveGuard guard = employeeLeaveGuardRepository.findByEmployeeIdForUpdate(employeeId)
                .orElseThrow(() -> new IllegalStateException(
                        "Employee leave guard row was not available after ensuring it exists"));

        LeaveRequest leaveRequest = leaveRequestRepository.findByIdForUpdate(leaveRequestId)
                .orElseThrow(() -> new LeaveRequestNotFoundException(leaveRequestId));
        if (!leaveRequest.getEmployeeId().equals(guard.getEmployeeId())) {
            throw new IllegalStateException(
                    "Leave request employee changed while acquiring the employee guard lock");
        }
        if (leaveRequest.getStatus() != LeaveStatus.PENDING) {
            throw new LeaveRequestNotPendingException(leaveRequestId, leaveRequest.getStatus());
        }
        return leaveRequest;
    }

    private static LeaveResponse toResponse(LeaveRequest leaveRequest) {
        LeaveResponse response = new LeaveResponse();
        response.setId(leaveRequest.getId());
        response.setEmployeeId(leaveRequest.getEmployeeId());
        response.setLeaveType(leaveRequest.getLeaveType());
        response.setStartDate(leaveRequest.getStartDate());
        response.setEndDate(leaveRequest.getEndDate());
        response.setNumberOfDays(leaveRequest.getNumberOfDays());
        response.setReason(leaveRequest.getReason());
        response.setStatus(leaveRequest.getStatus());
        response.setProcessedBy(leaveRequest.getProcessedBy());
        response.setCreatedAt(leaveRequest.getCreatedAt());
        response.setUpdatedAt(leaveRequest.getUpdatedAt());
        return response;
    }
}
