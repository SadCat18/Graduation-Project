package com.skatehub.provider;

import com.skatehub.config.AiProperties;
import com.skatehub.pojo.ai.AiRequest;
import com.skatehub.pojo.ai.AiResponse;

import java.util.Locale;

public interface AiProvider {

    String getProviderName();

    default boolean supports(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return false;
        }
        return getProviderName().toLowerCase(Locale.ROOT)
                .equals(providerName.toLowerCase(Locale.ROOT));
    }

    AiResponse chat(AiRequest request, AiProperties.EndpointConfig config);
}
