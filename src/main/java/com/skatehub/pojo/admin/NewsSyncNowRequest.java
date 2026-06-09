package com.skatehub.pojo.admin;

import lombok.Data;

@Data
public class NewsSyncNowRequest {

    private Integer sourceId;
    private Boolean dryRun;
}
