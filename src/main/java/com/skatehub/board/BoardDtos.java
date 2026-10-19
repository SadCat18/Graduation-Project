package com.skatehub.board;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public final class BoardDtos {
    private BoardDtos() {}
    public record Issue(String path, String code, String detail) {}
    public record ValidationReport(boolean valid, List<Issue> errors, JsonNode gaps, String catalogVersion) {}
    public record ImportRequest(@NotNull JsonNode dataset,
                                @NotBlank @Pattern(regexp="[a-f0-9]{64}") String expectedCatalogVersion) {}
    public record ImportResult(long created, long updated, long unchanged, long rejected,
                               List<Issue> errors, JsonNode gaps, String errorCode, String catalogVersion) {}
}
