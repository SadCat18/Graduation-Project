package com.skatehub.scene;

import com.skatehub.pojo.ai.AiRequest;

import java.util.Locale;

public interface AiSceneHandler {

    String getScene();

    default boolean supports(String scene) {
        if (scene == null || scene.isBlank()) {
            return false;
        }
        return getScene().toLowerCase(Locale.ROOT)
                .equals(scene.toLowerCase(Locale.ROOT));
    }

    AiRequest prepare(AiRequest request);
}
