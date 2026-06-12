package com.skatehub.pojo.admin;

import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class NewsSyncNowRequest {

    @Positive(message = "来源ID必须为正数")
    private Integer sourceId;
    private Boolean dryRun;
}
