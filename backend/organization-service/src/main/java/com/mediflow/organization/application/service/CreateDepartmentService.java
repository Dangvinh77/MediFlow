package com.mediflow.organization.application.service;

import java.util.UUID;

import com.mediflow.organization.application.port.in.CreateDepartmentUseCase;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.EventPublisher;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.DepartmentType;

/**
 * Application Service thực thi CreateDepartmentUseCase.
 *
 * Nhiệm vụ:
 * - Điều phối các bước của use case.
 * - Gọi repository khi cần dữ liệu bên ngoài Domain.
 * - Gọi Domain để thay đổi business state.
 * - Publish event.
 */
public class CreateDepartmentService
        implements CreateDepartmentUseCase {

    private final DepartmentRepository departmentRepository;
    private final EventPublisher eventPublisher;

    public CreateDepartmentService(
            DepartmentRepository departmentRepository,
            EventPublisher eventPublisher
    ) {
        this.departmentRepository = departmentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public UUID execute(
            String departmentName,
            String abbreviation,
            DepartmentType departmentType,
            String location
    ) {

        /*
         * Rule uniqueness cần database.
         *
         * Domain không thể tự biết abbreviation
         * đã tồn tại trong hệ thống hay chưa.
         */
        if (departmentRepository.existsByAbbreviation(abbreviation)) {
            throw new IllegalArgumentException(
                    "Department abbreviation already exists: "
                            + abbreviation
            );
        }

        /*
         * Gọi Domain Factory.
         *
         * Các invariant nội tại của Department
         * được kiểm tra trong Domain.
         */
        Department department = Department.create(
                UUID.randomUUID(),
                departmentName,
                abbreviation,
                departmentType,
                location
        );

        /*
         * Persist.
         */
        departmentRepository.save(department);

        /*
         * Publish department.created.
         */
        eventPublisher.publish(
                new DepartmentCreatedEvent(
                        department.getDepartmentId()
                )
        );

        return department.getDepartmentId();
    }

    /**
     * Event được publish sau khi Department được tạo.
     */
    public record DepartmentCreatedEvent(
            UUID departmentId
    ) {
    }
}
