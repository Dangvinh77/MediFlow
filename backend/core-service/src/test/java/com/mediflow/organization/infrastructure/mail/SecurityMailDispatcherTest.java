package com.mediflow.organization.infrastructure.mail;

import com.mediflow.organization.application.dto.response.MailDeliveryDTO;
import com.mediflow.organization.application.port.out.SecurityMailStore;
import com.mediflow.organization.application.service.SecurityMailTemplate;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SecurityMailDispatcherTest {
    @Test
    void sendsMultipartHtmlWithInlineCidLogoAndPlainTextAlternative() throws Exception {
        SecurityMailStore store = mock(SecurityMailStore.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        var message = new jakarta.mail.internet.MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        UUID messageId = UUID.randomUUID();
        var template = new SecurityMailTemplate("http://localhost:3000");
        var rendered = template.credential("Demo User", "demo.user@example.test", "SampleOnly-123!");
        when(store.claimDue(10)).thenReturn(List.of(new SecurityMailStore.QueuedMessage(messageId, false,
                new SecurityMailStore.MailContent("no-reply@example.test", "demo.user@example.test", rendered.subject(),
                        rendered.textBody(), rendered.htmlBody()))));

        new SecurityMailDispatcher(store, sender, "MediFlow").dispatch();

        verify(sender).send(message);
        verify(store).markSent(messageId);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        message.writeTo(output);
        String mime = output.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(mime).contains("Content-Type: multipart/related", "Content-Type: text/plain",
                "Content-Type: text/html", "cid:mediflow-logo", "Content-ID: <mediflow-logo>");
        assertThat(message.getFrom()).hasSize(1);
        assertThat(((InternetAddress) message.getFrom()[0]).getAddress()).isEqualTo("no-reply@example.test");
        assertThat(mime).doesNotContain("data:image/png;base64");
        assertThat(mime).contains("SampleOnly-123!");
    }

    @Test
    void rendererEscapesUserFieldsAndWritesAVisualPreviewWithSyntheticCredentials() throws Exception {
        var template = new SecurityMailTemplate("http://localhost:3000");
        var rendered = template.credential("<Demo User>", "demo.user@example.test", "SampleOnly-123!");
        assertThat(rendered.htmlBody()).contains("&lt;Demo User&gt;", "#1d4ed8", "MediFlow")
                .doesNotContain("<Demo User>");
        assertThat(rendered.textBody()).contains("SampleOnly-123!", "http://localhost:3000/account-access?purpose=login");
        var preview = java.nio.file.Path.of("target", "mail-preview", "credential-email.html");
        java.nio.file.Files.createDirectories(preview.getParent());
        java.nio.file.Files.writeString(preview, rendered.htmlBody(), java.nio.charset.StandardCharsets.UTF_8);
    }
}
