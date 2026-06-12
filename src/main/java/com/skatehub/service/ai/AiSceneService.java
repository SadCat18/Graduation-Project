package com.skatehub.service.ai;

import com.skatehub.pojo.ai.AiRequest;
import com.skatehub.pojo.ai.AiResponse;
import com.skatehub.scene.AiSceneHandler;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiSceneService {

    private final AiGatewayService aiGatewayService;
    private final List<AiSceneHandler> sceneHandlers;

    public AiResponse execute(CurrentUser currentUser, AiRequest request) {
        if (currentUser == null || !"ADMIN".equalsIgnoreCase(currentUser.role())) {
            throw new BizException("普通用户不能调用通用 AI scene 执行接口");
        }
        aiGatewayService.checkRateLimit(currentUser);
        return execute(request);
    }

    public AiResponse execute(AiRequest request) {
        if (request == null || !StringUtils.hasText(request.getScene())) {
            throw new BizException("scene 不能为空");
        }
        AiSceneHandler handler = getHandler(request.getScene());
        AiRequest preparedRequest = handler.prepare(request);
        return aiGatewayService.chat(preparedRequest);
    }

    private AiSceneHandler getHandler(String scene) {
        for (AiSceneHandler handler : sceneHandlers) {
            if (handler.supports(scene)) {
                return handler;
            }
        }
        throw new BizException("不支持的 AI scene: " + scene);
    }
}
