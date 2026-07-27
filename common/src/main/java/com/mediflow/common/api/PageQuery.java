package com.mediflow.common.api;

/**
 * Framework-free page request.
 *
 * <p>The application layer must never see Spring Data's {@code Pageable} — that is an
 * infrastructure type. The persistence adapter converts this into a {@code PageRequest}.
 * See docs/ai/04-microservice-blueprint.md.
 */
public record PageQuery(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    /**
     * Builds a sane page request from raw query parameters.
     * Null or out-of-range values are clamped rather than rejected: a bad {@code size}
     * is not worth a 400 when a sensible default exists.
     */
    public static PageQuery of(Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new PageQuery(p, s);
    }
}
