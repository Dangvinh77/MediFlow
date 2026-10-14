# Inpatient Placement Authority Implementation Plan

> **DEFERRED reference after the 2026-10-14 MVP redesign.** Preserved from master with
> its implementation evidence; not an active MVP backlog. The canonical scope is
> [system design](../../plans/01-system-design.md) and
> [contracts](../../plans/03-contracts.md). Do not activate Pharmacy/Report consumers
> or reopen Inpatient/Surgery merely by following this historical plan.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Inpatient producer-ready for fresh admission placement authority, immutable bed-use intervals, and historical bed-capacity facts consumed by Pharmacy and Report.

**Architecture:** Preserve the existing admission-authority lookup and add a separate service-only placement lookup backed by one coherent JDBC query. Persist admission-scoped placement revisions and bed-scoped capacity revisions, then append canonical V1 outbox facts in the same transaction as each bed mutation. Pharmacy and Report remain blocked consumers until Huy proves the same checked-in fixture bytes.

**Tech Stack:** Java 21, Spring Boot 3, Spring Security, Spring Data JPA/JdbcTemplate, PostgreSQL/Flyway, RabbitMQ outbox, JUnit 5, Mockito, Testcontainers, Maven.

---

## File map

### New production files

- `backend/inpatient-service/src/main/resources/db/migration/V3__placement_authority_and_capacity_history.sql` — source revisions, capacity history, constraints, and deployment baselines.
- `backend/inpatient-service/src/main/java/com/mediflow/inpatient/domain/model/PlacementAuthoritySnapshot.java` — coherent admission/current-placement read model.
- `backend/inpatient-service/src/main/java/com/mediflow/inpatient/domain/model/BedCapacitySnapshot.java` — immutable capacity-history fact.
- `backend/inpatient-service/src/main/java/com/mediflow/inpatient/domain/model/enums/PlacementChangeType.java` — ASSIGNED/TRANSFERRED/RELEASED.
- `backend/inpatient-service/src/main/java/com/mediflow/inpatient/application/dto/event/AdmissionPlacementChangedEvent.java` — producer event payload.
- `backend/inpatient-service/src/main/java/com/mediflow/inpatient/application/dto/event/BedCapacityChangedEvent.java` — producer event payload.
- `backend/inpatient-service/src/main/java/com/mediflow/inpatient/application/dto/response/PlacementLookupDTO.java` — service-only lookup response.
- `backend/inpatient-service/src/main/java/com/mediflow/inpatient/application/port/in/LookupPlacementAuthorityUseCase.java` — lookup boundary.
- `backend/inpatient-service/src/main/java/com/mediflow/inpatient/application/port/out/PlacementAuthorityRepositoryPort.java` — coherent lookup port.
- `backend/inpatient-service/src/main/java/com/mediflow/inpatient/application/port/out/BedCapacityHistoryPort.java` — append-only capacity-history port.
- `backend/inpatient-service/src/main/java/com/mediflow/inpatient/application/service/LookupPlacementAuthorityService.java` — lookup mapping.
- `backend/inpatient-service/src/main/java/com/mediflow/inpatient/infrastructure/persistence/adapter/PlacementAuthorityPersistenceAdapter.java` — one-statement lookup.
- `backend/inpatient-service/src/main/java/com/mediflow/inpatient/infrastructure/persistence/adapter/BedCapacityHistoryPersistenceAdapter.java` — history persistence.

### Existing production files to modify

- `Admission.java`, `AdmissionJpaEntity.java`, `InpatientPersistenceMapper.java` — placement revision.
- `Bed.java`, `BedJpaEntity.java`, `InpatientPersistenceMapper.java` — capacity revision and snapshot comparison.
- `InpatientApplicationService.java` — append placement/capacity facts atomically.
- `ManageBedUseCase.java`, `InpatientController.java`, `ActorIdentity.java` — authenticated actor/correlation for bed-catalog changes.
- `AdmissionAuthorityController.java`, `InpatientApplicationConfiguration.java` — new service-only lookup.
- `InpatientOutboxPort.java`, `InpatientOutboxPersistenceAdapter.java`, `InpatientEventWireMapper.java` — explicit aggregate type and new canonical wire payloads.

### Tests and fixtures

