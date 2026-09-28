package com.springtest.product_store.exception;



import com.springtest.product_store.dto.ErrorResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

// Messages come from messages*.properties in the request's language (Accept-Language);
// validation messages arrive already translated by the validator
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    private String message(String code, Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    private static ResponseEntity<ErrorResponse> error(int status, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status, message, LocalDateTime.now().toString()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return error(404, messageSource.getMessage(ex, LocaleContextHolder.getLocale()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        return error(403, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return error(400, msg);
    }

    // Invalid request params, e.g. page < 0 or size < 1
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(HandlerMethodValidationException ex) {
        String msg = ex.getAllErrors()
                .stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return error(400, msg);
    }

    // Wrong param type, e.g. page=abc
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(400, message("error.invalidParam", ex.getName()));
    }

    // Unknown sort property, e.g. sortBy=foo (only plain identifiers get this far)
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSort(PropertyReferenceException ex) {
        return error(400, message("error.invalidSort", ex.getPropertyName()));
    }

    // Too many failed password attempts
    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyAttempts(TooManyAttemptsException ex) {
        String seconds = String.valueOf(ex.getRetryAfterSeconds());
        return ResponseEntity.status(429)
                .header(HttpHeaders.RETRY_AFTER, seconds)
                .body(new ErrorResponse(429, message("rateLimit.tooManyAttempts", seconds),
                        LocalDateTime.now().toString()));
    }

    // Redis (token store) unreachable or timing out: same 503 as JwtAuthFilter returns
    @ExceptionHandler({RedisConnectionFailureException.class, QueryTimeoutException.class})
    public ResponseEntity<ErrorResponse> handleTokenStoreUnavailable(RuntimeException ex) {
        return error(503, message("auth.unavailable"));
    }

    // Malformed or missing JSON body
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return error(400, message("error.malformedBody"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        // Spring MVC's own exceptions (unknown path 404, wrong method 405, unsupported
        // media type 415, ...) carry their proper status; keep it instead of turning it into 500
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            String reason = messageSource.getMessage("error.http." + status.value(), null,
                    message("error.http.other"), LocaleContextHolder.getLocale());
            return error(status.value(), reason);
        }

        log.error("Unhandled exception", ex);
        return error(500, message("error.unexpected"));
    }
}
