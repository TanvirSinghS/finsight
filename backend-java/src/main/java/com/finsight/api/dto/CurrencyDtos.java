package com.finsight.api.dto;

import java.math.BigDecimal;
import java.util.Map;

public final class CurrencyDtos {
    private CurrencyDtos() {
    }

    public record RatesResponse(String base, String date, Map<String, BigDecimal> rates, boolean cached,
                                boolean stale) {
    }
}