- Extend `InpatientApplicationServiceTest`, `BedTest`, `InpatientJpaSchemaIntegrationTest`, `InpatientSchemaIntegrationTest`, `InpatientEventWireMapperContractFixtureTest`, `AdmissionAuthorityControllerTest`, and configuration/security tests.
- Add `LookupPlacementAuthorityServiceTest.java` and `PlacementAuthorityIntegrationTest.java`.
- Add canonical JSON under `src/test/resources/contracts/admission-placement-v1/`, `bed-capacity-v1/`, and `placement-authority-v1/`.

### Documentation

- Update the two canonical contracts, active handoff/registry, Inpatient service guide, and Inpatient backend spec named in the approved design.

---

### Task 1: Persist independent placement and capacity revisions

**Files:**
- Create: `backend/inpatient-service/src/main/resources/db/migration/V3__placement_authority_and_capacity_history.sql`
- Modify: `backend/inpatient-service/src/main/java/com/mediflow/inpatient/domain/model/Admission.java`
- Modify: `backend/inpatient-service/src/main/java/com/mediflow/inpatient/domain/model/Bed.java`
- Modify: `backend/inpatient-service/src/main/java/com/mediflow/inpatient/infrastructure/persistence/jpaEntity/AdmissionJpaEntity.java`
- Modify: `backend/inpatient-service/src/main/java/com/mediflow/inpatient/infrastructure/persistence/jpaEntity/BedJpaEntity.java`
- Modify: `backend/inpatient-service/src/main/java/com/mediflow/inpatient/infrastructure/persistence/mapper/InpatientPersistenceMapper.java`
- Test: `backend/inpatient-service/src/test/java/com/mediflow/inpatient/domain/model/BedTest.java`
- Test: `backend/inpatient-service/src/test/java/com/mediflow/inpatient/infrastructure/persistence/InpatientSchemaIntegrationTest.java`

- [ ] **Step 1: Write failing domain and migration tests**

Add tests proving admission placement revisions start at zero and increase only on placement mutations, while capacity revisions increase only when the publishable capacity snapshot changes:

```java
@Test
void placementRevision_isIndependentFromAdmissionStateChanges() {
    Admission admission = admission();
    assertThat(admission.placementRevision()).isZero();
    assertThat(admission.nextPlacementRevision()).isEqualTo(1);
    admission.markAwaitingBed();
    assertThat(admission.placementRevision()).isEqualTo(1);
}

@Test
void occupancyDoesNotChangeCapacityRevision() {
    Bed bed = Bed.create(BED_ID, DEPARTMENT_ID, "A", "101", "01", "STANDARD");
    assertThat(bed.nextCapacityRevision()).isEqualTo(1);
    bed.assign();
    bed.release();
    assertThat(bed.capacityRevision()).isEqualTo(1);
}
```

Extend the schema test to assert `dot_noi_tru.placement_revision`, `giuong_benh.capacity_revision`, and append-only table `lich_su_cong_suat_giuong` with unique `(bed_id, capacity_revision)`.

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```text
mvn -q -pl backend/inpatient-service -am -Dtest=BedTest,InpatientSchemaIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation/test failure because the revision API and V3 migration do not exist.

- [ ] **Step 3: Add the migration and minimal domain persistence**

The migration must add:

```sql
ALTER TABLE dot_noi_tru ADD COLUMN placement_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE giuong_benh ADD COLUMN capacity_revision BIGINT NOT NULL DEFAULT 0;

