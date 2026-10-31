package com.foliotrack.web;

import com.foliotrack.dto.ApiError;
import com.foliotrack.dto.ApiError.FieldViolation;
import com.foliotrack.exception.InvalidOrderException;
import com.foliotrack.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.List;

// @RestControllerAdvice applies these handlers to every controller, so error handling lives in one place
// instead of try/catch in each endpoint. Extending ResponseEntityExceptionHandler means Spring MVC's own
// errors (malformed JSON, wrong type in a path variable, unknown URL, unsupported method...) keep their
// correct status codes; we only override how the body looks, so every error has the same ApiError shape.
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> handleNotFound(NotFoundException ex, WebRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<Object> handleInvalidOrder(InvalidOrderException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, List.of());
    }

    // A DB constraint (CHECK, UNIQUE, FK, numeric range) refused the write. The transaction has rolled back,
    // so we say nothing was saved. The SQL detail is logged, never sent to the client.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "Request violates a data constraint; no changes were saved", request, List.of());
    }

    // Safety net: anything unexpected becomes a generic 500 without leaking a stack trace to the client
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request, List.of());
    }

    // @Valid @RequestBody failures: report every invalid field, not just the first
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return build(status, "Validation failed", request, violations);
    }

    // Constraint failures on @RequestParam / @PathVariable, e.g. size=500 on the history endpoints
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        List<FieldViolation> violations = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return build(status, "Validation failed", request, violations);
    }

    // Invalid JSON, or a value Jackson can't convert (e.g. "side": "HOLD"). The default text, "Failed to read
    // request", doesn't tell the client what to fix; the exception's own message would leak Java class names.
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return build(status, "Request body is missing or malformed: check JSON syntax and field values "
                + "(e.g. side must be BUY or SELL)", request, List.of());
    }

    // Every other Spring MVC exception funnels through here. The base class passes a ProblemDetail
    // whose "detail" is a client-safe description (e.g. "Failed to read request"), which we reuse.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        String message = body instanceof ProblemDetail problem && problem.getDetail() != null
                ? problem.getDetail()
                : HttpStatus.valueOf(status.value()).getReasonPhrase();
        // Keep headers the base class set, e.g. "Allow" on a 405
        return ResponseEntity.status(status).headers(headers).body(apiError(status, message, request, List.of()));
    }

    private static ResponseEntity<Object> build(HttpStatusCode status, String message, WebRequest request,
                                                List<FieldViolation> fieldErrors) {
        return ResponseEntity.status(status).body(apiError(status, message, request, fieldErrors));
    }

    private static ApiError apiError(HttpStatusCode status, String message, WebRequest request,
                                     List<FieldViolation> fieldErrors) {
        return new ApiError(
                Instant.now(),
                status.value(),
                HttpStatus.valueOf(status.value()).getReasonPhrase(),
                message,
                ((ServletWebRequest) request).getRequest().getRequestURI(),
                fieldErrors);
    }
}
