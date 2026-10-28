package hu.familyfleet.common.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Stabil lapozott válasz. A Spring Data {@code PageImpl} közvetlen sorosítása belső szerkezetet
 * adna ki (és a Spring Data figyelmeztet is miatta), ezért saját, szándékosan egyszerű formát használunk.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
