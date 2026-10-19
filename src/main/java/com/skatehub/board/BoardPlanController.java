package com.skatehub.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.skatehub.util.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.io.IOException;

@RestController @RequestMapping("/api/user/board-plans") @RequiredArgsConstructor @PreAuthorize("hasRole('USER')")
public class BoardPlanController {
    private final BoardPlanService service;
    private final BoardDatasetValidator json;
    private Long owner(){return SecurityUtils.currentUser().id();}
    @GetMapping public ApiResponse<PageResult<JsonNode>> list(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return ApiResponse.success(service.list(owner(),page,size));}
    @PostMapping(consumes="application/json") public ApiResponse<JsonNode> save(HttpServletRequest r)throws IOException{return ApiResponse.success(service.save(owner(),json.read(r.getInputStream())));}
    @GetMapping("/{key}") public ApiResponse<JsonNode> detail(@PathVariable String key){return ApiResponse.success(service.detail(owner(),key));}
    @PatchMapping(value="/{key}",consumes="application/json") public ApiResponse<JsonNode> patch(@PathVariable String key,HttpServletRequest r)throws IOException{return ApiResponse.success(service.patch(owner(),key,json.read(r.getInputStream())));}
    @PostMapping(value="/{key}/copy",consumes="application/json") public ApiResponse<JsonNode> copy(@PathVariable String key,HttpServletRequest r)throws IOException{return ApiResponse.success(service.copy(owner(),key,json.read(r.getInputStream())));}
    @DeleteMapping("/{key}") public ApiResponse<Void> delete(@PathVariable String key){service.delete(owner(),key);return ApiResponse.success();}
}
