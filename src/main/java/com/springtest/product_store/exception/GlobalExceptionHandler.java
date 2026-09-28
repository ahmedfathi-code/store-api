package com.springtest.product_store.exception;



import com.springtest.product_store.dto.ErrorResponse;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(404)
                .body(new ErrorResponse(404, ex.getMessage(), LocalDateTime.now().toString()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(403)
                .body(new ErrorResponse(403, ex.getMessage(), LocalDateTime.now().toString()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(400)
                .body(new ErrorResponse(400, msg, LocalDateTime.now().toString()));
    }

    // Invalid request params, e.g. page < 0 or size < 1
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(HandlerMethodValidationException ex) {
        String msg = ex.getAllErrors()
                .stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(400)
                .body(new ErrorResponse(400, msg, LocalDateTime.now().toString()));
    }

    // Wrong param type, e.g. page=abc
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(400)
                .body(new ErrorResponse(400, "Invalid value for parameter '" + ex.getName() + "'",
                        LocalDateTime.now().toString()));
    }

    // Unknown sort property, e.g. sortBy=foo
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSort(PropertyReferenceException ex) {
        return ResponseEntity.status(400)
                .body(new ErrorResponse(400, "Invalid sort property '" + ex.getPropertyName() + "'",
                        LocalDateTime.now().toString()));
    }

    // Redis (token store) unreachable or timing out: same 503 as JwtAuthFilter returns
    @ExceptionHandler({RedisConnectionFailureException.class, QueryTimeoutException.class})
    public ResponseEntity<ErrorResponse> handleTokenStoreUnavailable(RuntimeException ex) {
        return ResponseEntity.status(503)
                .body(new ErrorResponse(503, "Authentication service unavailable", LocalDateTime.now().toString()));
    }

    // Malformed or missing JSON body
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(400)
                .body(new ErrorResponse(400, "Malformed request body", LocalDateTime.now().toString()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        // Spring MVC's own exceptions (unknown path 404, wrong method 405, unsupported
        // media type 415, ...) carry their proper status; keep it instead of turning it into 500
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            HttpStatus known = HttpStatus.resolve(status.value());
            String reason = known != null ? known.getReasonPhrase() : "Request failed";
            return ResponseEntity.status(status)
                    .body(new ErrorResponse(status.value(), reason, LocalDateTime.now().toString()));
        }

        log.error("Unhandled exception", ex);
        return ResponseEntity.status(500)
                .body(new ErrorResponse(500, "Something went wrong", LocalDateTime.now().toString()));
    }
}