CREATE TABLE lich_su_cong_suat_giuong (
    capacity_history_id UUID PRIMARY KEY,
    bed_id UUID NOT NULL REFERENCES giuong_benh(bed_id),
    capacity_revision BIGINT NOT NULL CHECK (capacity_revision > 0),
    department_id UUID NOT NULL,
    ward_code VARCHAR(32) NOT NULL,
    bed_type VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL,
    operational_status VARCHAR(20) NOT NULL,
    capacity_eligible BOOLEAN NOT NULL,
    effective_at TIMESTAMPTZ NOT NULL,
    actor_id UUID,
    correlation_id VARCHAR(100) NOT NULL,
    event_id UUID NOT NULL UNIQUE,
    UNIQUE (bed_id, capacity_revision)
);
```

Backfill each existing bed as revision 1 at its current `updated_at`, insert one history row and one pending `bed.capacity.changed` V1 outbox envelope with a stable event UUID derived from bed ID. The backfill explicitly starts coverage at that instant and does not claim earlier history.

Add `long placementRevision` to `Admission`, `long capacityRevision` to `Bed`, backward-compatible restore overloads for existing tests, and methods that increment with `Math.addExact`.

- [ ] **Step 4: Run tests and verify GREEN**

Run the focused command above. Expected: PASS.

- [ ] **Step 5: Commit**

```text
git add backend/inpatient-service/src/main backend/inpatient-service/src/test
git commit -m "feat(inpatient): persist placement and capacity revisions"
```

### Task 2: Add current-placement authority lookup

**Files:**
- Create the placement snapshot, DTO, input/output ports, service and JDBC adapter from the file map.
- Modify: `backend/inpatient-service/src/main/java/com/mediflow/inpatient/infrastructure/web/AdmissionAuthorityController.java`
- Modify: `backend/inpatient-service/src/main/java/com/mediflow/inpatient/infrastructure/config/InpatientApplicationConfiguration.java`
- Test: `backend/inpatient-service/src/test/java/com/mediflow/inpatient/application/service/LookupPlacementAuthorityServiceTest.java`
- Test: `backend/inpatient-service/src/test/java/com/mediflow/inpatient/infrastructure/persistence/PlacementAuthorityIntegrationTest.java`
- Test: `backend/inpatient-service/src/test/java/com/mediflow/inpatient/infrastructure/web/AdmissionAuthorityControllerTest.java`

- [ ] **Step 1: Write failing lookup tests**

Cover active placement, no placement, missing admission, medical discharge with a still-active bed, storage failure, exact current-bed department, and service-only RBAC. The desired response is:

```java
public record PlacementLookupDTO(
        boolean exists,
        UUID admissionId,
        UUID patientId,
        String admissionStatus,
        boolean medicationWindowOpen,
        long placementRevision,
        PlacementDTO placement,
        Instant observedAt) {
    public record PlacementDTO(UUID assignmentId, UUID bedId, UUID departmentId,
                               String wardCode, Instant assignedAt) {}
}
```

The JDBC integration test must insert an admission whose owning department differs from its active bed's department and assert the response uses the bed department inside `placement`.

- [ ] **Step 2: Run focused tests and verify RED**

```text
mvn -q -pl backend/inpatient-service -am -Dtest=LookupPlacementAuthorityServiceTest,PlacementAuthorityIntegrationTest,AdmissionAuthorityControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: missing types/route failures.

- [ ] **Step 3: Implement the minimal lookup**

The adapter executes one statement joining `dot_noi_tru` to the single ACTIVE `phan_giuong` row and its `giuong_benh` row. `medicationWindowOpen` is true only for ADMITTED with no medical-discharge, close or cancellation timestamp. Expose:

```java
@GetMapping("/api/v1/inpatient/admissions/{id}/placement-lookup")
@PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE')")
public ApiResponse<PlacementLookupDTO> placement(@PathVariable UUID id,
                                                  HttpServletRequest request) {
    return ApiResponse.ok(placements.lookup(id), correlation(request));
}
```

Reuse the stable `INPATIENT_LOOKUP_UNAVAILABLE` 503 mapping. Do not change the existing `/lookup` DTO or semantics.

- [ ] **Step 4: Run focused and existing lookup tests**

Expected: PASS with human/access/refresh/wrong-role/long-lived service tokens denied.

- [ ] **Step 5: Commit**

```text
git add backend/inpatient-service/src/main backend/inpatient-service/src/test
git commit -m "feat(inpatient): expose current placement authority"
```

### Task 3: Publish immutable placement changes

**Files:**
- Create: `PlacementChangeType.java`, `AdmissionPlacementChangedEvent.java`
- Modify: `InpatientApplicationService.java`, `InpatientOutboxPort.java`, `InpatientOutboxPersistenceAdapter.java`, `InpatientEventWireMapper.java`
- Test: `InpatientApplicationServiceTest.java`, `InpatientEventWireMapperContractFixtureTest.java`, `InpatientJpaSchemaIntegrationTest.java`
- Create fixtures: `contracts/admission-placement-v1/assigned.json`, `transferred.json`, `released.json`

- [ ] **Step 1: Write failing assignment/transfer/release tests**

The event API must model one atomic placement change:

