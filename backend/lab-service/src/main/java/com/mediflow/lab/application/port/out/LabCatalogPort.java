package com.mediflow.lab.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.lab.domain.model.LabResultTemplate;
import com.mediflow.lab.domain.model.LabServiceCatalogItem;

/** Lab-owned catalogue of orderable services and versioned result templates. */
public interface LabCatalogPort {

    /** Active services, optionally limited to one display group, ordered by group then name. */
    List<LabServiceCatalogItem> activeServices(String displayGroup);

    Optional<LabServiceCatalogItem> service(String serviceCode);

    Optional<LabResultTemplate> activeTemplate(String templateCode);

    Optional<LabResultTemplate> template(String templateCode, int version);

    void bind(UUID testId, String serviceCode, String templateCode, int templateVersion);

    Optional<TemplateBinding> binding(UUID testId);

    record TemplateBinding(UUID testId, String serviceCode, String templateCode, int templateVersion) {}
}
