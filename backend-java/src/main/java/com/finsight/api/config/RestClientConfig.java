package com.finsight.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient insightsRestClient(@Value("${app.insights.url}") String baseUrl,
                                         @Value("${app.insights.timeout-seconds}") int timeoutSeconds) {
        return buildClient(baseUrl, 4000, timeoutSeconds * 1000);
    }

    @Bean
    public RestClient currencyRestClient(@Value("${app.currency.api-url}") String baseUrl) {
        return buildClient(baseUrl, 4000, 8000);
    }

    private RestClient buildClient(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
