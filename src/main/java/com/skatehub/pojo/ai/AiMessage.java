package com.skatehub.pojo.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiMessage {

    @NotBlank(message = "消息角色不能为空")
    @Pattern(regexp = "system|user|assistant|tool", message = "消息角色不合法")
    private String role;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 20000, message = "消息内容长度不能超过 20000")
    private String content;
}
