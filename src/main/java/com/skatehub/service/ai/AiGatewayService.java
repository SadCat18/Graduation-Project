package com.skatehub.service.ai;

import com.skatehub.config.AiProperties;
import com.skatehub.pojo.ai.AiRequest;
import com.skatehub.pojo.ai.AiResponse;
import com.skatehub.provider.AiProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiGatewayService {

    private final AiProperties aiProperties;
    private final AiProviderRegistry aiProviderRegistry;

    public AiResponse chat(AiRequest request) {
        AiProperties.EndpointConfig resolvedConfig = resolveConfig(request);
        String providerName = resolvedConfig.getProvider();
        log.info("ai route resolved, scene={}, provider={}, model={}",
                request == null ? null : request.getScene(),
                providerName,
                resolvedConfig.getModel());
        AiProvider provider = aiProviderRegistry.getProvider(providerName);
        return provider.chat(request, resolvedConfig);
    }

    public AiProperties config() {
        return aiProperties;
    }

    public AiProperties.EndpointConfig resolveConfig(AiRequest request) {
        if (request == null) {
            return aiProperties.resolveSceneConfig(null);
        }
        return aiProperties.resolveRequestConfig(
                request.getScene(),
                normalize(request.getProvider()),
                normalize(request.getModel())
        );
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
