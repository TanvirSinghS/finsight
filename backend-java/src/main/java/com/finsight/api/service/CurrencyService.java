package com.finsight.api.service;

import com.finsight.api.dto.CurrencyDtos.RatesResponse;
import com.finsight.api.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live FX rates from the free Frankfurter API, cached in memory. When the upstream API is
 * unreachable an approximate static table is used so balances never break the UI.
 */
@Service
public class CurrencyService {
    private static final Logger log = LoggerFactory.getLogger(CurrencyService.class);
    private static final String[] SUPPORTED = {"USD", "EUR", "GBP", "JPY", "INR", "CAD", "AUD", "CHF", "CNY",
            "SEK", "NOK", "SGD", "NZD", "ZAR", "BRL", "MXN", "KRW", "TRY", "AED", "PLN", "DKK"};

    // Approximate USD rates, only used if Frankfurter is unreachable.
    private static final Map<String, BigDecimal> FALLBACK = Map.ofEntries(
            Map.entry("USD", BigDecimal.ONE), Map.entry("EUR", new BigDecimal("0.92")),
            Map.entry("GBP", new BigDecimal("0.79")), Map.entry("JPY", new BigDecimal("151.0")),
            Map.entry("INR", new BigDecimal("83.2")), Map.entry("CAD", new BigDecimal("1.36")),
            Map.entry("AUD", new BigDecimal("1.52")), Map.entry("CHF", new BigDecimal("0.89")),
            Map.entry("CNY", new BigDecimal("7.24")), Map.entry("SEK", new BigDecimal("10.5")),
            Map.entry("NOK", new BigDecimal("10.7")), Map.entry("SGD", new BigDecimal("1.34")),
            Map.entry("NZD", new BigDecimal("1.64")), Map.entry("ZAR", new BigDecimal("18.6")),
            Map.entry("BRL", new BigDecimal("5.05")), Map.entry("MXN", new BigDecimal("17.1")),
            Map.entry("KRW", new BigDecimal("1330.0")), Map.entry("TRY", new BigDecimal("32.5")),
            Map.entry("AED", new BigDecimal("3.67")), Map.entry("PLN", new BigDecimal("4.0")),
            Map.entry("DKK", new BigDecimal("6.9")));

    private record CachedRates(Instant fetchedAt, String date, Map<String, BigDecimal> rates) {
    }

    private record FrankfurterResponse(String base, String date, Map<String, BigDecimal> rates) {
    }

    private final RestClient client;
    private final Duration ttl;
    private final AccountRepository accountRepository;
    private final Map<String, CachedRates> cache = new ConcurrentHashMap<>();

    public CurrencyService(@Qualifier("currencyRestClient") RestClient client,
                           @Value("${app.currency.cache-minutes:360}") long cacheMinutes,
                           AccountRepository accountRepository) {
        this.client = client;
        this.ttl = Duration.ofMinutes(cacheMinutes);
        this.accountRepository = accountRepository;
    }

