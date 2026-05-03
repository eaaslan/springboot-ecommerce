package com.backendguru.paymentservice.payment.iyzico;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Iyzico SDK config. Set via env vars when deploying: IYZICO_API_KEY=sandbox-...
 * IYZICO_SECRET_KEY=sandbox-... IYZICO_BASE_URL=https://sandbox-api.iyzipay.com
 *
 * <p>If apiKey is blank/missing, the service falls back to its built-in mock so the stack still
 * boots without credentials.
 */
@ConfigurationProperties(prefix = "iyzico")
public record IyzicoProperties(String apiKey, String secretKey, String baseUrl) {

  public boolean isConfigured() {
    return apiKey != null
        && !apiKey.isBlank()
        && secretKey != null
        && !secretKey.isBlank()
        && baseUrl != null
        && !baseUrl.isBlank();
  }
}
