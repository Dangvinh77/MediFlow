package com.mediflow.pharmacy.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.ApiResponse.ApiError;
import com.mediflow.common.api.ApiResponse.ErrorDetail;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.pharmacy.domain.exception.PrescriptionCancellationForbiddenException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Chuyển các exception phát sinh ở tầng web thành {@link ApiResponse} theo chuẩn API chung.
 *
 * <p>Nhờ xử lý tập trung tại đây, controller chỉ cần điều phối request và client luôn nhận được
 * mã HTTP cùng cấu trúc lỗi nhất quán.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Trả về 404 khi không tìm thấy tài nguyên được yêu cầu.
     *
     * @param exception exception chứa mã và thông điệp nghiệp vụ
     * @return phản hồi lỗi {@code 404 Not Found}
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(
            ResourceNotFoundException exception) {

        return build(
                HttpStatus.NOT_FOUND,
                exception.getCode(),
                exception.getMessage());
    }

    /**
     * Trả về 409 khi thao tác tạo hoặc cập nhật làm trùng dữ liệu duy nhất.
     *
     * @param exception exception chứa mã và thông điệp nghiệp vụ
     * @return phản hồi lỗi {@code 409 Conflict}
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> duplicate(
            DuplicateResourceException exception) {

        return build(
                HttpStatus.CONFLICT,
                exception.getCode(),
                exception.getMessage());
    }

    /**
     * Trả về 422 khi request vi phạm quy tắc nghiệp vụ.
     *
     * @param exception exception chứa mã và thông điệp nghiệp vụ
     * @return phản hồi lỗi {@code 422 Unprocessable Entity}
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Void>> businessRule(
            BusinessRuleException exception) {

        return build(
                HttpStatus.UNPROCESSABLE_ENTITY,
                exception.getCode(),
                exception.getMessage());
    }

    /**
     * Trả về 403 khi người dùng có role phù hợp nhưng không có quyền sở hữu để hủy đơn.
     *
     * @param exception lỗi phân quyền nghiệp vụ từ application layer
     * @return phản hồi lỗi {@code 403 Forbidden}
     */
    @ExceptionHandler(PrescriptionCancellationForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> cancellationForbidden(
            PrescriptionCancellationForbiddenException exception) {

        return build(
                HttpStatus.FORBIDDEN,
                PrescriptionCancellationForbiddenException.CODE,
                exception.getMessage());
    }

    /**
     * Trả về chi tiết lỗi theo từng trường khi DTO không qua Jakarta Bean Validation.
     *
     * @param exception exception chứa các field lỗi từ quá trình binding request
     * @return phản hồi lỗi {@code 400 Bad Request} với danh sách lỗi từng trường
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(
            MethodArgumentNotValidException exception) {

        List<ErrorDetail> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toDetail)
                .toList();

        ApiError error = new ApiError(
                "VALIDATION_ERROR",
                "Dữ liệu không hợp lệ",
                details);

        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(error));
    }

    /**
     * Xử lý request có JSON, kiểu dữ liệu hoặc tham số không đúng định dạng.
     *
     * @param exception exception phát sinh trong lúc đọc và chuyển đổi request
     * @return phản hồi lỗi {@code 400 Bad Request}
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ApiResponse<Void>> malformedRequest(
            Exception exception) {

        return build(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "Request không đúng định dạng");
    }

    /**
     * Trả về 403 khi người dùng đã xác thực nhưng không có quyền thực hiện thao tác.
     *
     * @param exception exception được Spring Security ném ra khi từ chối quyền truy cập
     * @return phản hồi lỗi {@code 403 Forbidden}
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> accessDenied(
            AccessDeniedException exception) {

        return build(
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "Bạn không có quyền thực hiện thao tác này");
    }

    /**
     * Ghi log và che giấu chi tiết nội bộ cho mọi lỗi chưa được xử lý riêng.
     *
     * @param exception exception không xác định
     * @return phản hồi lỗi {@code 500 Internal Server Error}
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(
            Exception exception) {

        log.error("Lỗi không mong đợi", exception);

        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Đã xảy ra lỗi hệ thống");
    }

    private ErrorDetail toDetail(FieldError error) {
        return new ErrorDetail(
                error.getField(),
                error.getDefaultMessage());
    }

    private ResponseEntity<ApiResponse<Void>> build(
            HttpStatus status,
            String code,
            String message) {

        return ResponseEntity.status(status)
                .body(ApiResponse.fail(ApiError.of(code, message)));
    }
}
