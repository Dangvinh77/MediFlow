package com.mediflow.organization.application.service;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.organization.application.port.in.ManageRoomTypesUseCase.NewRoomType;
import com.mediflow.organization.application.port.in.ManageRoomTypesUseCase.RoomTypeUsage;
import com.mediflow.organization.application.port.out.RoomTypeRepository;
import com.mediflow.organization.domain.exception.InvalidRoomException;
import com.mediflow.organization.domain.model.DepartmentType;
import com.mediflow.organization.domain.model.RoomTypeDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManageRoomTypesServiceTest {

    private final RoomTypeRepository types = mock(RoomTypeRepository.class);
    private final ManageRoomTypesService service = new ManageRoomTypesService(types);

    @Test
    void listsTypesGroupedByCategoryWithRoomCounts() {
        when(types.findAll()).thenReturn(List.of(
                type("CASHIER", "Quầy thu viện phí", DepartmentType.ADMINISTRATIVE, false, 50),
                type("DEPARTMENT_OFFICE", "Văn phòng khoa", DepartmentType.CLINICAL, false, 20),
                type("CONSULTATION", "Phòng khám", DepartmentType.CLINICAL, true, 10)));
        when(types.roomCounts()).thenReturn(Map.of("CONSULTATION", 4L));

        List<RoomTypeUsage> list = service.list();

        assertThat(list).extracting(usage -> usage.type().getCode())
                .containsExactly("CONSULTATION", "DEPARTMENT_OFFICE", "CASHIER");
        assertThat(list).extracting(RoomTypeUsage::roomCount).containsExactly(4L, 0L, 0L);
    }

    @Test
    void createsAnAdministratorTypeWithACodeDerivedFromItsName() {
        when(types.nextSortOrder()).thenReturn(70);
        when(types.findByCode(anyString())).thenReturn(Optional.empty());

        RoomTypeUsage created = service.create(new NewRoomType("Phòng thủ thuật – Đông y",
                "Thủ thuật ngoại trú", DepartmentType.CLINICAL, false));

        ArgumentCaptor<RoomTypeDefinition> saved = ArgumentCaptor.forClass(RoomTypeDefinition.class);
        verify(types).insert(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo("PHONG_THU_THUAT_DONG_Y");
        assertThat(saved.getValue().isSystemDefined()).isFalse();
        assertThat(saved.getValue().schedulable()).isFalse();
        assertThat(created.roomCount()).isZero();
    }

    @Test
    void duplicateNameOrCodeIsRejected() {
        when(types.existsByName("Quầy thu viện phí", null)).thenReturn(true);
        assertThatThrownBy(() -> service.create(new NewRoomType("Quầy thu viện phí", null,
                DepartmentType.ADMINISTRATIVE, true)))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting("code").isEqualTo("ROOM_TYPE_DUPLICATE");

        when(types.findByCode("PHONG_KHAM")).thenReturn(Optional.of(
                type("PHONG_KHAM", "Phong kham", DepartmentType.CLINICAL, false, 10)));
        assertThatThrownBy(() -> service.create(new NewRoomType("Phòng khám", null,
                DepartmentType.CLINICAL, false)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(types, never()).insert(any());
    }

    @Test
    void nameAndCategoryAreRequired() {
        assertThatThrownBy(() -> service.create(new NewRoomType(" ", null, DepartmentType.CLINICAL, false)))
                .isInstanceOf(InvalidRoomException.class);
        assertThatThrownBy(() -> service.create(new NewRoomType("Phòng mổ", null, null, false)))
                .isInstanceOf(InvalidRoomException.class);
    }

    @Test
    void renameKeepsCodeCategoryAndRoomRule() {
        RoomTypeDefinition cashier = type("CASHIER", "Quầy thu ngân", DepartmentType.ADMINISTRATIVE, false, 50);
        when(types.lockByCode("CASHIER")).thenReturn(Optional.of(cashier));
        when(types.roomCounts()).thenReturn(Map.of("CASHIER", 1L));

        RoomTypeUsage renamed = service.rename("CASHIER", "Quầy thu viện phí", "Thu viện phí ngoại trú");

        assertThat(renamed.type().getName()).isEqualTo("Quầy thu viện phí");
        assertThat(renamed.type().getCode()).isEqualTo("CASHIER");
        assertThat(renamed.type().getDepartmentType()).isEqualTo(DepartmentType.ADMINISTRATIVE);
        assertThat(renamed.roomCount()).isEqualTo(1L);
        verify(types).update(cashier);
    }

    @Test
    void systemTypeCannotBeDeleted() {
        when(types.lockByCode("CONSULTATION")).thenReturn(Optional.of(
                type("CONSULTATION", "Phòng khám", DepartmentType.CLINICAL, true, 10)));

        assertThatThrownBy(() -> service.delete("CONSULTATION"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("ROOM_TYPE_SYSTEM");
        verify(types, never()).delete(anyString());
    }

    @Test
    void typeUsedByAnyRoomCannotBeDeleted() {
        when(types.lockByCode("PHARMACY")).thenReturn(Optional.of(
                type("PHARMACY", "Quầy cấp phát thuốc", DepartmentType.PARACLINICAL, false, 40)));
        when(types.roomCounts()).thenReturn(Map.of("PHARMACY", 1L));

        assertThatThrownBy(() -> service.delete("PHARMACY"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("ROOM_TYPE_IN_USE");
        verify(types, never()).delete(anyString());
    }

    @Test
    void unusedTypeIsDeleted() {
        when(types.lockByCode("PHONG_THU_THUAT")).thenReturn(Optional.of(
                type("PHONG_THU_THUAT", "Phòng thủ thuật", DepartmentType.CLINICAL, false, 70)));
        when(types.roomCounts()).thenReturn(Map.of());

        service.delete("PHONG_THU_THUAT");

        verify(types).delete("PHONG_THU_THUAT");
    }

    @Test
    void missingTypeIsNotFound() {
        when(types.lockByCode("UNKNOWN")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete("UNKNOWN")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.rename("UNKNOWN", "Tên mới", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void codeIsAsciiUpperSnakeCase() {
        assertThat(RoomTypeDefinition.codeFor("Đơn vị đo điện tim")).isEqualTo("DON_VI_DO_DIEN_TIM");
        assertThat(RoomTypeDefinition.codeFor("  Phòng 3D  ")).isEqualTo("PHONG_3D");
        assertThat(RoomTypeDefinition.codeFor("123 Kho")).isEqualTo("TYPE_123_KHO");
    }

    private static RoomTypeDefinition type(String code, String name, DepartmentType category,
                                           boolean system, int order) {
        return RoomTypeDefinition.reconstitute(code, name, null, category, true, system, order, null, null);
    }
}
