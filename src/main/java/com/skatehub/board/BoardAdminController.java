package com.skatehub.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.skatehub.util.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.io.IOException;

@RestController @RequestMapping("/api/admin/board-assistant") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class BoardAdminController {
    private final BoardAdminService service;
    private final BoardDatasetValidator json;
    @GetMapping("/{resource}") public ApiResponse<PageResult<JsonNode>> list(@PathVariable String resource,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return ApiResponse.success(service.list(resource,page,size));}
    @PostMapping(value="/{resource}",consumes="application/json") public ResponseEntity<ApiResponse<JsonNode>> create(@PathVariable String resource,HttpServletRequest r)throws IOException{return writeResponse(service.create(resource,json.read(r.getInputStream()),SecurityUtils.currentUser().id()));}
    @PatchMapping(value="/{resource}/{key}",consumes="application/json") public ResponseEntity<ApiResponse<JsonNode>> patch(@PathVariable String resource,@PathVariable String key,HttpServletRequest r)throws IOException{return writeResponse(service.patch(resource,key,json.read(r.getInputStream()),SecurityUtils.currentUser().id()));}
    @PostMapping(value="/{resource}/{key}/activation",consumes="application/json") public ApiResponse<JsonNode> activation(@PathVariable String resource,@PathVariable String key,HttpServletRequest r)throws IOException{return ApiResponse.success(service.activate(resource,key,json.read(r.getInputStream()),SecurityUtils.currentUser().id()));}
    @PostMapping(value="/trial",consumes="application/json") public ApiResponse<JsonNode> trial(HttpServletRequest r)throws IOException{return ApiResponse.success(service.trial(json.read(r.getInputStream())));}
    private ResponseEntity<ApiResponse<JsonNode>> writeResponse(JsonNode data){return "SELECTION_INVALID".equals(data.path("errorCode").asText())?ResponseEntity.badRequest().body(new ApiResponse<>(400,"目录字段校验失败",data)):ResponseEntity.ok(ApiResponse.success(data));}
}
