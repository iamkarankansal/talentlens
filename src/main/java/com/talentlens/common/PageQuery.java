package com.talentlens.common;

import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Turns the {@code page}, {@code size} and {@code sort} query parameters into a {@link Pageable},
 * rejecting values that would otherwise surface as server errors.
 */
public final class PageQuery {

    public static final int MAX_SIZE = 100;

    private PageQuery() {
    }

    public static Pageable of(int page, int size, String sort, Set<String> sortableFields) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_SIZE);
        }
        return PageRequest.of(page, size, parseSort(sort, sortableFields));
    }

    private static Sort parseSort(String sort, Set<String> sortableFields) {
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (parts.length > 2 || !sortableFields.contains(field)) {
            throw new BadRequestException("sort must be one of " + sortableFields.stream().sorted().toList()
                    + ", optionally followed by ,asc or ,desc");
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            String requested = parts[1].trim();
            if (requested.equalsIgnoreCase("desc")) {
                direction = Sort.Direction.DESC;
            } else if (!requested.equalsIgnoreCase("asc")) {
                throw new BadRequestException("sort direction must be asc or desc");
            }
        }
        Sort primary = Sort.by(direction, field);
        return field.equals("id") ? primary : primary.and(Sort.by(direction, "id"));
    }
}
