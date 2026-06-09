package com.skatehub.pojo.news;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsSyncItem {

    private String title;
    private String content;
    private String summary;
    private String cover;
    private String category;
    private String sourceName;
    private String sourceUrl;
    private LocalDate publishedDate;
}
