# --- MediFlow Backend Dockerfile ---
#
# Multi-stage build:
#   1. build  — compile toàn bộ Maven project (dùng cache để lần sau nhanh hơn)
#   2. runtime — chỉ copy JAR của service cần chạy (image rất nhỏ)
#
# Cách dùng:
#   docker build -t mediflow/patient-service \
#     --build-arg SERVICE_NAME=patient-service \
#     -f backend/Dockerfile .
#
#   Hoặc để docker-compose lo — không cần gõ tay.

# ============================================================
# Stage 1: Build — dùng Maven image có sẵn JDK 21
# ============================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS build

# Cho phép chọn service khi build (bắt buộc)
ARG SERVICE_NAME

WORKDIR /build

# Copy file cấu trúc dự án
COPY pom.xml ./
# ⚠️ Pom.xml của từng module nằm ở backend/<module>/pom.xml
COPY backend/common/pom.xml backend/common/pom.xml
COPY backend/eureka-server/pom.xml backend/eureka-server/pom.xml
COPY backend/gateway/pom.xml backend/gateway/pom.xml
COPY backend/organization-service/pom.xml backend/organization-service/pom.xml
COPY backend/patient-service/pom.xml backend/patient-service/pom.xml
COPY backend/clinical-service/pom.xml backend/clinical-service/pom.xml
COPY backend/lab-service/pom.xml backend/lab-service/pom.xml
COPY backend/pharmacy-service/pom.xml backend/pharmacy-service/pom.xml
COPY backend/billing-service/pom.xml backend/billing-service/pom.xml
COPY backend/notification-service/pom.xml backend/notification-service/pom.xml
COPY backend/report-service/pom.xml backend/report-service/pom.xml

# Tải dependencies (layer này được cache nếu POM không đổi)
# --fail-at-end để không fail ngay nếu thiếu dependency của service khác
RUN mvn dependency:go-offline -pl "backend/${SERVICE_NAME}" -am -q 2>/dev/null || true

# Copy toàn bộ source code
COPY backend/ backend/

# Build chỉ service cần thiết (cùng các dependency như common)
RUN mvn package -pl "backend/${SERVICE_NAME}" -am -DskipTests -q

# ============================================================
# Stage 2: Runtime — JRE Alpine siêu nhẹ
# ============================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

ARG SERVICE_NAME

# Tạo user không phải root (bảo mật)
RUN addgroup -S mediflow && adduser -S mediflow -G mediflow

WORKDIR /app

# Copy JAR từ stage build — chỉ 1 file duy nhất
COPY --from=build /build/backend/${SERVICE_NAME}/target/*.jar app.jar

# Chạy với quyền không root
USER mediflow

# Port mặc định (sẽ ghi đè trong docker-compose nếu cần)
EXPOSE 8080

# labels giúp nhận diện container trong Docker Desktop
LABEL com.mediflow.service="${SERVICE_NAME}"
LABEL com.mediflow.project="MediFlow"

# Health check: Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
