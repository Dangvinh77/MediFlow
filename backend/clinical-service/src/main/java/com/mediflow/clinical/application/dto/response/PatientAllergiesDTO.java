package com.mediflow.clinical.application.dto.response;

import java.util.List;

/** An empty {@code active} list means "no allergy recorded", never "no allergy". */
public record PatientAllergiesDTO(List<PatientAllergyDTO> active, List<PatientAllergyDTO> inactive) {
    public PatientAllergiesDTO {
        active = List.copyOf(active);
        inactive = List.copyOf(inactive);
    }
}
