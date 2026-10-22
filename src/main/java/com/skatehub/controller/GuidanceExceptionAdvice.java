package com.skatehub.controller;

import com.skatehub.service.training.GuidanceException;
import com.skatehub.util.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GuidanceExceptionAdvice {
    @ExceptionHandler(GuidanceException.class)
    public ResponseEntity<ApiResponse<?>> business(GuidanceException e) {var data=new java.util.LinkedHashMap<String,Object>(e.details);data.put("errorCode",e.errorCode);return ResponseEntity.status(e.status).body(new ApiResponse<>(e.status,e.getMessage(),data));}
}