    /**
     * Fetch the rates this installation actually needs while the instance is still
     * booting, so the first dashboard request after a cold start does not sit on the
     * FX provider (a 3-6s stall on a free-tier container). Runs off the startup
     * thread, so boot time and the health check never wait on the network.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void prefetchRates() {
        List<String> currencies;
        try {
            currencies = accountRepository.findDistinctCurrencies();
        } catch (RuntimeException ex) {
            log.warn("Skipping FX prefetch: {}", ex.getMessage());
            return;
        }
        CompletableFuture.runAsync(() -> currencies.stream()
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .forEach(code -> {
                    try {
                        rate(code, "USD");
                    } catch (RuntimeException ex) {
                        log.debug("FX prefetch for {} failed: {}", code, ex.getMessage());
                    }
                }));
    }

    public static Map<String, String> supportedCurrencies() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("USD", "US Dollar");
        names.put("EUR", "Euro");
        names.put("GBP", "British Pound");
        names.put("JPY", "Japanese Yen");
        names.put("INR", "Indian Rupee");
        names.put("CAD", "Canadian Dollar");
        names.put("AUD", "Australian Dollar");
        names.put("CHF", "Swiss Franc");
        names.put("CNY", "Chinese Yuan");
        names.put("SGD", "Singapore Dollar");
        names.put("NZD", "New Zealand Dollar");
        names.put("ZAR", "South African Rand");
        names.put("BRL", "Brazilian Real");
        names.put("MXN", "Mexican Peso");
        names.put("KRW", "South Korean Won");
        names.put("SEK", "Swedish Krona");
        names.put("NOK", "Norwegian Krone");
        names.put("PLN", "Polish Zloty");
        names.put("DKK", "Danish Krone");
        names.put("AED", "UAE Dirham");
        names.put("TRY", "Turkish Lira");
        return names;
    }

    public RatesResponse getRates(String baseCurrency) {
        String base = baseCurrency.toUpperCase(Locale.ROOT);
        CachedRates cached = cache.get(base);
        boolean fresh = cached != null && cached.fetchedAt().plus(ttl).isAfter(Instant.now());
        if (fresh) {
            return new RatesResponse(base, cached.date(), cached.rates(), true, false);
        }
        try {
            CachedRates loaded = fetch(base);
            cache.put(base, loaded);
            return new RatesResponse(base, loaded.date(), loaded.rates(), false, false);
        } catch (RestClientException ex) {
            log.warn("Frankfurter unavailable for {}: {}", base, ex.getMessage());
            Map<String, BigDecimal> fallback = fallbackRates(base);
            return new RatesResponse(base, "offline", fallback, false, true);
        }
    }

    public BigDecimal rate(String from, String to) {
        String source = from == null ? "USD" : from.toUpperCase(Locale.ROOT);
        String target = to == null ? "USD" : to.toUpperCase(Locale.ROOT);
        if (source.equals(target)) {
            return BigDecimal.ONE;
        }
        Map<String, BigDecimal> rates;
        try {
            CachedRates cached = cache.get(source);
            if (cached != null && cached.fetchedAt().plus(ttl).isAfter(Instant.now())) {
                rates = cached.rates();
            } else {
                CachedRates loaded = fetch(source);
                cache.put(source, loaded);
                rates = loaded.rates();
            }
        } catch (RestClientException ex) {
            log.warn("FX lookup failed ({} -> {}), using fallback rates", source, target);
            rates = fallbackRates(source);
        }
        BigDecimal value = rates.get(target);
        if (value == null) {
            value = crossRate(source, target);
        }
        return value == null ? BigDecimal.ONE : value;
    }

    public BigDecimal convert(BigDecimal amount, String from, String to) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        if (from == null || to == null || from.equalsIgnoreCase(to)) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.multiply(rate(from, to)).setScale(2, RoundingMode.HALF_UP);
    }

    private CachedRates fetch(String base) {
        FrankfurterResponse response = client.get()
                .uri(uri -> uri.path("/latest").queryParam("from", base).build())
                .retrieve()
                .body(FrankfurterResponse.class);
        if (response == null || response.rates() == null) {
            throw new RestClientException("Empty FX response");
        }
        Map<String, BigDecimal> rates = new LinkedHashMap<>(response.rates());
        rates.put(base, BigDecimal.ONE);
        return new CachedRates(Instant.now(), response.date() == null ? "unknown" : response.date(),
                Map.copyOf(rates));
    }

    private Map<String, BigDecimal> fallbackRates(String base) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (String code : SUPPORTED) {
            BigDecimal value = crossRate(base, code);
            if (value != null) {
                result.put(code, value);
            }
        }
        result.put(base, BigDecimal.ONE);
        return Map.copyOf(result);
    }

    private BigDecimal crossRate(String from, String to) {
        BigDecimal fromUsd = FALLBACK.get(from);
        BigDecimal toUsd = FALLBACK.get(to);
        if (fromUsd == null || toUsd == null) {
            return null;
        }
        return toUsd.divide(fromUsd, 6, RoundingMode.HALF_UP);
    }
}
