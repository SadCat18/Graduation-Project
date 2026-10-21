package com.skatehub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skatehub.service.training.*;
import com.skatehub.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/user/training-guidance")
@RequiredArgsConstructor
public class TrainingGuidanceController {
    private final TrainingGuidanceService service;
    private final GuidanceContentService content;
    private final GuidanceAiService ai;
    @GetMapping("/templates") public ApiResponse<?> templates() { return ApiResponse.success(content.templates(false)); }
    @GetMapping("/templates/{id}") public ApiResponse<?> template(@PathVariable String id,@RequestParam(required=false) Long version) {return ApiResponse.success(content.template(id,version,false));}
    @PostMapping("/plans/preview") public ApiResponse<?> preview(@RequestBody ObjectNode b) {return ApiResponse.success(service.preview(b));}
    @PostMapping("/plans") public ApiResponse<?> create(@RequestBody ObjectNode b) {return ApiResponse.success(service.create(SecurityUtils.currentUser(),b));}
    @GetMapping("/current") public ObjectNode current() {return GuidanceJson.obj("code",200,"message","success","data",service.current(SecurityUtils.currentUser()));}
    @GetMapping("/plans") public ApiResponse<?> history(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {return ApiResponse.success(service.history(SecurityUtils.currentUser(),page,size));}
    @GetMapping("/plans/{id}") public ApiResponse<?> plan(@PathVariable long id) {return ApiResponse.success(service.plan(SecurityUtils.currentUser(),id));}
    @GetMapping("/plans/{id}/today") public ApiResponse<?> today(@PathVariable long id,@RequestParam(required=false) LocalDate date) {return ApiResponse.success(service.todayView(SecurityUtils.currentUser(),id,date==null?service.today():date));}
    @GetMapping("/plans/{id}/tasks/{instanceId}") public ApiResponse<?> task(@PathVariable long id,@PathVariable String instanceId) {return ApiResponse.success(service.task(SecurityUtils.currentUser(),id,instanceId));}
    @PostMapping("/plans/{id}/feedback") public ApiResponse<?> feedback(@PathVariable long id,@RequestBody ObjectNode b) {return ApiResponse.success(service.feedback(SecurityUtils.currentUser(),id,b));}
    @GetMapping("/plans/{id}/records") public ApiResponse<?> records(@PathVariable long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {return ApiResponse.success(service.records(SecurityUtils.currentUser(),id,page,size));}
    @GetMapping("/plans/{id}/records/{recordId}") public ApiResponse<?> record(@PathVariable long id,@PathVariable String recordId) {return ApiResponse.success(service.record(SecurityUtils.currentUser(),id,recordId));}
    @GetMapping("/plans/{id}/statistics") public ApiResponse<?> stats(@PathVariable long id) {return ApiResponse.success(service.statistics(SecurityUtils.currentUser(),id));}
    @GetMapping("/plans/{id}/suggestions") public ApiResponse<?> suggestions(@PathVariable long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {return ApiResponse.success(service.suggestions(SecurityUtils.currentUser(),id,page,size));}
    @PostMapping("/plans/{id}/suggestions/{suggestionId}/decision") public ApiResponse<?> decision(@PathVariable long id,@PathVariable String suggestionId,@RequestBody ObjectNode b) {return ApiResponse.success(service.decision(SecurityUtils.currentUser(),id,suggestionId,b));}
    @PostMapping("/plans/{id}/state") public ApiResponse<?> state(@PathVariable long id,@RequestBody ObjectNode b) {return ApiResponse.success(service.state(SecurityUtils.currentUser(),id,b));}
    @GetMapping("/plans/{id}/adjustments") public ApiResponse<?> adjustments(@PathVariable long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {return ApiResponse.success(service.adjustments(SecurityUtils.currentUser(),id,page,size));}
    @PostMapping("/plans/{id}/ai-context") public ApiResponse<?> context(@PathVariable long id,@RequestBody ObjectNode b) {return ApiResponse.success(service.aiContext(SecurityUtils.currentUser(),id,b));}
    @PostMapping("/plans/{id}/ai-explain") public ApiResponse<?> explain(@PathVariable long id,@RequestBody ObjectNode b) {return ApiResponse.success(ai.explain(SecurityUtils.currentUser(),id,b));}
}
