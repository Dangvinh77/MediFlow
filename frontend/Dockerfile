# --- MediFlow Frontend Dockerfile ---
#
# Next.js 16 standalone build: output gọn, tự serve bằng server Node.js built-in.
# Dùng pnpm làm package manager (theo chuẩn dự án).
#
# Cách dùng:
#   docker build -t mediflow/frontend -f frontend/Dockerfile frontend/

# ============================================================
# Stage 1: Dependencies — cài node_modules
# ============================================================
FROM node:22-alpine AS deps

RUN corepack enable && corepack prepare pnpm@latest --activate

WORKDIR /app

# Copy file cấu hình
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./

# Cài dependencies
RUN pnpm install --frozen-lockfile

# ============================================================
# Stage 2: Build — compile Next.js
# ============================================================
FROM node:22-alpine AS build

RUN corepack enable && corepack prepare pnpm@latest --activate

WORKDIR /app

# Copy node_modules từ stage deps
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Build Next.js (standalone output)
RUN pnpm build

# ============================================================
# Stage 3: Runtime — chỉ copy file cần thiết
# ============================================================
FROM node:22-alpine AS runtime

WORKDIR /app

# Tạo user không root
RUN addgroup -S mediflow && adduser -S mediflow -G mediflow

# Copy standalone output (gọn: chỉ có server code + node_modules cần thiết)
COPY --from=build /app/.next/standalone ./
# Copy thư mục static (Next.js public assets built)
COPY --from=build /app/.next/static ./.next/static
# Copy public assets
COPY --from=build /app/public ./public

# Mặc định Next.js chạy port 3000
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# GATEWAY_URL trỏ đến gateway container trong Docker network
ENV GATEWAY_URL=http://gateway:8080

USER mediflow

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:3000/ || exit 1

CMD ["node", "server.js"]
