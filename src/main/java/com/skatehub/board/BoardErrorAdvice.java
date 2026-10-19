package com.skatehub.board;

import com.skatehub.util.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackageClasses=BoardCatalogController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BoardErrorAdvice {
    @ExceptionHandler(BoardRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handle(BoardRequestException error) {
        return ResponseEntity.status(error.status()).body(ApiResponse.fail(error.status(),error.getMessage()));
    }
}
