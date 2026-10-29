package com.talentlens.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageQueryTests {

    private static final Set<String> FIELDS = Set.of("id", "title", "createdAt");

    @Test
    void buildsPageableWithIdAsTieBreaker() {
        Pageable pageable = PageQuery.of(2, 10, "title,desc", FIELDS);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "title").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    @Test
    void defaultsToAscendingAndDoesNotRepeatId() {
        assertThat(PageQuery.of(0, 20, "id", FIELDS).getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "id"));
        assertThat(PageQuery.of(0, 20, " createdAt , ASC ", FIELDS).getSort())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
    }

    @Test
    void rejectsOutOfRangePageAndSize() {
        assertThatThrownBy(() -> PageQuery.of(-1, 20, "id", FIELDS))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("page must be 0 or greater");
        assertThatThrownBy(() -> PageQuery.of(0, 0, "id", FIELDS))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("size must be between 1 and " + PageQuery.MAX_SIZE);
        assertThatThrownBy(() -> PageQuery.of(0, PageQuery.MAX_SIZE + 1, "id", FIELDS))
                .isInstanceOf(BadRequestException.class);
        assertThat(PageQuery.of(0, PageQuery.MAX_SIZE, "id", FIELDS).getPageSize()).isEqualTo(PageQuery.MAX_SIZE);
    }

    @Test
    void rejectsUnknownFieldsAndDirections() {
        assertThatThrownBy(() -> PageQuery.of(0, 20, "password", FIELDS))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("sort must be one of [createdAt, id, title], optionally followed by ,asc or ,desc");
        assertThatThrownBy(() -> PageQuery.of(0, 20, "title,asc,id", FIELDS))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> PageQuery.of(0, 20, "title,sideways", FIELDS))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("sort direction must be asc or desc");
    }
}
