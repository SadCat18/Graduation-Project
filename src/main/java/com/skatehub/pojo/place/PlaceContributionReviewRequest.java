package com.skatehub.pojo.place;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PlaceContributionReviewRequest {

    @NotBlank(message = "Review status cannot be blank")
    @Pattern(regexp = "APPROVED|REJECTED", message = "Review status must be APPROVED or REJECTED")
    private String status;

    @Size(max = 200, message = "Review note must be at most 200 characters")
    private String note;
}
