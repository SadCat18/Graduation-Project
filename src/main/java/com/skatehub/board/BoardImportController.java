package com.skatehub.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.skatehub.util.ApiResponse;
import com.skatehub.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import static com.skatehub.board.BoardDtos.*;
import static com.skatehub.board.BoardDatasetValidator.text;

@RestController @RequestMapping("/api/admin/board-assistant") @RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class BoardImportController {
    private final BoardCatalogService service;
    private final BoardDatasetValidator datasets;
    private final Validator beanValidator;
    @PostMapping(value="/import/validate",consumes="application/json")
    public ApiResponse<ValidationReport> validate(HttpServletRequest request) throws IOException {
        return ApiResponse.success(service.validate(datasets.read(request.getInputStream())));
    }
    @PostMapping(value="/import",consumes="application/json")
    public ResponseEntity<ApiResponse<ImportResult>> importDataset(HttpServletRequest request) throws IOException {
        JsonNode body=datasets.read(request.getInputStream());
        if(!body.isObject()||body.size()!=2||!body.has("dataset")||!body.has("expectedCatalogVersion"))throw new BoardRequestException(400,"导入请求字段错误");
        ImportRequest input=new ImportRequest(body.get("dataset"),text(body,"expectedCatalogVersion"));
        if(!beanValidator.validate(input).isEmpty())throw new BoardRequestException(400,"数据集与目录版本必填且格式正确");
        ImportResult result=service.importDataset(input.dataset(),input.expectedCatalogVersion(),SecurityUtils.currentUser().id());
        if("SELECTION_INVALID".equals(result.errorCode()))return ResponseEntity.badRequest().body(new ApiResponse<>(400,"数据校验失败",result));
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
