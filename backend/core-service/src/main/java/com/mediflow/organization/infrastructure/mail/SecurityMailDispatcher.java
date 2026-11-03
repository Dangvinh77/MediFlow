package com.mediflow.organization.infrastructure.mail;

import com.mediflow.organization.application.port.out.SecurityMailStore;
import com.mediflow.organization.application.service.SecurityMailTemplate;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name = "mediflow.security-mail.dispatch-enabled", havingValue = "true", matchIfMissing = true)
public class SecurityMailDispatcher {
    private final SecurityMailStore messages;
    private final JavaMailSender sender;
    private final String fromName;

    public SecurityMailDispatcher(SecurityMailStore messages, JavaMailSender sender,
            @Value("${mediflow.mail.from-name:MediFlow}") String fromName) {
        this.messages = messages;
        this.sender = sender;
        this.fromName = fromName;
    }

    @Scheduled(fixedDelayString = "${mediflow.security-mail.dispatch-delay-ms:5000}")
    public void dispatch() {
        for (var queued : messages.claimDue(10)) {
            try {
                send(queued.content());
                messages.markSent(queued.messageId());
            } catch (Exception deliveryFailure) {
                // Keep errors deliberately generic so provider exceptions cannot leak message bodies.
                messages.markFailed(queued.messageId(), "SMTP_SEND_FAILED");
            }
        }
    }

    private void send(SecurityMailStore.MailContent content) throws Exception {
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(content.from(), fromName);
        helper.setTo(content.recipient());
        helper.setSubject(content.subject());
        String html = content.htmlBody().replace(SecurityMailTemplate.logoDataUri(), "cid:mediflow-logo");
        helper.setText(content.textBody(), html);
        helper.addInline("mediflow-logo", new ByteArrayResource(SecurityMailTemplate.logoPng()), "image/png");
        sender.send(message);
    }
}
