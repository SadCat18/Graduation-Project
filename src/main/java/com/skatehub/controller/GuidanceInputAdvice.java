package com.skatehub.controller;
import com.skatehub.util.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.Map;
@RestControllerAdvice(assignableTypes={TrainingGuidanceController.class,AdminTrainingGuidanceController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GuidanceInputAdvice {
 @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
 public ResponseEntity<ApiResponse<?>> invalid(Exception e) {return ResponseEntity.badRequest().body(new ApiResponse<>(400,"请求结构或字段类型无效",Map.of("errorCode","VALIDATION_ERROR")));}
}
