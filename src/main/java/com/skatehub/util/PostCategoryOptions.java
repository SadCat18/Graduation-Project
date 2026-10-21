package com.skatehub.util;

import java.util.List;

/** Existing publish/AI suggestions only; historical arbitrary categories remain filterable, not new suggestions. */
public final class PostCategoryOptions {
    public static final List<String> CREATE=List.of("技巧交流","装备讨论","路线分享","活动讨论","经验分享");
    private PostCategoryOptions() {}
}
