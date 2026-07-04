package com.example.restapi.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, WebRequest request) {
		return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex, WebRequest request) {
		return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
		Map<String, String> validationErrors = new HashMap<>();
		for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			validationErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
		}
		return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed", request, validationErrors);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, WebRequest request) {
		return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request, null);
	}

	private ResponseEntity<ErrorResponse> buildResponse(
			HttpStatus status,
			String message,
			WebRequest request,
			Map<String, String> validationErrors) {
		ErrorResponse body = new ErrorResponse(
				Instant.now(),
				status.value(),
				status.getReasonPhrase(),
				message,
				request.getDescription(false).replace("uri=", ""),
				validationErrors);
		return ResponseEntity.status(status).body(body);
	}

}
