package com.skatehub.pojo.ai;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class AiActivityDescriptionRequest {

    @NotBlank(message = "活动标题不能为空")
    @Size(max = 100, message = "活动标题长度不能超过 100")
    private String title;

    @NotBlank(message = "活动时间不能为空")
    @Size(max = 100, message = "活动时间长度不能超过 100")
    private String activityTime;

    @Size(max = 200, message = "location 长度不能超过 200")
    private String location;

    @Size(max = 200, message = "place 长度不能超过 200")
    private String place;

    @Size(max = 300, message = "address 长度不能超过 300")
    private String address;

    @NotNull(message = "人数上限不能为空")
    @Min(value = 1, message = "人数上限必须大于 0")
    private Integer maxNum;

    @NotBlank(message = "活动内容不能为空")
    @Size(max = 10000, message = "活动内容长度不能超过 10000")
    private String content;

    @AssertTrue(message = "请至少提供 location，或提供 place/address")
    public boolean hasLocationInfo() {
        return StringUtils.hasText(location)
                || StringUtils.hasText(place)
                || StringUtils.hasText(address);
    }
}