```java
public record AdmissionPlacementChangedEvent(
        UUID admissionId,
        UUID patientId,
        long placementRevision,
        PlacementChangeType changeType,
        Instant changedAt,
        UUID actorId,
        String reason,
        PlacementFact previousPlacement,
        PlacementFact currentPlacement) {
    public record PlacementFact(UUID assignmentId, UUID bedId, UUID departmentId,
                                String wardCode, Instant assignedAt, Instant releasedAt) {}
}
```

Tests must first fail while asserting:

- assign emits revision 1 with only `currentPlacement`;
- transfer emits revision 2 with closed previous and active current placement;
- release emits revision 3 with only the closed previous placement;
- cancellation with an active bed emits RELEASED; cancellation without a bed emits nothing;
- same-bed idempotent assign and transfer emit nothing and do not increment revision;
- aggregate save, assignment rows and outbox append are ordered inside one transaction.

- [ ] **Step 2: Verify RED**

```text
mvn -q -pl backend/inpatient-service -am -Dtest=InpatientApplicationServiceTest,InpatientEventWireMapperContractFixtureTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Implement minimal placement publication**

Change the outbox port to carry aggregate type explicitly:

```java
void append(String aggregateType, UUID aggregateId, DomainEventEnvelope<?> event);
```

Existing lifecycle calls use `ADMISSION`; capacity calls later use `BED`. In each successful placement mutation, capture exact old/new assignment and bed facts, increment `placementRevision` once, save the admission, and append `admission.placement.changed` with the request correlation ID and business time. Refactor `releaseActiveAssignment` to return an optional released-placement fact instead of silently discarding it.

- [ ] **Step 4: Add exact canonical wire mapping and fixtures**

The mapper must preserve explicit null `previousPlacement`/`currentPlacement`, exact integral revision and ISO timestamps. Fixture tests compare full JSON trees, including envelope producer/version/correlation.

- [ ] **Step 5: Verify GREEN and commit**

```text
mvn -q -pl backend/inpatient-service -am -Dtest=InpatientApplicationServiceTest,InpatientEventWireMapperContractFixtureTest,InpatientJpaSchemaIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
git add backend/inpatient-service
git commit -m "feat(inpatient): publish versioned placement changes"
```

### Task 4: Publish and retain bed-capacity history

**Files:**
- Create: `BedCapacitySnapshot.java`, `BedCapacityChangedEvent.java`, `BedCapacityHistoryPort.java`, `BedCapacityHistoryPersistenceAdapter.java`
- Modify: `ManageBedUseCase.java`, `InpatientApplicationService.java`, `InpatientController.java`, `ActorIdentity.java`, `InpatientApplicationConfiguration.java`, `InpatientEventWireMapper.java`
- Test: `InpatientApplicationServiceTest.java`, `InpatientControllerAuthorizationTest.java`, `InpatientJpaSchemaIntegrationTest.java`, `InpatientEventWireMapperContractFixtureTest.java`
- Create fixtures: `contracts/bed-capacity-v1/eligible.json`, `out-of-service.json`

- [ ] **Step 1: Write failing capacity tests**

The desired fact is:

```java
public record BedCapacityChangedEvent(
        UUID bedId,
        long capacityRevision,
        UUID departmentId,
        String wardCode,
        String bedType,
        boolean active,
        String operationalStatus,
        boolean capacityEligible,
        Instant effectiveAt,
        UUID actorId) {}
```

Tests must prove create publishes revision 1; AVAILABLE → OUT_OF_SERVICE publishes revision 2; active/type changes publish; exact no-op update does not publish; assignment-controlled AVAILABLE ↔ OCCUPIED changes do not publish or increment capacity revision; authenticated staff ID and correlation are retained; history/outbox rollback with the bed transaction.

- [ ] **Step 2: Verify RED**

```text
mvn -q -pl backend/inpatient-service -am -Dtest=InpatientApplicationServiceTest,InpatientControllerAuthorizationTest,InpatientEventWireMapperContractFixtureTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Implement capacity snapshot persistence and publication**

Expose the current authenticated staff UUID without accepting a new body field:

```java
public UUID currentStaffId() {
    UUID staffId = authenticatedDetails().staffId();
    if (staffId == null) throw new AccessDeniedException("Authenticated staff identity is required");
    return staffId;
}
```

Pass actor/correlation into `ManageBedUseCase.create/update`. Compute the publishable snapshot before and after update; when changed, increment revision, save bed, append exact history and `bed.capacity.changed` in the same controller transaction. `capacityEligible = active && status != OUT_OF_SERVICE`; OCCUPIED remains capacity-eligible.

