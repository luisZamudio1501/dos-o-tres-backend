package com.dosotres.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AgePolicyTest {

    // Hoy = 2026-06-20 (UTC).
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-20T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void nullDob_isNotAdult() {
        assertThat(AgePolicy.isAdult(null, clock)).isFalse();
    }

    @Test
    void exactly18Today_isAdult() {
        assertThat(AgePolicy.isAdult(LocalDate.of(2008, 6, 20), clock)).isTrue();
    }

    @Test
    void oneDayShortOf18_isNotAdult() {
        assertThat(AgePolicy.isAdult(LocalDate.of(2008, 6, 21), clock)).isFalse();
    }

    @Test
    void wellOver18_isAdult() {
        assertThat(AgePolicy.isAdult(LocalDate.of(1990, 1, 1), clock)).isTrue();
    }
}
