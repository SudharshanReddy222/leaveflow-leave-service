
# LeaveFlow Leave Service - Copilot Instructions

## Project Overview
This microservice handles employee leave applications, approval and leave balance management.

## Technology Stack
- Java 21
- Spring Boot 3.5.x
- Spring Data JPA
- Hibernate
- PostgreSQL
- Apache Kafka
- Maven
- Lombok
- Bean Validation
- JUnit 5
- Mockito

## Port
8083

## Database
Database name: leaveflow_leave

This service owns leave requests and leave balances.

## Package Structure
com.leaveflow.leave
controller
service
repository
entity
dto
request
response
event
exception
config
kafka
client

## Leave Entity
Create LeaveRequest entity with:

- id (Long)
- employeeId (Long)
- leaveType (enum)
- startDate (LocalDate)
- endDate (LocalDate)
- numberOfDays (Integer)
- reason (String)
- status (enum)
- approvedBy (Long)
- createdAt (LocalDateTime)
- updatedAt (LocalDateTime)

LeaveType:
- CASUAL
- SICK
- EARNED

LeaveStatus:
- PENDING
- APPROVED
- REJECTED
- CANCELLED

## Leave Balance
Maintain leave balances by employee and leave type.

Suggested fields:
- id
- employeeId
- leaveType
- totalDays
- usedDays
- availableDays

## API Endpoints
POST /api/leaves
GET /api/leaves
GET /api/leaves/{id}
GET /api/leaves/employee/{employeeId}
PUT /api/leaves/{id}/approve
PUT /api/leaves/{id}/reject
GET /api/leaves/balance/{employeeId}

## Business Rules
1. Employee can apply for leave.
2. Start date cannot be in the past.
3. End date cannot be before start date.
4. Leave duration must be calculated by the backend.
5. Employee cannot apply for overlapping leave requests.
6. Employee must have sufficient leave balance.
7. Only MANAGER or ADMIN can approve/reject leave.
8. Employees cannot approve their own requests.
9. Only PENDING requests can be approved or rejected.
10. Leave balance must be updated transactionally.
11. Rejected requests must not reduce leave balance.
12. Cancelled approved requests must restore balance if cancellation is supported.

## Kafka Producer
Publish events after successful approval or rejection.

Kafka topic:
leave-events

Event types:
- LEAVE_APPROVED
- LEAVE_REJECTED

Event fields:
- eventId
- eventType
- leaveId
- employeeId
- employeeEmail (when available)
- message
- eventTimestamp

Use JSON serialization.
Use a stable event schema.
Do not publish events before the database transaction successfully commits.
Use a transaction-aware event listener or an appropriate after-commit publishing mechanism.

## Employee Service Communication
Use Employee Service when employee validation or employee details are required.
Avoid hardcoding service URLs.
Use configurable service endpoints.

## Coding Standards
- Follow Controller -> Service -> Repository architecture.
- Use DTOs.
- Use constructor injection.
- Use @Transactional.
- Use custom exceptions.
- Implement global exception handling.
- Validate all requests.
- Avoid business logic in controllers.

## Security
- Requests arrive through API Gateway.
- Validate the authenticated user identity and role using a trusted mechanism.
- Never trust employeeId supplied in request bodies as proof of identity.
- Employees can apply for and view their own leave requests.
- Managers can approve or reject requests within their authorized scope.
- Admin can manage all leave requests.
- Employees cannot approve or reject leave.

## Testing
Test:
- Successful leave application.
- Insufficient balance.
- Overlapping leave requests.
- Invalid leave dates.
- Successful approval.
- Rejection.
- Duplicate approval attempts.
- Kafka event publishing.
- Transaction rollback.

## Important
Inspect the existing project before implementation.
Do not create unnecessary classes.
Run Maven tests after modifications.
