package com.mediflow.organization.infrastructure.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediflow.organization.domain.model.DepartmentProfile;
import com.mediflow.organization.domain.model.StaffProfile;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

/** Runs inside the seeder's Organization transaction and advisory lock. */
public final class OrganizationDemoProfileBackfill {
    private final JdbcTemplate jdbc;

    public OrganizationDemoProfileBackfill(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isTracked(String key) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select count(*) > 0 from local_demo_seed_receipt where seed_key=?", Boolean.class, key));
    }

    public void department(JsonNode row, UUID id, boolean previouslyTracked) {
        String key = row.required("key").asText();
        if (completed(key)) return;
        if (previouslyTracked) {
            var profile = new DepartmentProfile(text(row, "description"), text(row, "phoneNumber"), text(row, "email"));
            jdbc.update("""
                    update department set
                      description=case when description is null or trim(description)='' then ? else description end,
                      phone_number=case when phone_number is null or trim(phone_number)='' then ? else phone_number end,
                      email=case when email is null or trim(email)='' then ? else email end
                    where department_id=?
                    """, profile.description(), profile.phoneNumber(), profile.email(), id);
        }
        complete(key, id);
    }

    public void staff(JsonNode row, UUID id, boolean previouslyTracked) {
        String key = row.required("key").asText();
        if (completed(key)) return;
        if (previouslyTracked) {
            var profile = new StaffProfile(date(row, "dateOfBirth"), text(row, "gender") == null ? null
                    : StaffProfile.Gender.valueOf(text(row, "gender")), text(row, "address"),
                    date(row, "hireDate"), text(row, "qualification"));
            // Preserve edited dates and leave incompatible missing dates blank rather than blocking startup.
            jdbc.update("""
                    update staff set
                      date_of_birth=case when date_of_birth is null and (hire_date is null or ? < hire_date) then ? else date_of_birth end,
                      gender=coalesce(gender, ?),
                      address=case when address is null or trim(address)='' then ? else address end,
                      hire_date=case when hire_date is null and (date_of_birth is null or ? > date_of_birth) then ? else hire_date end,
                      qualification=case when qualification is null or trim(qualification)='' then ? else qualification end
                    where staff_id=?
                    """, profile.dateOfBirth(), profile.dateOfBirth(), profile.gender() == null ? null : profile.gender().name(),
                    profile.address(), profile.hireDate(), profile.hireDate(), profile.qualification(), id);
        }
        complete(key, id);
    }

    private boolean completed(String key) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select count(*) > 0 from local_demo_profile_receipt where seed_key=?", Boolean.class, key));
    }

    private void complete(String key, UUID id) {
        // Missing/deleted rows also get a receipt: replay never recreates them.
        jdbc.update("insert into local_demo_profile_receipt(seed_key,entity_id) values (?,?)", key, id);
    }

    private static String text(JsonNode row, String field) {
        return row.path(field).isMissingNode() || row.path(field).isNull() ? null : row.path(field).asText();
    }

    private static LocalDate date(JsonNode row, String field) {
        String value = text(row, field);
        return value == null ? null : LocalDate.parse(value);
    }
}
