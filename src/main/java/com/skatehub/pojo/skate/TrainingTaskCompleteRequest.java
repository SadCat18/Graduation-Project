package com.skatehub.pojo.skate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TrainingTaskCompleteRequest {

    @Min(1)
    @Max(600)
    private Integer practiceMinutes;

    @Size(max = 1000)
    private String content;

    @Size(max = 1000)
    private String mediaUrls;
}
