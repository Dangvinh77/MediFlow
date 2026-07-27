# 08 — Persistence & Bilingual Naming

The database speaks **Vietnamese snake_case**; Java speaks **English class names + Vietnamese camelCase fields**; JSON mirrors the Java fields. Map explicitly — never let Hibernate guess.

## The mapping

| Layer | Style | Example |
|-------|-------|---------|
| DB table | VN UPPER_SNAKE | `BENH_NHAN` |
| DB column | VN snake_case | `ma_benh_nhan`, `ho_ten`, `ngay_sinh` |
| JPA entity class | English PascalCase | `Patient` |
| JPA field | VN camelCase | `maBenhNhan`, `hoTen`, `ngaySinh` |
| DTO / JSON field | VN camelCase (same as field) | `maBenhNhan`, `hoTen` |

## Entity example

```java
@Entity
@Table(name = "BENH_NHAN")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ma_benh_nhan")
    private UUID maBenhNhan;

    @Column(name = "ho_ten", length = 100, nullable = false)
    private String hoTen;

    @Column(name = "ngay_sinh", nullable = false)
    private LocalDate ngaySinh;

    @Enumerated(EnumType.STRING)
    @Column(name = "gioi_tinh", length = 1)
    private GioiTinh gioiTinh;          // enum M/F

    @Column(name = "so_cmnd", length = 20, unique = true, nullable = false)
    private String soCmnd;

    @Column(name = "bhyt_so", length = 20)
    private String bhytSo;              // nullable

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
```

## Rules

1. **Always** put `@Column(name = "...")` — do not rely on naming strategies to translate. Explicit beats a global `PhysicalNamingStrategy` because names are Vietnamese and irregular.
2. **`@Table(name = "...")`** with the exact VN table name from the design doc.
3. Primary keys: `UUID`, `@GeneratedValue(strategy = UUID)`.
4. Money: `BigDecimal`, `@Column(precision = 15, scale = 2)`.
5. Enums: `@Enumerated(EnumType.STRING)` — store the readable value; never `ORDINAL`.
6. Timestamps: `Instant` (`created_at`, `updated_at`) with `@CreationTimestamp` / `@UpdateTimestamp`.
7. **No JPA relationships across services.** A `ma_benh_nhan` column inside `LICH_HEN` is a bare `UUID`, not a `@ManyToOne` to a `Patient` in another service.
8. **No `@Data` on entities** (breaks `equals`/`hashCode` with JPA proxies). Use `@Getter/@Setter` + explicit `@Builder`.
9. **Lazy by default** for any in-service association; fetch explicitly when needed.

## Migrations (Flyway)

- Location: `src/main/resources/db/migration/`.
- Name: `V<n>__<snake_desc>.sql` (e.g. `V1__init_benh_nhan.sql`).
- Migrations are **append-only** — never edit a released migration; add a new one.
- `ddl-auto` = `validate` in non-local profiles; Flyway owns the schema.
