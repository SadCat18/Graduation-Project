package com.skatehub.security;

import com.skatehub.dao.CommentRepository;
import com.skatehub.dao.InteractionRepository;
import com.skatehub.dao.PostRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.service.MessageNotifyService;
import com.skatehub.service.PostService;
import com.skatehub.service.UserGrowthService;
import com.skatehub.util.BizException;
import com.skatehub.util.InputValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InjectionRiskTest {

    @Test
    void likeKeywordEscapesWildcardCharacters() {
        assertThat(InputValidator.likeKeyword("100%_safe", 50, "keyword"))
                .isEqualTo("%100!%!_safe%");
    }

    @Test
    void postSearchRejectsSqlInjectionKeywordBeforeRepositoryCall() {
        PostRepository postRepository = mock(PostRepository.class);
        PostService postService = new PostService(
                postRepository,
                mock(CommentRepository.class),
                mock(UserRepository.class),
                mock(InteractionRepository.class),
                mock(MessageNotifyService.class),
                mock(UserGrowthService.class)
        );

        assertThatThrownBy(() -> postService.list("' OR 1=1 --", null, "latest", 1, 10))
                .isInstanceOf(BizException.class);

        verify(postRepository, never()).searchPublicPosts(anyString(), any(), anyString(), any());
    }
}
