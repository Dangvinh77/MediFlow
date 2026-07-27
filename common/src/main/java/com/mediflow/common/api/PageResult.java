package com.mediflow.common.api;

import java.util.List;
import java.util.function.Function;

/**
 * Framework-free page response.
 *
 * <p>Serialises to the same JSON shape as Spring Data's {@code Page} ({@code content},
 * {@code totalElements}, {@code totalPages}, {@code number}, {@code size}), so the frontend
 * contract in docs/ai/05-api-conventions.md is unchanged.
 *
 * @param <T> the element type
 */
public record PageResult<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int number,
        int size
) {

    public static <T> PageResult<T> of(List<T> content, long totalElements, int number, int size) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResult<>(List.copyOf(content), totalElements, totalPages, number, size);
    }

    public static <T> PageResult<T> empty(PageQuery query) {
        return new PageResult<>(List.of(), 0L, 0, query.page(), query.size());
    }

    /** Maps the elements while keeping the paging metadata — used to turn domain models into DTOs. */
    public <R> PageResult<R> map(Function<? super T, ? extends R> fn) {
        List<R> mapped = content.stream().<R>map(fn).toList();
        return new PageResult<>(mapped, totalElements, totalPages, number, size);
    }
}
