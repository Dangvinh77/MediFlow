package com.mediflow.notification.application.service;
import com.mediflow.notification.application.dto.command.PatientResultNoticeCommand;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.domain.model.CareNotificationIntent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class PatientResultNoticeService {
    private final CareNotificationRepositoryPort history;
    public PatientResultNoticeService(CareNotificationRepositoryPort history) { this.history = history; }
    @Transactional("notificationTransactionManager")
    public void receive(PatientResultNoticeCommand command) {
        if (!history.claim(command.eventId(), command.eventType(), command.fingerprint())) return;
        history.deliverInApp(new CareNotificationIntent(UUID.randomUUID(), command.patientId(), command.eventId(),
                command.eventType(), command.sourceId(), command.correlationId(), command.eventType(),
                command.title(), command.content()));
    }
}
