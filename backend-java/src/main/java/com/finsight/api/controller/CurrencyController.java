package com.finsight.api.controller;

import com.finsight.api.dto.CurrencyDtos.RatesResponse;
import com.finsight.api.service.CurrencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/currency")
@Tag(name = "Currency", description = "Live FX rates from Frankfurter (free, no API key)")
public class CurrencyController {
    private final CurrencyService currencyService;

    public CurrencyController(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    @GetMapping("/rates")
    @Operation(summary = "Latest rates for a base currency, cached in memory")
    public RatesResponse rates(@RequestParam(defaultValue = "USD") String base) {
        return currencyService.getRates(base);
    }

    @GetMapping("/supported")
    @Operation(summary = "Supported currency codes")
    public Map<String, String> supported() {
        return CurrencyService.supportedCurrencies();
    }
}
