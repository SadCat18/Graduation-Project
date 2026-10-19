package com.skatehub.board;

import com.skatehub.util.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

@RestController @RequestMapping("/api/skate/board-assistant") @RequiredArgsConstructor
public class BoardCalculationController {
    private final BoardCalculationService service;
    private final BoardDatasetValidator json;
    @PostMapping(value="/recommend",consumes="application/json")
    public ApiResponse<JsonNode> recommend(HttpServletRequest request)throws IOException{return ApiResponse.success(service.publicView(service.recommend(json.read(request.getInputStream()))));}
    @PostMapping(value="/evaluate",consumes="application/json")
    public ApiResponse<JsonNode> evaluate(HttpServletRequest request)throws IOException{return ApiResponse.success(service.publicView(service.evaluate(json.read(request.getInputStream()))));}
    @PostMapping(value="/replacements",consumes="application/json")
    public ApiResponse<JsonNode> replacements(HttpServletRequest request)throws IOException{return ApiResponse.success(service.publicView(service.replacements(json.read(request.getInputStream()))));}
    @PostMapping(value="/compare",consumes="application/json")
    public ApiResponse<JsonNode> compare(HttpServletRequest request)throws IOException{return ApiResponse.success(service.publicView(service.compare(json.read(request.getInputStream()))));}
}
