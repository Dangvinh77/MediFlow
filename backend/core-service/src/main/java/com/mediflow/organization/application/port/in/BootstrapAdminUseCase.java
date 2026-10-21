package com.mediflow.organization.application.port.in;

public interface BootstrapAdminUseCase {
    void execute(String username, String password, String identityNumber, String fullName, String departmentAbbreviation);
}
