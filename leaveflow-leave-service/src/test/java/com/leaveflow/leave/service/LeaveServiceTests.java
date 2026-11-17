package com.leaveflow.leave.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leaveflow.leave.dto.ApplyLeaveRequest;
import com.leaveflow.leave.dto.LeaveResponse;
import com.leaveflow.leave.entity.EmployeeLeaveGuard;
import com.leaveflow.leave.entity.LeaveBalance;
import com.leaveflow.leave.entity.LeaveRequest;
import com.leaveflow.leave.entity.LeaveStatus;
import com.leaveflow.leave.entity.LeaveType;
import com.leaveflow.leave.exception.LeaveDurationOutOfRangeException;
import com.leaveflow.leave.exception.LeaveRequestNotFoundException;
import com.leaveflow.leave.exception.LeaveRequestNotPendingException;
import com.leaveflow.leave.exception.LeaveRequestOverlapException;
import com.leaveflow.leave.exception.LeaveStartDateInPastException;
import com.leaveflow.leave.exception.InsufficientLeaveBalanceException;
import com.leaveflow.leave.exception.SelfApprovalNotAllowedException;
import com.leaveflow.leave.repository.EmployeeLeaveGuardRepository;
import com.leaveflow.leave.repository.LeaveBalanceRepository;
import com.leaveflow.leave.repository.LeaveRequestRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTests {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private EmployeeLeaveGuardRepository employeeLeaveGuardRepository;

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    @InjectMocks
    private LeaveService leaveService;

    @Test
    void appliesForLeaveWithInclusiveDurationAndLocksBeforeOverlapCheck() {
        Long employeeId = 42L;
        ApplyLeaveRequest request = request(LocalDate.now().plusDays(2), LocalDate.now().plusDays(4));
        when(employeeLeaveGuardRepository.findByEmployeeIdForUpdate(employeeId))
                .thenReturn(Optional.of(new EmployeeLeaveGuard(employeeId)));
        when(leaveRequestRepository.existsOverlappingRequest(
                eq(employeeId),
                eq(List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED)),
                eq(request.getStartDate()),
                eq(request.getEndDate())))
                .thenReturn(false);
        when(leaveRequestRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(LeaveRequest.class)))
                .thenAnswer(invocation -> {
                    LeaveRequest saved = invocation.getArgument(0);
                    saved.setId(100L);
                    return saved;
                });

        LeaveResponse response = leaveService.applyForLeave(employeeId, request);

        ArgumentCaptor<LeaveRequest> savedRequest = ArgumentCaptor.forClass(LeaveRequest.class);
        InOrder transactionOrder = inOrder(employeeLeaveGuardRepository, leaveRequestRepository);
        transactionOrder.verify(employeeLeaveGuardRepository).insertIfAbsent(employeeId);
        transactionOrder.verify(employeeLeaveGuardRepository).findByEmployeeIdForUpdate(employeeId);
        transactionOrder.verify(leaveRequestRepository).existsOverlappingRequest(
                employeeId,
                List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED),
                request.getStartDate(),
                request.getEndDate());
        transactionOrder.verify(leaveRequestRepository).saveAndFlush(savedRequest.capture());

        LeaveRequest persisted = savedRequest.getValue();
        assertThat(persisted.getNumberOfDays()).isEqualTo(3);
        assertThat(persisted.getEmployeeId()).isEqualTo(employeeId);
        assertThat(persisted.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(persisted.getProcessedBy()).isNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getNumberOfDays()).isEqualTo(3);
        assertThat(response.getReason()).isEqualTo(request.getReason());
    }

    @Test
    void rejectsOverlappingRequestWithoutSaving() {
        Long employeeId = 43L;
        ApplyLeaveRequest request = request(LocalDate.now().plusDays(3), LocalDate.now().plusDays(4));
        when(employeeLeaveGuardRepository.findByEmployeeIdForUpdate(employeeId))
                .thenReturn(Optional.of(new EmployeeLeaveGuard(employeeId)));
        when(leaveRequestRepository.existsOverlappingRequest(
                eq(employeeId),
                eq(List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED)),
                eq(request.getStartDate()),
                eq(request.getEndDate())))
                .thenReturn(true);

        assertThatThrownBy(() -> leaveService.applyForLeave(employeeId, request))
                .isInstanceOf(LeaveRequestOverlapException.class);

        verify(leaveRequestRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsPastStartDateBeforeAccessingRepositories() {
        ApplyLeaveRequest request = request(LocalDate.now().minusDays(1), LocalDate.now());

        assertThatThrownBy(() -> leaveService.applyForLeave(44L, request))
                .isInstanceOf(LeaveStartDateInPastException.class);

        verify(employeeLeaveGuardRepository, never()).insertIfAbsent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsDurationThatCannotFitInDatabaseIntegerColumn() {
        LocalDate startDate = LocalDate.now().plusDays(1);
        ApplyLeaveRequest request = request(startDate, startDate.plusDays((long) Integer.MAX_VALUE));

        assertThatThrownBy(() -> leaveService.applyForLeave(45L, request))
                .isInstanceOf(LeaveDurationOutOfRangeException.class);

        verify(employeeLeaveGuardRepository, never()).insertIfAbsent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void retrievesAndMapsRequestsByEmployee() {
        Long employeeId = 46L;
        LeaveRequest leaveRequest = persistedRequest(employeeId, 200L);
        when(leaveRequestRepository.findAllByEmployeeIdOrderByCreatedAtDesc(employeeId))
                .thenReturn(List.of(leaveRequest));

        List<LeaveResponse> responses = leaveService.getLeaveRequestsByEmployeeId(employeeId);

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.getId()).isEqualTo(200L);
            assertThat(response.getEmployeeId()).isEqualTo(employeeId);
            assertThat(response.getLeaveType()).isEqualTo(LeaveType.SICK);
            assertThat(response.getNumberOfDays()).isEqualTo(2);
            assertThat(response.getStatus()).isEqualTo(LeaveStatus.PENDING);
        });
    }

    @Test
    void retrievesAllRequestsAndFindsRequestById() {
        LeaveRequest leaveRequest = persistedRequest(47L, 201L);
        when(leaveRequestRepository.findAll()).thenReturn(List.of(leaveRequest));
        when(leaveRequestRepository.findById(201L)).thenReturn(Optional.of(leaveRequest));

        assertThat(leaveService.getAllLeaveRequests())
                .singleElement()
                .extracting(LeaveResponse::getId)
                .isEqualTo(201L);
        assertThat(leaveService.getLeaveRequestById(201L).getProcessedBy()).isEqualTo(9L);
    }

    @Test
    void throwsNotFoundForUnknownRequestId() {
        when(leaveRequestRepository.findById(202L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveService.getLeaveRequestById(202L))
                .isInstanceOf(LeaveRequestNotFoundException.class)
                .hasMessageContaining("202");
    }

    @Test
    void approvesPendingRequestAndDeductsBalance() {
        LeaveRequest request = pendingRequest(300L, 48L);
        LeaveBalance balance = new LeaveBalance(48L, LeaveType.CASUAL, 10, 2, 8);
        stubLockedRequest(request);
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveType(48L, LeaveType.CASUAL))
                .thenReturn(Optional.of(balance));
        when(leaveBalanceRepository.saveAndFlush(balance)).thenReturn(balance);
        when(leaveRequestRepository.saveAndFlush(request)).thenReturn(request);

        LeaveResponse response = leaveService.approveLeaveRequest(300L, 49L);

        assertThat(balance.getUsedDays()).isEqualTo(4);
        assertThat(balance.getAvailableDays()).isEqualTo(6);
        assertThat(request.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(request.getProcessedBy()).isEqualTo(49L);
        assertThat(response.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(response.getProcessedBy()).isEqualTo(49L);
        verify(leaveBalanceRepository).saveAndFlush(balance);
        verify(leaveRequestRepository).saveAndFlush(request);
    }

    @Test
    void rejectsPendingRequestWithoutLoadingOrChangingBalance() {
        LeaveRequest request = pendingRequest(301L, 50L);
        stubLockedRequest(request);
        when(leaveRequestRepository.saveAndFlush(request)).thenReturn(request);

        LeaveResponse response = leaveService.rejectLeaveRequest(301L, 51L);

        assertThat(request.getStatus()).isEqualTo(LeaveStatus.REJECTED);
        assertThat(request.getProcessedBy()).isEqualTo(51L);
        assertThat(response.getStatus()).isEqualTo(LeaveStatus.REJECTED);
        assertThat(response.getProcessedBy()).isEqualTo(51L);
        verifyNoInteractions(leaveBalanceRepository);
    }

    @Test
    void rejectsApprovalWhenBalanceIsInsufficientWithoutChangingEitherRecord() {
        LeaveRequest request = pendingRequest(302L, 52L);
        LeaveBalance balance = new LeaveBalance(52L, LeaveType.CASUAL, 3, 2, 1);
        stubLockedRequest(request);
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveType(52L, LeaveType.CASUAL))
                .thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> leaveService.approveLeaveRequest(302L, 53L))
                .isInstanceOf(InsufficientLeaveBalanceException.class);

        assertThat(balance.getUsedDays()).isEqualTo(2);
        assertThat(balance.getAvailableDays()).isEqualTo(1);
        assertThat(request.getStatus()).isEqualTo(LeaveStatus.PENDING);
        verify(leaveBalanceRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verify(leaveRequestRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsApprovalWhenBalanceDoesNotExist() {
        LeaveRequest request = pendingRequest(303L, 54L);
        stubLockedRequest(request);
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveType(54L, LeaveType.CASUAL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveService.approveLeaveRequest(303L, 55L))
                .isInstanceOf(InsufficientLeaveBalanceException.class)
                .hasMessageContaining("0 available");

        assertThat(request.getStatus()).isEqualTo(LeaveStatus.PENDING);
        verify(leaveRequestRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotApproveAlreadyApprovedRequestAgain() {
        LeaveRequest request = pendingRequest(304L, 56L);
        request.setStatus(LeaveStatus.APPROVED);
        stubLockedRequest(request);

        assertNotPending(() -> leaveService.approveLeaveRequest(304L, 57L), 304L, LeaveStatus.APPROVED);

        verifyNoInteractions(leaveBalanceRepository);
        verify(leaveRequestRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotApproveRejectedRequest() {
        LeaveRequest request = pendingRequest(305L, 58L);
        request.setStatus(LeaveStatus.REJECTED);
        stubLockedRequest(request);

        assertNotPending(() -> leaveService.approveLeaveRequest(305L, 59L), 305L, LeaveStatus.REJECTED);

        verifyNoInteractions(leaveBalanceRepository);
    }

    @Test
    void doesNotRejectAlreadyRejectedRequestAgain() {
        LeaveRequest request = pendingRequest(306L, 60L);
        request.setStatus(LeaveStatus.REJECTED);
        stubLockedRequest(request);

        assertNotPending(() -> leaveService.rejectLeaveRequest(306L, 61L), 306L, LeaveStatus.REJECTED);

        verifyNoInteractions(leaveBalanceRepository);
        verify(leaveRequestRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotRejectApprovedRequest() {
        LeaveRequest request = pendingRequest(307L, 62L);
        request.setStatus(LeaveStatus.APPROVED);
        stubLockedRequest(request);

        assertNotPending(() -> leaveService.rejectLeaveRequest(307L, 63L), 307L, LeaveStatus.APPROVED);

        verifyNoInteractions(leaveBalanceRepository);
    }

    @Test
    void preventsSelfApproval() {
        LeaveRequest request = pendingRequest(308L, 64L);
        stubLockedRequest(request);

        assertThatThrownBy(() -> leaveService.approveLeaveRequest(308L, 64L))
                .isInstanceOf(SelfApprovalNotAllowedException.class);

        verifyNoInteractions(leaveBalanceRepository);
        verify(leaveRequestRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void propagatesBalancePersistenceFailureWithoutPersistingApproval() {
        LeaveRequest request = pendingRequest(309L, 65L);
        LeaveBalance balance = new LeaveBalance(65L, LeaveType.CASUAL, 10, 1, 9);
        stubLockedRequest(request);
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveType(65L, LeaveType.CASUAL))
                .thenReturn(Optional.of(balance));
        doThrow(new IllegalStateException("simulated persistence failure"))
                .when(leaveBalanceRepository).saveAndFlush(balance);

        assertThatThrownBy(() -> leaveService.approveLeaveRequest(309L, 66L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated persistence failure");

        assertThat(request.getStatus()).isEqualTo(LeaveStatus.PENDING);
        verify(leaveRequestRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    private void stubLockedRequest(LeaveRequest request) {
        when(leaveRequestRepository.findEmployeeIdById(request.getId()))
                .thenReturn(Optional.of(request.getEmployeeId()));
        when(employeeLeaveGuardRepository.findByEmployeeIdForUpdate(request.getEmployeeId()))
                .thenReturn(Optional.of(new EmployeeLeaveGuard(request.getEmployeeId())));
        when(leaveRequestRepository.findByIdForUpdate(request.getId()))
                .thenReturn(Optional.of(request));
    }

    private static void assertNotPending(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable operation,
            Long requestId,
            LeaveStatus status) {
        assertThatThrownBy(operation)
                .isInstanceOf(LeaveRequestNotPendingException.class)
                .hasMessageContaining(requestId.toString())
                .hasMessageContaining(status.toString());
    }

    private static LeaveRequest pendingRequest(Long requestId, Long employeeId) {
        LeaveRequest request = new LeaveRequest(
                employeeId,
                LeaveType.CASUAL,
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(11),
                2,
                "Family event");
        request.setId(requestId);
        return request;
    }

    private static ApplyLeaveRequest request(LocalDate startDate, LocalDate endDate) {
        ApplyLeaveRequest request = new ApplyLeaveRequest();
        request.setLeaveType(LeaveType.CASUAL);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setReason("Family event");
        return request;
    }

    private static LeaveRequest persistedRequest(Long employeeId, Long requestId) {
        LeaveRequest request = new LeaveRequest(
                employeeId,
                LeaveType.SICK,
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(6),
                2,
                "Medical appointment");
        request.setId(requestId);
        request.setProcessedBy(9L);
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        return request;
    }
}
