package com.finsight.api.service;

import com.finsight.api.domain.Frequency;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringServiceTest {

    @Test
    void advancesMonthlySchedulesAcrossShortMonths() {
        assertThat(RecurringService.nextDate(LocalDate.of(2026, 1, 31), Frequency.MONTHLY))
                .isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(RecurringService.nextDate(LocalDate.of(2024, 1, 31), Frequency.MONTHLY))
                .isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    void advancesDailyAndWeeklySchedules() {
        assertThat(RecurringService.nextDate(LocalDate.of(2026, 11, 13), Frequency.DAILY))
                .isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(RecurringService.nextDate(LocalDate.of(2026, 11, 13), Frequency.WEEKLY))
                .isEqualTo(LocalDate.of(2026, 11, 20));
    }
}
