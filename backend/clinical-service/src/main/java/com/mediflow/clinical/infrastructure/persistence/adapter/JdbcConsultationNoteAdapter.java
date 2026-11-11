package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.port.out.ConsultationNoteRepositoryPort;
import com.mediflow.clinical.domain.model.ConsultationNote;

@Component
public class JdbcConsultationNoteAdapter implements ConsultationNoteRepositoryPort {
    private final JdbcTemplate jdbc;

    public JdbcConsultationNoteAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(ConsultationNote note) {
        jdbc.update("INSERT INTO consultation_note(note_id, record_id, author_staff_id, opinion, created_at) VALUES (?,?,?,?,?)",
                note.getNoteId(), note.getRecordId(), note.getAuthorStaffId(), note.getOpinion(),
                Timestamp.from(note.getCreatedAt()));
    }

    @Override
    public List<ConsultationNote> findByRecord(UUID recordId) {
        return jdbc.query("""
                SELECT note_id, record_id, author_staff_id, opinion, created_at FROM consultation_note
                WHERE record_id = ? ORDER BY created_at, note_id
                """, (rs, n) -> ConsultationNote.restore(rs.getObject("note_id", UUID.class),
                rs.getObject("record_id", UUID.class), rs.getObject("author_staff_id", UUID.class),
                rs.getString("opinion"), rs.getTimestamp("created_at").toInstant()), recordId);
    }
}
