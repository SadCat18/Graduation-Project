package com.skatehub.pojo.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsAiReprocessBatchResponse {

    private int total;
    private int success;
    private int skipped;
    private int failed;
    private List<NewsAiReprocessItemResponse> items;
}
