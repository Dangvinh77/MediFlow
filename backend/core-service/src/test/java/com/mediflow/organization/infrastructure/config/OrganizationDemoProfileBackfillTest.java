package com.mediflow.organization.infrastructure.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class OrganizationDemoProfileBackfillTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private OrganizationDemoProfileBackfill backfill;
    private final UUID departmentId = UUID.randomUUID(), staffId = UUID.randomUUID();
    private JsonNode department, staff;

    @BeforeEach
    void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        backfill = new OrganizationDemoProfileBackfill(jdbc);
        jdbc.execute("create table local_demo_seed_receipt(seed_key varchar(100) primary key,entity_id uuid not null)");
        jdbc.execute("create table local_demo_profile_receipt(seed_key varchar(100) primary key,entity_id uuid not null)");
        jdbc.execute("create table department(department_id uuid primary key,department_name varchar(100),is_active boolean,description varchar(2000),phone_number varchar(30),email varchar(100))");
        jdbc.execute("create table staff(staff_id uuid primary key,full_name varchar(100),is_active boolean,date_of_birth date,gender varchar(10),address varchar(255),hire_date date,qualification varchar(100),check(date_of_birth is null or hire_date is null or hire_date > date_of_birth))");
        jdbc.update("insert into department(department_id,department_name,is_active,phone_number) values (?,'Edited name',false,'9999')", departmentId);
        jdbc.update("insert into staff(staff_id,full_name,is_active,gender,qualification) values (?,'Edited person',false,'OTHER','Custom degree')", staffId);
        jdbc.update("insert into local_demo_seed_receipt values ('dept-01',?)", departmentId);
        jdbc.update("insert into local_demo_seed_receipt values ('staff-01',?)", staffId);
        var mapper = new ObjectMapper();
        department = mapper.readTree("""
                {"key":"dept-01","description":"Care","phoneNumber":"1234","email":"unit@example.test"}
                """);
        staff = mapper.readTree("""
                {"key":"staff-01","dateOfBirth":"1990-01-02","gender":"FEMALE","address":"Seed address","hireDate":"2020-03-01","qualification":"Seed degree"}
                """);
    }

    private void run() {
        transaction.executeWithoutResult(status -> {
            backfill.department(department, departmentId, backfill.isTracked("dept-01"));
            backfill.staff(staff, staffId, backfill.isTracked("staff-01"));
        });
    }

    @Test
    void fillsOnlyEmptyNewFieldsAndPreservesUserEditsAndInactiveStatus() {
        jdbc.update("update department set description='   ' where department_id=?", departmentId);
        run();
        var unit = jdbc.queryForMap("select * from department where department_id=?", departmentId);
        assertThat(unit.get("DESCRIPTION")).isEqualTo("Care");
        assertThat(unit.get("PHONE_NUMBER")).isEqualTo("9999");
        assertThat(unit.get("DEPARTMENT_NAME")).isEqualTo("Edited name");
        assertThat(unit.get("IS_ACTIVE")).isEqualTo(false);
        var person = jdbc.queryForMap("select * from staff where staff_id=?", staffId);
        assertThat(person.get("ADDRESS")).isEqualTo("Seed address");
        assertThat(person.get("DATE_OF_BIRTH")).isEqualTo(Date.valueOf("1990-01-02"));
        assertThat(person.get("HIRE_DATE")).isEqualTo(Date.valueOf("2020-03-01"));
        assertThat(person.get("GENDER")).isEqualTo("OTHER");
        assertThat(person.get("QUALIFICATION")).isEqualTo("Custom degree");
        assertThat(person.get("FULL_NAME")).isEqualTo("Edited person");
        assertThat(person.get("IS_ACTIVE")).isEqualTo(false);
    }

    @Test
    void receiptPreventsRefillingFieldsThatTheUserClearsAfterFirstRun() {
        run();
        jdbc.update("update department set description=null,email='edited@example.test'");
        jdbc.update("update staff set address=null,qualification='Updated degree'");
        run();
        assertThat(jdbc.queryForObject("select description from department", String.class)).isNull();
        assertThat(jdbc.queryForObject("select email from department", String.class)).isEqualTo("edited@example.test");
        assertThat(jdbc.queryForObject("select address from staff", String.class)).isNull();
        assertThat(jdbc.queryForObject("select qualification from staff", String.class)).isEqualTo("Updated degree");
        assertThat(jdbc.queryForObject("select count(*) from local_demo_profile_receipt", Integer.class)).isEqualTo(2);
    }

    @Test
    void failureRollsBackBothEffectsAndReceiptsAndRetryCompletes() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            backfill.department(department, departmentId, true);
            backfill.staff(staff, staffId, true);
            throw new IllegalStateException("simulated interruption");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select description from department", String.class)).isNull();
        assertThat(jdbc.queryForObject("select address from staff", String.class)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from local_demo_profile_receipt", Integer.class)).isZero();
        run();
        assertThat(jdbc.queryForObject("select address from staff", String.class)).isEqualTo("Seed address");
    }

    @Test
    void newlyAdoptedUntrackedRecordsAreMarkedWithoutChangingThem() {
        jdbc.update("delete from local_demo_seed_receipt");
        run();
        assertThat(jdbc.queryForObject("select description from department", String.class)).isNull();
        assertThat(jdbc.queryForObject("select address from staff", String.class)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from local_demo_profile_receipt", Integer.class)).isEqualTo(2);
    }

    @Test
    void deletedSeedRecordsAreNeverRecreated() {
        jdbc.update("delete from department");
        jdbc.update("delete from staff");
        run(); run();
        assertThat(jdbc.queryForObject("select count(*) from department", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from staff", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from local_demo_profile_receipt", Integer.class)).isEqualTo(2);
    }

    @Test
    void incompatibleSeedHireDateDoesNotOverwriteEditedBirthDateOrBlockStartup() {
        jdbc.update("update staff set date_of_birth=date '2021-01-01'");
        run();
        assertThat(jdbc.queryForObject("select date_of_birth from staff", Date.class)).isEqualTo(Date.valueOf("2021-01-01"));
        assertThat(jdbc.queryForObject("select hire_date from staff", Date.class)).isNull();
        assertThat(jdbc.queryForObject("select address from staff", String.class)).isEqualTo("Seed address");
    }

    @Test
    void incompatibleSeedBirthDateDoesNotOverwriteEditedHireDate() {
        jdbc.update("update staff set hire_date=date '1980-01-01'");
        run();
        assertThat(jdbc.queryForObject("select date_of_birth from staff", Date.class)).isNull();
        assertThat(jdbc.queryForObject("select hire_date from staff", Date.class)).isEqualTo(Date.valueOf("1980-01-01"));
    }
}
