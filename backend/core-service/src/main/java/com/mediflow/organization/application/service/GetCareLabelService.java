package com.mediflow.organization.application.service;

import com.mediflow.organization.application.dto.response.CareLabelDTO;
import com.mediflow.organization.application.port.in.GetCareLabelUseCase;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.RoomRepository;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.model.JobTitle;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public class GetCareLabelService implements GetCareLabelUseCase {
    private final StaffRepository staff;
    private final DepartmentRepository departments;
    private final RoomRepository rooms;

    public GetCareLabelService(StaffRepository staff, DepartmentRepository departments, RoomRepository rooms) {
        this.staff = staff;
        this.departments = departments;
        this.rooms = rooms;
    }

    @Override
    public Optional<CareLabelDTO> read(String kind, UUID id) {
        // Inactive references still name historical visits; labels do not prove current eligibility.
        return switch (kind) {
            case "doctor" -> staff.findById(id).filter(member -> member.getJobTitle() == JobTitle.DOCTOR)
                    .map(member -> new CareLabelDTO(member.getStaffId(), member.getFullName()));
            case "department" -> departments.findById(id)
                    .map(department -> new CareLabelDTO(department.getDepartmentId(), department.getDepartmentName()));
            case "room" -> rooms.findById(id).filter(room -> "CONSULTATION".equals(room.getRoomType()))
                    .map(room -> new CareLabelDTO(room.getRoomId(), room.getRoomName()));
            default -> throw new IllegalArgumentException("Loại thông tin không được hỗ trợ");
        };
    }
}
