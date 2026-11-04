package com.mediflow.organization.application.port.in;

import com.mediflow.organization.application.dto.response.ProfileAccountLookupDTO;
import java.util.UUID;

public interface LookupProfileAccountUseCase {
    ProfileAccountLookupDTO staff(UUID staffId);
    ProfileAccountLookupDTO patient(UUID patientId);
}
