package com.leaveflow.leave.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leaveflow.leave.dto.ApplyLeaveRequest;
import com.leaveflow.leave.entity.LeaveBalance;
import com.leaveflow.leave.entity.LeaveRequest;
import com.leaveflow.leave.entity.LeaveStatus;
import com.leaveflow.leave.entity.LeaveType;
import com.leaveflow.leave.exception.InsufficientLeaveBalanceException;
import com.leaveflow.leave.exception.LeaveRequestNotPendingException;
import com.leaveflow.leave.exception.LeaveRequestOverlapException;
import com.leaveflow.leave.repository.EmployeeLeaveGuardRepository;
import com.leaveflow.leave.repository.LeaveBalanceRepository;
import com.leaveflow.leave.repository.LeaveRequestRepository;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class LeaveServiceIntegrationTests {

    private static final AtomicLong EMPLOYEE_IDS = new AtomicLong(9_000_000_000L);

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private EmployeeLeaveGuardRepository employeeLeaveGuardRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsGuardAndPersistsRequestWithInclusiveDuration() {
        Long employeeId = nextEmployeeId();
        LocalDate startDate = LocalDate.now().plusDays(10);
        ApplyLeaveRequest request = request(startDate, startDate.plusDays(2));

        var response = leaveService.applyForLeave(employeeId, request);

        assertThat(response.getNumberOfDays()).isEqualTo(3);
        assertThat(response.getEmployeeId()).isEqualTo(employeeId);
        assertThat(employeeLeaveGuardRepository.findById(employeeId)).isPresent();
        assertThat(leaveRequestRepository.findAllByEmployeeIdOrderByCreatedAtDesc(employeeId))
                .singleElement()
                .satisfies(saved -> assertThat(saved.getNumberOfDays()).isEqualTo(3));
    }

    @Test
    void rejectsSecondOverlappingRequestForEmployee() {
        Long employeeId = nextEmployeeId();
        LocalDate startDate = LocalDate.now().plusDays(12);
        leaveService.applyForLeave(employeeId, request(startDate, startDate.plusDays(1)));

        assertThatThrownBy(() ->
                leaveService.applyForLeave(employeeId, request(startDate.plusDays(1), startDate.plusDays(2))))
                .isInstanceOf(LeaveRequestOverlapException.class);

        assertThat(leaveRequestRepository.findAllByEmployeeIdOrderByCreatedAtDesc(employeeId))
                .hasSize(1);
    }

    @Test
    void serializesConcurrentFirstApplicationsForSameEmployee() throws Exception {
        Long employeeId = nextEmployeeId();
        LocalDate startDate = LocalDate.now().plusDays(20);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() ->
                    attemptApplication(employeeId, startDate, ready, start));
            Future<Boolean> second = executor.submit(() ->
                    attemptApplication(employeeId, startDate, ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            boolean firstAccepted = first.get(20, TimeUnit.SECONDS);
            boolean secondAccepted = second.get(20, TimeUnit.SECONDS);

            assertThat(firstAccepted).isNotEqualTo(secondAccepted);
            assertThat(leaveRequestRepository.findAllByEmployeeIdOrderByCreatedAtDesc(employeeId))
                    .hasSize(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void approvalUpdatesRequestAndBalanceInPostgres() {
        Long employeeId = nextEmployeeId();
        LeaveRequest request = savePendingRequest(employeeId, LocalDate.now().plusDays(30), 3);
        LeaveBalance balance = saveBalance(employeeId, 10, 2, 8);

        var response = leaveService.approveLeaveRequest(request.getId(), employeeId + 1);

        LeaveBalance updatedBalance = leaveBalanceRepository
                .findByEmployeeIdAndLeaveType(employeeId, LeaveType.CASUAL)
                .orElseThrow();
        LeaveRequest updatedRequest = leaveRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(response.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(response.getProcessedBy()).isEqualTo(employeeId + 1);
        assertThat(updatedRequest.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(updatedRequest.getProcessedBy()).isEqualTo(employeeId + 1);
        assertThat(updatedBalance.getUsedDays()).isEqualTo(5);
        assertThat(updatedBalance.getAvailableDays()).isEqualTo(5);
    }

    @Test
    void rejectionLeavesBalanceUnchanged() {
        Long employeeId = nextEmployeeId();
        LeaveRequest request = savePendingRequest(employeeId, LocalDate.now().plusDays(35), 3);
        LeaveBalance balance = saveBalance(employeeId, 10, 2, 8);

        var response = leaveService.rejectLeaveRequest(request.getId(), employeeId + 1);

        LeaveBalance unchangedBalance = leaveBalanceRepository
                .findByEmployeeIdAndLeaveType(employeeId, LeaveType.CASUAL)
                .orElseThrow();
        assertThat(response.getStatus()).isEqualTo(LeaveStatus.REJECTED);
        assertThat(response.getProcessedBy()).isEqualTo(employeeId + 1);
        assertThat(unchangedBalance.getId()).isEqualTo(balance.getId());
        assertThat(unchangedBalance.getUsedDays()).isEqualTo(2);
        assertThat(unchangedBalance.getAvailableDays()).isEqualTo(8);
    }

    @Test
    void concurrentApprovalsOfSameRequestDeductBalanceOnlyOnce() throws Exception {
        Long employeeId = nextEmployeeId();
        LeaveRequest request = savePendingRequest(employeeId, LocalDate.now().plusDays(40), 3);
        saveBalance(employeeId, 10, 0, 10);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() ->
                    attemptApproval(request.getId(), employeeId + 1, ready, start));
            Future<Boolean> second = executor.submit(() ->
                    attemptApproval(request.getId(), employeeId + 2, ready, start));
            releaseTogether(ready, start);

            assertThat(first.get(20, TimeUnit.SECONDS)).isNotEqualTo(second.get(20, TimeUnit.SECONDS));
            LeaveBalance balance = leaveBalanceRepository
                    .findByEmployeeIdAndLeaveType(employeeId, LeaveType.CASUAL)
                    .orElseThrow();
            assertThat(balance.getUsedDays()).isEqualTo(3);
            assertThat(balance.getAvailableDays()).isEqualTo(7);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentApprovalsCannotOverspendEmployeeBalance() throws Exception {
        Long employeeId = nextEmployeeId();
        LocalDate firstStart = LocalDate.now().plusDays(50);
        LeaveRequest firstRequest = savePendingRequest(employeeId, firstStart, 4);
        LeaveRequest secondRequest = savePendingRequest(employeeId, firstStart.plusDays(10), 4);
        saveBalance(employeeId, 5, 0, 5);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() ->
                    attemptApprovalWithInsufficientBalance(
                            firstRequest.getId(), employeeId + 1, ready, start));
            Future<Boolean> second = executor.submit(() ->
                    attemptApprovalWithInsufficientBalance(
                            secondRequest.getId(), employeeId + 2, ready, start));
            releaseTogether(ready, start);

            assertThat(first.get(20, TimeUnit.SECONDS)).isNotEqualTo(second.get(20, TimeUnit.SECONDS));
            LeaveBalance balance = leaveBalanceRepository
                    .findByEmployeeIdAndLeaveType(employeeId, LeaveType.CASUAL)
                    .orElseThrow();
            assertThat(balance.getUsedDays()).isEqualTo(4);
            assertThat(balance.getAvailableDays()).isEqualTo(1);
            assertThat(leaveRequestRepository.findById(firstRequest.getId()).orElseThrow().getStatus())
                    .isIn(LeaveStatus.APPROVED, LeaveStatus.PENDING);
            assertThat(leaveRequestRepository.findById(secondRequest.getId()).orElseThrow().getStatus())
                    .isIn(LeaveStatus.APPROVED, LeaveStatus.PENDING);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void rollsBackBalanceUpdateWhenRequestPersistenceFails() {
        Long employeeId = nextEmployeeId();
        LeaveRequest request = savePendingRequest(employeeId, LocalDate.now().plusDays(60), 3);
        saveBalance(employeeId, 10, 1, 9);
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION fail_leave_request_update()
                RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'simulated leave request update failure';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER fail_leave_request_update_trigger
                BEFORE UPDATE ON leave_requests
                FOR EACH ROW EXECUTE FUNCTION fail_leave_request_update()
                """);

        try {
            assertThatThrownBy(() -> leaveService.approveLeaveRequest(request.getId(), employeeId + 1))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS fail_leave_request_update_trigger ON leave_requests");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_leave_request_update()");
        }

        LeaveBalance unchangedBalance = leaveBalanceRepository
                .findByEmployeeIdAndLeaveType(employeeId, LeaveType.CASUAL)
                .orElseThrow();
        LeaveRequest unchangedRequest = leaveRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(unchangedBalance.getUsedDays()).isEqualTo(1);
        assertThat(unchangedBalance.getAvailableDays()).isEqualTo(9);
        assertThat(unchangedRequest.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(unchangedRequest.getProcessedBy()).isNull();
    }

    private boolean attemptApplication(
            Long employeeId,
            LocalDate startDate,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to start concurrent leave application");
        }
        try {
            leaveService.applyForLeave(employeeId, request(startDate, startDate.plusDays(1)));
            return true;
        } catch (LeaveRequestOverlapException exception) {
            return false;
        }
    }

    private boolean attemptApproval(
            Long leaveRequestId,
            Long managerEmployeeId,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        awaitStart(ready, start);
        try {
            leaveService.approveLeaveRequest(leaveRequestId, managerEmployeeId);
            return true;
        } catch (LeaveRequestNotPendingException exception) {
            return false;
        }
    }

    private boolean attemptApprovalWithInsufficientBalance(
            Long leaveRequestId,
            Long managerEmployeeId,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        awaitStart(ready, start);
        try {
            leaveService.approveLeaveRequest(leaveRequestId, managerEmployeeId);
            return true;
        } catch (InsufficientLeaveBalanceException exception) {
            return false;
        }
    }

    private static void releaseTogether(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
    }

    private static void awaitStart(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to start concurrent approval");
        }
    }

    private LeaveRequest savePendingRequest(Long employeeId, LocalDate startDate, int numberOfDays) {
        LeaveRequest request = new LeaveRequest(
                employeeId,
                LeaveType.CASUAL,
                startDate,
                startDate.plusDays(numberOfDays - 1L),
                numberOfDays,
                "Family event");
        return leaveRequestRepository.saveAndFlush(request);
    }

    private LeaveBalance saveBalance(Long employeeId, int totalDays, int usedDays, int availableDays) {
        return leaveBalanceRepository.saveAndFlush(
                new LeaveBalance(employeeId, LeaveType.CASUAL, totalDays, usedDays, availableDays));
    }

    private static Long nextEmployeeId() {
        return EMPLOYEE_IDS.incrementAndGet();
    }

    private static ApplyLeaveRequest request(LocalDate startDate, LocalDate endDate) {
        ApplyLeaveRequest request = new ApplyLeaveRequest();
        request.setLeaveType(LeaveType.CASUAL);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setReason("Family event");
        return request;
    }
}
