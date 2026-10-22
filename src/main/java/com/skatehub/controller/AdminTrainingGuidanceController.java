package com.skatehub.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skatehub.service.training.*;
import com.skatehub.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static com.skatehub.service.training.GuidanceJson.page;

@RestController
@RequestMapping("/api/admin/training-guidance")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminTrainingGuidanceController {
    private final GuidanceContentService content;
    @GetMapping("/templates") public ApiResponse<?> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {return ApiResponse.success(page(content.templates(true),page,size));}
    @GetMapping("/templates/{id}") public ApiResponse<?> detail(@PathVariable String id,@RequestParam(required=false) Long version) {return ApiResponse.success(content.template(id,version,true));}
    @PostMapping("/templates") public ApiResponse<?> create(@RequestBody ObjectNode b) {return ApiResponse.success(content.mutate(SecurityUtils.currentUser(),null,"create",b));}
    @PutMapping("/templates/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody ObjectNode b) {return ApiResponse.success(content.mutate(SecurityUtils.currentUser(),id,"edit",b));}
    @PostMapping("/templates/{id}/publication") public ApiResponse<?> publish(@PathVariable String id,@RequestBody ObjectNode b) {return ApiResponse.success(content.mutate(SecurityUtils.currentUser(),id,"publication",b));}
}
