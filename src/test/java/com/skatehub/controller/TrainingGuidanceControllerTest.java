package com.skatehub.controller;
import com.skatehub.config.SecurityConfig;
import com.skatehub.dao.*;
import com.skatehub.service.training.*;
import com.skatehub.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

@WebMvcTest({TrainingGuidanceController.class,AdminTrainingGuidanceController.class})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,GuidanceExceptionAdvice.class,GuidanceInputAdvice.class})
class TrainingGuidanceControllerTest {
 @Autowired MockMvc mvc;
 @MockBean TrainingGuidanceService training;
 @MockBean GuidanceContentService content;
 @MockBean GuidanceAiService ai;
 @MockBean JwtTokenService tokens;
 @MockBean UserRepository users;
 @MockBean AdminRepository admins;
 org.springframework.test.web.servlet.request.RequestPostProcessor auth(String role){return authentication(new UsernamePasswordAuthenticationToken(new CurrentUser(73L,role,"local"),null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));}
 @Test void anonymousAndNonAdminAreBlockedByRealSecurityChain() throws Exception {
  mvc.perform(get("/api/user/training-guidance/templates")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/admin/training-guidance/templates").with(auth("USER")).contentType("application/json").content("{}" )).andExpect(status().isForbidden());verifyNoInteractions(content);
 }
 @Test void moduleErrorsUseRealHttpAndTypedEnvelope() throws Exception {
  when(training.plan(any(),eq(999L))).thenThrow(new GuidanceException(404,"NOT_FOUND","记录不存在"));
  mvc.perform(get("/api/user/training-guidance/plans/999").with(auth("USER"))).andExpect(status().isNotFound()).andExpect(jsonPath("$.data.errorCode").value("NOT_FOUND"));
  mvc.perform(post("/api/user/training-guidance/plans").with(auth("USER")).contentType("application/json").content("[]")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"));
 }
 @Test void aiUnavailabilityAndRateLimitAreNeverHttpSuccess() throws Exception {
  when(ai.explain(any(),eq(5L),any())).thenThrow(new GuidanceException(503,"AI_UNAVAILABLE","解释不可用"));
  mvc.perform(post("/api/user/training-guidance/plans/5/ai-explain").with(auth("USER")).contentType("application/json").content("{}" )).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.data.errorCode").value("AI_UNAVAILABLE"));
  when(ai.explain(any(),eq(5L),any())).thenThrow(new GuidanceException(429,"RATE_LIMITED","请求过快"));
  mvc.perform(post("/api/user/training-guidance/plans/5/ai-explain").with(auth("USER")).contentType("application/json").content("{}" )).andExpect(status().isTooManyRequests());
 }
 @Test void adminBodyIsPassedToRealRouteAndSuccessfulNullCurrentIsExplicit() throws Exception {
  mvc.perform(get("/api/user/training-guidance/current").with(auth("USER"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("\"data\":null")));
  when(content.mutate(any(),isNull(),eq("create"),any())).thenReturn(GuidanceJson.obj("id","synthetic","status","DRAFT","version",1));
  mvc.perform(post("/api/admin/training-guidance/templates").with(auth("ADMIN")).contentType("application/json").content("{\"requestKey\":\"local-001\",\"document\":{\"title\":\"local\"}}" )).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DRAFT"));
 }
}