- [ ] **Step 4: Verify GREEN and commit**

```text
mvn -q -pl backend/inpatient-service -am -Dtest=InpatientApplicationServiceTest,InpatientControllerAuthorizationTest,InpatientEventWireMapperContractFixtureTest,InpatientJpaSchemaIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
git add backend/inpatient-service
git commit -m "feat(inpatient): publish bed capacity history"
```

### Task 5: Prove PostgreSQL and broker behavior

**Files:**
- Modify: `InpatientSchemaIntegrationTest.java`, `InpatientJpaSchemaIntegrationTest.java`, `InpatientRabbitIntegrationTest.java`, `InpatientServiceSmokeTest.java`
- Add fixture resources used by broker tests.

- [ ] **Step 1: Add failing integration cases**

Cover concurrent placement mutations producing one monotonic revision chain, transfer rollback leaving neither half applied, unique capacity revisions, exact baseline event shape, outbox aggregate type, producer flag disabled behavior, broker ACK publishing exact fixture bytes, and mandatory-return retry leaving the event pending.

- [ ] **Step 2: Run with Testcontainers and verify RED**

```text
mvn -q -pl backend/inpatient-service -am -Dtest=InpatientSchemaIntegrationTest,InpatientJpaSchemaIntegrationTest,InpatientRabbitIntegrationTest,InpatientServiceSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Make the smallest persistence/dispatcher corrections required**

Do not add consumer bindings, turn flags on, or weaken uniqueness/rollback checks. Unknown event payloads must still fail before outbox persistence.

- [ ] **Step 4: Re-run and commit**

Expected: all named integration tests PASS, or Docker-unavailable tests report their established skip mechanism without false success.

```text
git add backend/inpatient-service
git commit -m "test(inpatient): prove placement producer contracts"
```

### Task 6: Update canonical contracts and Huy handoff

**Files:**
- Modify: `docs/handoffs/care-finance/CONTRACT-INPATIENT-SURGERY-01.md`
- Modify: `docs/handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md`
- Modify: `docs/handoffs/HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md`
- Modify: `docs/handoffs/README.md`
- Modify: `docs/ai/services/inpatient.md`
- Modify: `docs/eproject_general_plan/backend-spec/care-finance-v2/10-inpatient.md`
- Modify: `backend/inpatient-service/README.md`

- [ ] **Step 1: Update producer contracts from blocked to producer-ready evidence**

Document exact routing keys, payload fields, business identities/revisions, absence/freshness semantics, feature defaults, compatibility of the old lookup, and the coverage horizon of migration baselines.

- [ ] **Step 2: Narrow Huy's remaining handoff**

State that Vinh supplies producer DTOs, fixtures, service lookup, placement history and capacity facts. Keep the handoff ACTIVE for Huy to implement Pharmacy post-lock authorization/race handling and Report projection/replay/publication. Do not claim `IMPLEMENTED` until Huy's consumer tests read these exact fixtures.

- [ ] **Step 3: Check documentation consistency**

```text
rg -n "placement|capacity|occupancy|medicationWindowOpen|admission.placement.changed|bed.capacity.changed" docs backend/inpatient-service/README.md
git diff --check
```

Expected: no statement still says the producer facts are undefined; no statement claims Pharmacy/Report activation.

- [ ] **Step 4: Commit**

```text
git add docs backend/inpatient-service/README.md
git commit -m "docs(inpatient): hand off placement facts to Huy"
```

### Task 7: Full verification and review readiness

**Files:**
- Review all changed files; no new production scope.

- [ ] **Step 1: Run mandatory module verification**

```text
mvn -q -pl backend/inpatient-service -am test
```

Expected: zero failures/errors.

- [ ] **Step 2: Run focused quality checks**

```text
git diff --check
git status --short
git log --oneline master..HEAD
```

Confirm no Pharmacy/Report production source, Gateway, shared build, unrelated docs, or user files changed.

- [ ] **Step 3: Review against the approved design and ownership rules**

Verify every new function has a test that was observed failing first, producer flags remain false, old wire fixtures are unchanged, and handoff status does not overclaim consumer acceptance.

- [ ] **Step 4: Prepare PR summary**

Summarize producer API/events, migration coverage horizon, tests, remaining Huy consumer work, and rollout gates. Do not merge or clean the branch until the user requests it.
