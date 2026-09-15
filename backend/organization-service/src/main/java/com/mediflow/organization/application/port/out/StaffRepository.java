package com.mediflow.organization.application.port.out;


import java.util.List;
import java.util.Optional;
import java.util.UUID;


import com.mediflow.organization.domain.model.Staff;

public interface StaffRepository {

        boolean existsByDepartmentIdAndActiveTrue(UUID departmentId);

        Optional<Staff> findById(UUID staffId);

        Staff save(Staff staff);
    List<Staff> findByDepartmentId(UUID departmentId);
    List<Staff> findAll();
    
}
