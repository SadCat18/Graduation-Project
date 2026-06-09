package com.skatehub.service.ai;

import com.skatehub.provider.AiProvider;
import com.skatehub.util.BizException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Component
public class AiProviderRegistry {

    private final List<AiProvider> providers;

    public AiProviderRegistry(List<AiProvider> providers) {
        this.providers = providers;
    }

    public AiProvider getProvider(String providerName) {
        if (!StringUtils.hasText(providerName)) {
            throw new BizException("AI provider 未配置");
        }
        String normalizedProviderName = providerName.toLowerCase(Locale.ROOT);
        for (AiProvider provider : providers) {
            if (provider.supports(normalizedProviderName)) {
                return provider;
            }
        }
        throw new BizException("不支持的 AI provider: " + providerName);
    }
}
