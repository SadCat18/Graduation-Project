package com.skatehub.pojo.analytics;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductEventRequest {

    @Pattern(regexp = "VIEW_PLACE|VIEW_ACTIVITY|SIGN_ACTIVITY|CHECKIN_ACTIVITY|ASK_AI_COACH|START_TRAINING_PLAN|COMPLETE_TRAINING_TASK|PUBLISH_POST|PUBLISH_VIDEO|CONFIRM_ARRIVAL|CONFIRM_RETURN", message = "eventType invalid")
    private String eventType;

    @Size(max = 30)
    private String targetType;

    private Long targetId;

    @Size(max = 80)
    private String sourcePage;
}
