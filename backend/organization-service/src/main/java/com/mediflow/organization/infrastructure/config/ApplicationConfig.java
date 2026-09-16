package com.mediflow.organization.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mediflow.organization.application.port.in.ChangeStaffDepartmentUseCase;
import com.mediflow.organization.application.port.in.CreateDepartmentUseCase;
import com.mediflow.organization.application.port.in.CreateStaffUseCase;
import com.mediflow.organization.application.port.in.GetStaffUseCase;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.EventPublisher;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.application.service.ChangeStaffDepartmentService;
import com.mediflow.organization.application.service.CreateDepartmentService;
import com.mediflow.organization.application.service.CreateStaffService;
import com.mediflow.organization.application.port.in.GetDepartmentUseCase;
import com.mediflow.organization.application.service.GetDepartmentService;
import com.mediflow.organization.application.service.GetStaffService;
import com.mediflow.organization.application.port.in.UpdateDepartmentUseCase;
import com.mediflow.organization.application.service.UpdateDepartmentService;
import com.mediflow.organization.application.port.in.UpdateStaffUseCase;
import com.mediflow.organization.application.service.UpdateStaffService;

@Configuration
public class ApplicationConfig {

        @Bean
        public CreateDepartmentUseCase createDepartmentUseCase(
                        DepartmentRepository departmentRepository,
                        EventPublisher eventPublisher) {

                return new CreateDepartmentService(
                                departmentRepository,
                                eventPublisher);
        }

        @Bean
        public GetDepartmentUseCase getDepartmentUseCase(
                        DepartmentRepository departmentRepository) {

                return new GetDepartmentService(
                                departmentRepository);
        }

        @Bean
        public GetStaffUseCase getStaffUseCase(
                        StaffRepository staffRepository) {

                return new GetStaffService(
                                staffRepository);
        }

        @Bean
        public CreateStaffUseCase createStaffUseCase(
                        StaffRepository staffRepository,
                        DepartmentRepository departmentRepository,
                        EventPublisher eventPublisher) {

                return new CreateStaffService(
                                staffRepository,
                                departmentRepository,
                                eventPublisher);
        }

        @Bean
        public ChangeStaffDepartmentUseCase changeStaffDepartmentUseCase(
                        StaffRepository staffRepository,
                        DepartmentRepository departmentRepository,
                        EventPublisher eventPublisher) {

                return new ChangeStaffDepartmentService(
                                staffRepository,
                                departmentRepository,
                                eventPublisher);
        }

        @Bean
        public UpdateDepartmentUseCase updateDepartmentUseCase(
                        DepartmentRepository departmentRepository,
                        EventPublisher eventPublisher) {
                return new UpdateDepartmentService(
                                departmentRepository,
                                eventPublisher);
        }

        @Bean
        public UpdateStaffUseCase updateStaffUseCase(
                        StaffRepository staffRepository,
                        EventPublisher eventPublisher) {
                return new UpdateStaffService(
                                staffRepository,
                                eventPublisher);
        }
}
