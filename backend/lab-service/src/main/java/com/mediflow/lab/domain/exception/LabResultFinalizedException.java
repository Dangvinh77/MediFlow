package com.mediflow.lab.domain.exception;

import com.mediflow.common.exception.DuplicateResourceException;

/** Conflict raised when a terminal V2 result snapshot is amended. */
public class LabResultFinalizedException extends DuplicateResourceException {

    public LabResultFinalizedException() {
        super("LAB_RESULT_FINALIZED", "Kết quả xét nghiệm đã được chốt và không thể sửa đổi");
    }
}
