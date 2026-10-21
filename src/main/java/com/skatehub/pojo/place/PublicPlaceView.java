package com.skatehub.pojo.place;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.skatehub.pojo.Place;

/** Preserve every legacy Place JSON property without mutating or saving the managed entity. */
public record PublicPlaceView(@JsonUnwrapped Place place, LocalReviewSummary localReviewSummary) {}
