package com.mediflow.lab.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.lab.application.port.out.LabCatalogPort;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.domain.model.LabResultTemplate;
import com.mediflow.lab.domain.model.LabTest;

class LabCatalogApplicationServiceTest {
    final LabCatalogPort catalog = mock(LabCatalogPort.class);
    final LabTestRepositoryPort tests = mock(LabTestRepositoryPort.class);

    @Test void disabledCatalogueIsNotFound() {
        LabCatalogApplicationService off = new LabCatalogApplicationService(catalog, tests, false);
        assertThatThrownBy(() -> off.services(null)).isInstanceOf(ResourceNotFoundException.class)
                .extracting("code").isEqualTo("LAB_CATALOG_DISABLED");
        assertThatThrownBy(() -> off.templateOf(UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(catalog);
    }

    @Test void testCreatedBeforeTheCatalogueHasNoTemplate() {
        UUID testId = UUID.randomUUID();
        when(tests.findById(testId)).thenReturn(Optional.of(mock(LabTest.class)));
        when(catalog.binding(testId)).thenReturn(Optional.empty());
        var dto = new LabCatalogApplicationService(catalog, tests, true).templateOf(testId);
        assertThat(dto.serviceCode()).isNull();
        assertThat(dto.template()).isNull();
    }

    @Test void pinnedVersionIsServedEvenWhenANewerVersionIsActive() {
        UUID testId = UUID.randomUUID();
        when(tests.findById(testId)).thenReturn(Optional.of(mock(LabTest.class)));
        when(catalog.binding(testId)).thenReturn(Optional.of(new LabCatalogPort.TemplateBinding(testId, "CBC", "CBC", 1)));
        when(catalog.template("CBC", 1)).thenReturn(Optional.of(new LabResultTemplate("CBC", 1, "CBC", List.of())));
        var dto = new LabCatalogApplicationService(catalog, tests, true).templateOf(testId);
        assertThat(dto.serviceCode()).isEqualTo("CBC");
        assertThat(dto.template().version()).isEqualTo(1);
    }
}
