package com.mediflow.pharmacy.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

/**
 * Cấu hình dùng để xác minh JSON Web Token (JWT) tại pharmacy-service.
 *
 * <p>Gateway và các business service phải sử dụng cùng một khóa bí mật HS256. Gateway xác minh
 * token ở biên hệ thống, còn pharmacy-service xác minh lại token trước khi áp dụng phân quyền ở
 * controller. Cách làm này tạo lớp bảo vệ thứ hai khi service bị gọi trực tiếp, không đi qua
 * gateway.</p>
 *
 * <p>Giá trị được bind từ thuộc tính {@code mediflow.jwt.secret}. Trong môi trường triển khai thật,
 * khóa phải được cung cấp qua biến môi trường {@code MEDIFLOW_JWT_SECRET}; không ghi khóa thật vào
 * source code hoặc file cấu hình được commit.</p>
 *
 * @param secret khóa bí mật dùng để kiểm tra chữ ký HS256; phải có ít nhất 32 byte
 */
@ConfigurationProperties(prefix = "mediflow.jwt")
public record JwtProperties(String secret) {

    private static final int MINIMUM_HS256_KEY_BYTES = 32;

    /**
     * Kiểm tra cấu hình ngay khi Spring khởi tạo bean.
     *
     * <p>Việc fail-fast giúp service không khởi động với khóa rỗng hoặc khóa quá ngắn, thay vì chỉ
     * phát hiện lỗi khi request đầu tiên mang JWT đi vào hệ thống.</p>
     *
     * @throws IllegalArgumentException nếu secret bị thiếu hoặc ngắn hơn yêu cầu của HS256
     */
    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("mediflow.jwt.secret không được để trống");
        }

        int secretLength = secret.getBytes(StandardCharsets.UTF_8).length;
        if (secretLength < MINIMUM_HS256_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "mediflow.jwt.secret phải có ít nhất 32 byte để dùng với HS256");
        }
    }
}
