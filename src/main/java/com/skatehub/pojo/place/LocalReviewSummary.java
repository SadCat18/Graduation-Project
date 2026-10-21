package com.skatehub.pojo.place;

import java.math.BigDecimal;

/** Read-only aggregate of the same records returned by the public local review list. */
public record LocalReviewSummary(BigDecimal score,long count,String source) {
    public static LocalReviewSummary empty(){return new LocalReviewSummary(null,0,"LOCAL_REVIEWS");}
}
