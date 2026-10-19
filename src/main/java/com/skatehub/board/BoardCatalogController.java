package com.skatehub.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.skatehub.util.ApiResponse;
import com.skatehub.util.PageResult;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/skate/board-assistant") @RequiredArgsConstructor @Validated
public class BoardCatalogController {
    private final BoardCatalogService service;
    @GetMapping("/catalog")
    public ResponseEntity<ApiResponse<PageResult<JsonNode>>> catalog(@RequestParam(required=false) String category,
            @RequestParam(defaultValue="1") @Min(1) @Max(10000) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(50) int size) {
        var result=service.catalog(category,page,size);
        return ResponseEntity.ok().header("X-Catalog-Version",service.catalogVersion()).body(ApiResponse.success(result));
    }
    @GetMapping("/variants/{key}")
    public ApiResponse<JsonNode> variant(@PathVariable @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._-]{0,199}") String key) {
        return ApiResponse.success(service.variant(key));
    }
}
