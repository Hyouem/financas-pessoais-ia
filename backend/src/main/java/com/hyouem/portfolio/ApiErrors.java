package com.hyouem.portfolio;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<?> badInput(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()==null?"Invalid input":e.getMessage()));
  }
  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  ResponseEntity<?> integrity() {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","Conflict or invalid reference"));
  }
}
