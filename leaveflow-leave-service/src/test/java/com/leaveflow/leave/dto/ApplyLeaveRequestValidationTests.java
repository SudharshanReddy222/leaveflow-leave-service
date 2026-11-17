package com.leaveflow.leave.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.leaveflow.leave.entity.LeaveType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ApplyLeaveRequestValidationTests {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void rejectsNullRequiredFields() {
        ApplyLeaveRequest request = new ApplyLeaveRequest();

        assertThat(propertyPaths(validator.validate(request)))
                .containsExactlyInAnyOrder("leaveType", "startDate", "endDate", "reason");
    }

    @Test
    void rejectsEndDateBeforeStartDate() {
        ApplyLeaveRequest request = validRequest();
        request.setStartDate(LocalDate.of(2026, 12, 10));
        request.setEndDate(LocalDate.of(2026, 12, 9));

        assertThat(propertyPaths(validator.validate(request))).containsExactly("dateRangeValid");
    }

    @Test
    void rejectsBlankReason() {
        ApplyLeaveRequest request = validRequest();
        request.setReason(" \t ");

        assertThat(propertyPaths(validator.validate(request))).contains("reason");
    }

    @Test
    void rejectsReasonLongerThanDatabaseColumn() {
        ApplyLeaveRequest request = validRequest();
        request.setReason("x".repeat(1001));

        assertThat(propertyPaths(validator.validate(request))).contains("reason");
    }

    @Test
    void acceptsValidRequestWithReasonAtDatabaseColumnLimit() {
        ApplyLeaveRequest request = validRequest();
        request.setReason("x".repeat(1000));

        assertThat(validator.validate(request)).isEmpty();
    }

    private static ApplyLeaveRequest validRequest() {
        ApplyLeaveRequest request = new ApplyLeaveRequest();
        request.setLeaveType(LeaveType.CASUAL);
        request.setStartDate(LocalDate.of(2026, 12, 9));
        request.setEndDate(LocalDate.of(2026, 12, 10));
        request.setReason("Family event");
        return request;
    }

    private static Set<String> propertyPaths(Set<ConstraintViolation<ApplyLeaveRequest>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
