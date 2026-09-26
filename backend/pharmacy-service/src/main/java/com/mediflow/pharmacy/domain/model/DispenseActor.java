package com.mediflow.pharmacy.domain.model;

import java.util.UUID;

import com.mediflow.pharmacy.domain.exception.DispenseRuleException;
import com.mediflow.pharmacy.domain.model.enums.DispenseActorType;

/** Typed actor snapshot written with a completed dispense audit record. */
public record DispenseActor(DispenseActorType type, UUID id) {

    public DispenseActor {
        if (type == null) {
            throw new DispenseRuleException(
                    "DISPENSE_ACTOR_TYPE_REQUIRED", "Loại tác nhân xuất thuốc là bắt buộc");
        }
        if ((type == DispenseActorType.STAFF || type == DispenseActorType.ACCOUNT) && id == null) {
            throw new DispenseRuleException(
                    "DISPENSE_ACTOR_ID_REQUIRED", "Tác nhân người dùng phải có định danh");
        }
        if (type == DispenseActorType.SYSTEM && id != null) {
            throw new DispenseRuleException(
                    "DISPENSE_SYSTEM_ACTOR_ID_INVALID", "Tác nhân hệ thống không có user id");
        }
        if (type == DispenseActorType.LEGACY_UNKNOWN) {
            throw new DispenseRuleException(
                    "DISPENSE_LEGACY_ACTOR_NOT_ACTIONABLE",
                    "Actor lịch sử không xác định không thể dùng cho thao tác mới");
        }
    }

    public static DispenseActor staff(UUID staffId) {
        return new DispenseActor(DispenseActorType.STAFF, staffId);
    }

    public static DispenseActor account(UUID accountId) {
        return new DispenseActor(DispenseActorType.ACCOUNT, accountId);
    }

    public static DispenseActor system() {
        return new DispenseActor(DispenseActorType.SYSTEM, null);
    }

}
