package com.universal.reconciliation.service.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BreakSearchCursorTest {

    @Test
    void toTokenShouldRoundTrip() {
        BreakSearchCursor cursor = new BreakSearchCursor(Instant.parse("2024-05-01T10:15:30Z"), 42L);
        String token = cursor.toToken();
        BreakSearchCursor decoded = BreakSearchCursor.fromToken(token);

        assertThat(decoded.runDateTime()).isEqualTo(cursor.runDateTime());
        assertThat(decoded.breakId()).isEqualTo(42L);
    }

    @Test
    void fromTokenShouldRejectInvalidFormat() {
        assertThatThrownBy(() -> BreakSearchCursor.fromToken("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isBeforeShouldCompareChronologically() {
        Instant ts = Instant.parse("2024-05-01T10:00:00Z");
        BreakSearchCursor cursor = new BreakSearchCursor(ts, 10L);

        assertThat(cursor.isBefore(ts.plusSeconds(60), 5L)).isFalse();
        assertThat(cursor.isBefore(ts, 9L)).isTrue();
        assertThat(cursor.isBefore(ts, 11L)).isFalse();
        assertThat(cursor.isBefore(ts.minusSeconds(10), 999L)).isTrue();
    }
}
