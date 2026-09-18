package com.mediflow.organization.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mediflow.organization.application.port.in.ChangeStaffDepartmentUseCase;
import com.mediflow.organization.application.port.in.CreateAccountUseCase;
import com.mediflow.organization.application.port.in.CreateDepartmentUseCase;
import com.mediflow.organization.application.port.in.CreateStaffUseCase;
import com.mediflow.organization.application.port.in.GetStaffUseCase;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.EventPublisher;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.application.port.out.PasswordHasher;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.application.service.ChangeStaffDepartmentService;
import com.mediflow.organization.application.service.CreateAccountService;
import com.mediflow.organization.application.service.CreateDepartmentService;
import com.mediflow.organization.application.service.CreateStaffService;
import com.mediflow.organization.application.port.in.GetDepartmentUseCase;
import com.mediflow.organization.application.service.GetDepartmentService;
import com.mediflow.organization.application.service.GetStaffService;
import com.mediflow.organization.application.port.in.UpdateDepartmentUseCase;
import com.mediflow.organization.application.port.in.UpdateAccountStatusUseCase;
import com.mediflow.organization.application.port.in.VerifyCredentialsUseCase;
import com.mediflow.organization.application.service.UpdateDepartmentService;
import com.mediflow.organization.application.service.UpdateAccountStatusService;
import com.mediflow.organization.application.service.VerifyCredentialsService;
import com.mediflow.organization.application.port.in.UpdateStaffUseCase;
import com.mediflow.organization.application.service.UpdateStaffService;

@Configuration
public class ApplicationConfig {

        @Bean
        public VerifyCredentialsUseCase verifyCredentialsUseCase(
                        AccountRepository accountRepository,
                        StaffRepository staffRepository,
                        PasswordHasher passwordHasher) {

                return new VerifyCredentialsService(
                                accountRepository,
                                staffRepository,
                                passwordHasher);
        }

        @Bean
        public UpdateAccountStatusUseCase updateAccountStatusUseCase(
                        AccountRepository accountRepository) {

                return new UpdateAccountStatusService(accountRepository);
        }

        @Bean
        public CreateAccountUseCase createAccountUseCase(
                        AccountRepository accountRepository,
                        PasswordHasher passwordHasher) {

                return new CreateAccountService(
                                accountRepository,
                                passwordHasher);
        }

        @Bean
        public CreateDepartmentUseCase createDepartmentUseCase(
                        DepartmentRepository departmentRepository,
                        EventPublisher eventPublisher,
                        CorrelationIdProvider correlationIds) {

                return new CreateDepartmentService(
                                departmentRepository,
                                eventPublisher,
                                correlationIds);
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
                        EventPublisher eventPublisher,
                        CorrelationIdProvider correlationIds) {

                return new CreateStaffService(
                                staffRepository,
                                departmentRepository,
                                eventPublisher,
                                correlationIds);
        }

        @Bean
        public ChangeStaffDepartmentUseCase changeStaffDepartmentUseCase(
                        StaffRepository staffRepository,
                        DepartmentRepository departmentRepository,
                        EventPublisher eventPublisher,
                        CorrelationIdProvider correlationIds) {

                return new ChangeStaffDepartmentService(
                                staffRepository,
                                departmentRepository,
                                eventPublisher,
                                correlationIds);
        }

        @Bean
        public UpdateDepartmentUseCase updateDepartmentUseCase(
                        DepartmentRepository departmentRepository,
                        StaffRepository staffRepository) {
                return new UpdateDepartmentService(
                                departmentRepository,
                                staffRepository);
        }

        @Bean
        public UpdateStaffUseCase updateStaffUseCase(
                        StaffRepository staffRepository) {
                return new UpdateStaffService(
                                staffRepository);
        }
}
