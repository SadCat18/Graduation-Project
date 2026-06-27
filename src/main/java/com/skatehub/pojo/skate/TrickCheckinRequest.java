package com.skatehub.pojo.skate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TrickCheckinRequest {

    @Min(1)
    @Max(600)
    private Integer practiceMinutes;

    @Pattern(regexp = "NOT_STARTED|PRACTICING|BASIC|SKILLED", message = "掌握状态不合法")
    private String masterStatus;

    @Size(max = 1000)
    private String content;

    @Size(max = 1000)
    private String mediaUrls;

    @Pattern(regexp = "0|1", message = "公开状态不合法")
    private String isPublic;
}
