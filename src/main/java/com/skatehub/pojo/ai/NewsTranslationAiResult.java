package com.skatehub.pojo.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsTranslationAiResult {

    private String translatedTitle;
    private String translatedContent;
}
