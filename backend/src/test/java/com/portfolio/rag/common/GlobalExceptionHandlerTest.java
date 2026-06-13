package com.portfolio.rag.common;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @SuppressWarnings("unused")
    private void dummy(String arg) {
        // only used to build a MethodParameter for MethodArgumentNotValidException
    }

    @Test
    void validationError_maps400WithFieldMessage() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "must not be blank"));
        MethodParameter parameter =
                new MethodParameter(getClass().getDeclaredMethod("dummy", String.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ApiError> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().message()).isEqualTo("email: must not be blank");
    }

    @Test
    void dataIntegrityViolation_maps409EmailAlreadyExists() {
        ResponseEntity<ApiError> response =
                handler.handleDataIntegrity(new DataIntegrityViolationException("duplicate key"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("EMAIL_ALREADY_EXISTS");
    }

    @Test
    void entityNotFound_maps404() {
        ResponseEntity<ApiError> response =
                handler.handleNotFound(new EntityNotFoundException("文档不存在"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("文档不存在");
    }

    @Test
    void maxUploadSizeExceeded_maps413() {
        ResponseEntity<ApiError> response =
                handler.handleMaxUpload(new MaxUploadSizeExceededException(20L * 1024 * 1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().code()).isEqualTo("PAYLOAD_TOO_LARGE");
    }

    @Test
    void apiException_mapsCarriedStatusAndCode() {
        ApiException ex = new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "邮箱或密码错误");

        ResponseEntity<ApiError> response = handler.handleApiException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("BAD_CREDENTIALS");
        assertThat(response.getBody().message()).isEqualTo("邮箱或密码错误");
    }

    @Test
    void genericException_maps500WithoutLeakingDetails() {
        ResponseEntity<ApiError> response =
                handler.handleGeneric(new IllegalStateException("secret internal detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).doesNotContain("secret internal detail");
    }

    @Test
    void errorBody_includesTimestamp() {
        ResponseEntity<ApiError> response = handler.handleGeneric(new RuntimeException("boom"));

        assertThat(response.getBody().timestamp()).isNotNull();
    }
}
