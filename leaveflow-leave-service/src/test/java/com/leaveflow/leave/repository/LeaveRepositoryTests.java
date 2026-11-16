package com.leaveflow.leave.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.leaveflow.leave.entity.EmployeeLeaveGuard;
import com.leaveflow.leave.entity.LeaveBalance;
import com.leaveflow.leave.entity.LeaveRequest;
import com.leaveflow.leave.entity.LeaveStatus;
import com.leaveflow.leave.entity.LeaveType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class LeaveRepositoryTests {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private EmployeeLeaveGuardRepository employeeLeaveGuardRepository;

    @Test
    void savesLeaveRequestAndFindsOverlappingActiveRequest() {
        Long employeeId = 42L;
        leaveRequestRepository.saveAndFlush(new LeaveRequest(
                employeeId,
                LeaveType.CASUAL,
                LocalDate.of(2026, 12, 4),
                LocalDate.of(2026, 12, 7),
                4,
                "Family event"));

        assertThat(leaveRequestRepository
                .existsOverlappingRequest(
                        employeeId,
                        List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED),
                        LocalDate.of(2026, 12, 5),
                        LocalDate.of(2026, 12, 5)))
                .isTrue();
    }

    @Test
    void enforcesUniqueEmployeeAndLeaveTypeBalance() {
        leaveBalanceRepository.saveAndFlush(new LeaveBalance(42L, LeaveType.CASUAL, 12, 0, 12));
        leaveBalanceRepository.save(new LeaveBalance(42L, LeaveType.CASUAL, 12, 0, 12));

        assertThrows(DataIntegrityViolationException.class, leaveBalanceRepository::flush);
    }

    @Test
    void usesEmployeeIdAsLeaveGuardPrimaryKey() {
        employeeLeaveGuardRepository.saveAndFlush(new EmployeeLeaveGuard(42L));

        assertThat(employeeLeaveGuardRepository.findById(42L)).isPresent();
        assertThat(employeeLeaveGuardRepository.findByEmployeeIdForUpdate(42L))
                .get()
                .extracting(EmployeeLeaveGuard::getEmployeeId)
                .isEqualTo(42L);
    }
}
