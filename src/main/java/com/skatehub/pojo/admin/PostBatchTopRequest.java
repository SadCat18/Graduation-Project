package com.skatehub.pojo.admin;

import lombok.Data;

import java.util.List;

@Data
public class PostBatchTopRequest {

    private List<Long> postIds;
    private String top;
}
