package com.example.banking.service;

import com.example.banking.config.PaymentProcessorProperties;
import com.example.banking.exception.PaymentProcessorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentService using a mocked RestTemplate.
 *
 * Covers: API key header forwarded, 5xx error → exception, network timeout → exception.
 */
@SuppressWarnings("unchecked")
class PaymentServiceTest {

    private RestTemplate http;
    private PaymentService svc;

    @BeforeEach
    void setUp() {
        http = mock(RestTemplate.class);
        PaymentProcessorProperties props =
                new PaymentProcessorProperties("http://localhost:8089", "test-secret-key", 3000, 3000);
        svc = new PaymentService(http, props);
    }

    // ------------------------------------------------------------------ happy path

    @Test
    void successful_call_does_not_throw() {
        when(http.exchange(eq("/payments"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok("{}"));

        svc.submitExternalTransfer("acc_1", "ext_acc", new BigDecimal("100.00"), "USD", "idem-123");

        verify(http).exchange(eq("/payments"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    // ------------------------------------------------------------------ API key forwarded in header

    @Test
    void api_key_is_sent_in_x_processor_key_header() {
        ArgumentCaptor<HttpEntity<Object>> captor = ArgumentCaptor.forClass((Class) HttpEntity.class);
        when(http.exchange(eq("/payments"), eq(HttpMethod.POST), captor.capture(), eq(String.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok("{}"));

        svc.submitExternalTransfer("acc_1", "ext_acc", new BigDecimal("50.00"), "USD", "idem-456");

        assertThat(captor.getValue().getHeaders().getFirst("X-Processor-Key"))
                .isEqualTo("test-secret-key");
    }

    // ------------------------------------------------------------------ 5xx → PaymentProcessorException

    @Test
    void http_5xx_from_processor_throws_payment_processor_exception() {
        when(http.exchange(eq("/payments"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() ->
                svc.submitExternalTransfer("acc_1", "ext_acc", new BigDecimal("75.00"), "USD", "idem-789"))
                .isInstanceOf(PaymentProcessorException.class);
    }

    // ------------------------------------------------------------------ timeout → PaymentProcessorException

    @Test
    void network_timeout_throws_payment_processor_exception() {
        when(http.exchange(eq("/payments"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Read timed out",
                        new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() ->
                svc.submitExternalTransfer("acc_1", "ext_acc", new BigDecimal("200.00"), "USD", "idem-000"))
                .isInstanceOf(PaymentProcessorException.class);
    }
}

    private RestTemplate http;
    private PaymentService svc;

    @BeforeEach
    void setUp() {
        http = mock(RestTemplate.class);
        PaymentProcessorProperties props =
                new PaymentProcessorProperties("http://localhost:8089", "test-secret-key", 3000, 3000);
        svc = new PaymentService(http, props);
    }

    // ------------------------------------------------------------------ happy path

    @Test
    void successful_call_does_not_throw() {
        when(http.exchange(eq("/payments"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok("{}"));

        // should not throw
        svc.submitExternalTransfer("acc_1", "ext_acc", new BigDecimal("100.00"), "USD", "idem-123");

        verify(http).exchange(eq("/payments"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    // ------------------------------------------------------------------ API key forwarded

    @Test
    void api_key_is_sent_in_x_processor_key_header() {
        when(http.exchange(eq("/payments"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenAnswer(inv -> {
                    HttpEntity<?> entity = inv.getArgument(2);
                    // Verify the secret key is in the header
                    assertThat(entity.getHeaders().getFirst("X-Processor-Key"))
                            .isEqualTo("test-secret-key");
                    return org.springframework.http.ResponseEntity.ok("{}");
                });

        svc.submitExternalTransfer("acc_1", "ext_acc", new BigDecimal("50.00"), "USD", "idem-456");
    }

    // ------------------------------------------------------------------ 5xx → PaymentProcessorException

    @Test
    void http_5xx_from_processor_throws_payment_processor_exception() {
        when(http.exchange(eq("/payments"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() ->
                svc.submitExternalTransfer("acc_1", "ext_acc", new BigDecimal("75.00"), "USD", "idem-789"))
                .isInstanceOf(PaymentProcessorException.class);
    }

    // ------------------------------------------------------------------ timeout → PaymentProcessorException

    @Test
    void network_timeout_throws_payment_processor_exception() {
        when(http.exchange(eq("/payments"), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Read timed out",
                        new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() ->
                svc.submitExternalTransfer("acc_1", "ext_acc", new BigDecimal("200.00"), "USD", "idem-000"))
                .isInstanceOf(PaymentProcessorException.class);
    }

    // ------------------------------------------------------------------ helper import shim

    private static <T> void assertThat(T actual) {
        // delegate to AssertJ — imported from outer test infrastructure
        org.assertj.core.api.Assertions.assertThat(actual);
    }

    private static org.assertj.core.api.AbstractStringAssert<?> assertThat(String actual) {
        return org.assertj.core.api.Assertions.assertThat(actual);
    }
}
