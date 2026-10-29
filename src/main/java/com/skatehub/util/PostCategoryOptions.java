package com.skatehub.util;

import java.util.*;

/** Canonical themes organize posts; category itself remains the original author's label. */
public final class PostCategoryOptions {
    public static final List<String> CREATE=List.of("新手入门","技巧交流","训练计划","装备讨论","安全经验","路线分享","活动讨论","经验分享");
    public static final Map<String,String> ALIASES=Map.of("入门","新手入门","技巧","技巧交流","训练","训练计划","装备","装备讨论","安全","安全经验");
    public static final String FALLBACK="经验分享";
    private PostCategoryOptions() {}
    public static String group(String category) {
        if(category==null||category.isBlank())return FALLBACK;
        if(ALIASES.containsKey(category))return ALIASES.get(category);
        return CREATE.contains(category)?category:FALLBACK;
    }
    public static List<String> expand(String requested,List<String> existing) {
        if(requested==null||requested.isBlank())return List.of();
        String canonical=ALIASES.getOrDefault(requested,requested);
        if(!CREATE.contains(canonical))return List.of(requested); // Culture etc. keep exact old deep-link semantics.
        SortedSet<String> labels=new TreeSet<>(); labels.add(canonical);
        ALIASES.forEach((alias,theme)->{if(theme.equals(canonical))labels.add(alias);});
        existing.stream().filter(Objects::nonNull).filter(c->!c.isBlank()&&group(c).equals(canonical)).forEach(labels::add);
        return List.copyOf(labels);
    }
}
