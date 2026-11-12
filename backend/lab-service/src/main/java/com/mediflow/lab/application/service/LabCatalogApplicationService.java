package com.mediflow.lab.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.lab.application.dto.response.LabCatalogDTOs.LabCatalogItemDTO;
import com.mediflow.lab.application.dto.response.LabCatalogDTOs.LabTemplateDTO;
import com.mediflow.lab.application.dto.response.LabCatalogDTOs.LabTestTemplateDTO;
import com.mediflow.lab.application.port.out.LabCatalogPort;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.domain.exception.LabTestNotFoundException;

/** Read side of the catalogue; answers 404 while {@code mediflow.v2.lab-catalog} is off so clients keep the old form. */
@Service
public class LabCatalogApplicationService {
    private final LabCatalogPort catalog;
    private final LabTestRepositoryPort tests;
    private final boolean enabled;

    public LabCatalogApplicationService(LabCatalogPort catalog, LabTestRepositoryPort tests,
            @Value("${mediflow.v2.lab-catalog:false}") boolean enabled) {
        this.catalog = catalog;
        this.tests = tests;
        this.enabled = enabled;
    }

    @Transactional(readOnly = true)
    public List<LabCatalogItemDTO> services(String displayGroup) {
        requireEnabled();
        return catalog.activeServices(displayGroup).stream().map(LabCatalogItemDTO::of).toList();
    }

    @Transactional(readOnly = true)
    public LabTestTemplateDTO templateOf(UUID testId) {
        requireEnabled();
        if (tests.findById(testId).isEmpty()) {
            throw new LabTestNotFoundException(testId);
        }
        return catalog.binding(testId)
                .map(binding -> new LabTestTemplateDTO(testId, binding.serviceCode(),
                        catalog.template(binding.templateCode(), binding.templateVersion()).map(LabTemplateDTO::of)
                                .orElse(null)))
                .orElse(new LabTestTemplateDTO(testId, null, null));
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ResourceNotFoundException("LAB_CATALOG_DISABLED", "The Lab catalogue is not enabled");
        }
    }
}
