package com.green.energy.tracker.cloud.site_bff.exception;

import com.green.energy.tracker.cloud.sitebff.web.model.ApiErrorDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteControllerAdviceTest {

    @InjectMocks
    private SiteControllerAdvice siteControllerAdvice;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ServerWebExchange exchange;

    @BeforeEach
    void setUp() {
        when(exchange.getRequest().getPath().value()).thenReturn("/test-path");
    }

    @Test
    void handleResponseStatusException_shouldReturnCorrectApiError() {
        // Arrange
        var status = HttpStatus.NOT_FOUND;
        var reason = "Site with the given ID was not found";
        var exception = new ResponseStatusException(status, reason);

        // Act
        Mono<ResponseEntity<ApiErrorDto>> result = siteControllerAdvice.handleResponseStatusException(exception, exchange);

        // Assert
        StepVerifier.create(result)
                .assertNext(responseEntity -> {
                    assertEquals(status, responseEntity.getStatusCode());
                    ApiErrorDto apiError = responseEntity.getBody();
                    assertNotNull(apiError);
                    assertEquals(status.value(), apiError.getStatus());
                    assertEquals(status.getReasonPhrase(), apiError.getError());
                    assertEquals(reason, apiError.getMessage());
                    assertEquals("/test-path", apiError.getPath());
                    assertNotNull(apiError.getTimestamp());
                })
                .verifyComplete();
    }

    @Test
    void handleValidationErrors_shouldReturnApiErrorWithValidationDetails() throws NoSuchMethodException {
        // Arrange
        BindingResult bindingResult = mock(BindingResult.class);
        var fieldError = new FieldError("siteRequestDto", "name", "must not be blank");
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

        var methodParameter = new MethodParameter(this.getClass().getDeclaredMethod("setUp"), -1);
        var exception = new WebExchangeBindException(methodParameter, bindingResult);

        // Act
        Mono<ResponseEntity<ApiErrorDto>> result = siteControllerAdvice.handleValidationErrors(exception, exchange);

        // Assert
        StepVerifier.create(result)
                .assertNext(responseEntity -> {
                    assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
                    ApiErrorDto apiError = responseEntity.getBody();
                    assertNotNull(apiError);
                    assertEquals(HttpStatus.BAD_REQUEST.value(), apiError.getStatus());
                    assertEquals("Validation failed for request", apiError.getMessage());
                    assertEquals("/test-path", apiError.getPath());
                    assertNotNull(apiError.getValidationErrors());
                    assertEquals(1, apiError.getValidationErrors().size());
                    assertEquals("must not be blank", apiError.getValidationErrors().get("name"));
                })
                .verifyComplete();
    }

    @Test
    void handleSiteProcessingException_shouldReturnServiceUnavailable() {
        // Arrange
        var errorMessage = "Failed to publish event to Pub/Sub";
        var exception = new SiteProcessingException(errorMessage);

        // Act
        Mono<ResponseEntity<ApiErrorDto>> result = siteControllerAdvice.handleSiteProcessingException(exception, exchange);

        // Assert
        StepVerifier.create(result)
                .assertNext(responseEntity -> {
                    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, responseEntity.getStatusCode());
                    ApiErrorDto apiError = responseEntity.getBody();
                    assertNotNull(apiError);
                    assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), apiError.getStatus());
                    assertEquals("Site Processing Error", apiError.getError());
                    assertEquals(errorMessage, apiError.getMessage());
                    assertEquals("/test-path", apiError.getPath());
                })
                .verifyComplete();
    }

    @Test
    void handleGenericException_shouldReturnInternalServerError() {
        // Arrange
        var exception = new RuntimeException("An unexpected database error occurred");

        // Act
        Mono<ResponseEntity<ApiErrorDto>> result = siteControllerAdvice.handleGenericException(exception, exchange);

        // Assert
        StepVerifier.create(result)
                .assertNext(responseEntity -> {
                    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
                    ApiErrorDto apiError = responseEntity.getBody();
                    assertNotNull(apiError);
                    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), apiError.getStatus());
                    assertEquals("An unexpected error occurred. Please contact support.", apiError.getMessage());
                    assertEquals("/test-path", apiError.getPath());
                    // FIX: Assert that the map is empty, not null, to match the DTO's behavior.
                    assertNotNull(apiError.getValidationErrors());
                    assertTrue(apiError.getValidationErrors().isEmpty());
                })
                .verifyComplete();
    }

    @Test
    void handleValidationErrors_withNullDefaultMessage_shouldUseUnknownError() throws NoSuchMethodException {
        // Arrange
        BindingResult bindingResult = mock(BindingResult.class);
        var fieldError = new FieldError("siteRequestDto", "name", null); // null default message
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

        var methodParameter = new MethodParameter(this.getClass().getDeclaredMethod("setUp"), -1);
        var exception = new WebExchangeBindException(methodParameter, bindingResult);

        // Act
        Mono<ResponseEntity<ApiErrorDto>> result = siteControllerAdvice.handleValidationErrors(exception, exchange);

        // Assert
        StepVerifier.create(result)
                .assertNext(responseEntity -> {
                    assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
                    ApiErrorDto apiError = responseEntity.getBody();
                    assertNotNull(apiError);
                    assertNotNull(apiError.getValidationErrors());
                    assertEquals("Unknown error", apiError.getValidationErrors().get("name"));
                })
                .verifyComplete();
    }

    @Test
    void handleResponseStatusException_withNonHttpStatusCode_shouldUseToString() {
        // Arrange
        HttpStatusCode customStatus = HttpStatusCode.valueOf(499);
        var reason = "Custom error reason";
        var exception = new ResponseStatusException(customStatus, reason);

        // Act
        Mono<ResponseEntity<ApiErrorDto>> result = siteControllerAdvice.handleResponseStatusException(exception, exchange);

        // Assert
        StepVerifier.create(result)
                .assertNext(responseEntity -> {
                    assertEquals(customStatus, responseEntity.getStatusCode());
                    ApiErrorDto apiError = responseEntity.getBody();
                    assertNotNull(apiError);
                    assertEquals(499, apiError.getStatus());
                    assertEquals("499", apiError.getError());
                    assertEquals(reason, apiError.getMessage());
                    assertEquals("/test-path", apiError.getPath());
                })
                .verifyComplete();
    }
}
