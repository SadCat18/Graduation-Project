package com.skatehub.board;

import com.skatehub.config.SecurityConfig;
import com.skatehub.dao.*;
import com.skatehub.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers={BoardCalculationController.class,BoardPlanController.class,BoardAdminController.class})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,BoardDatasetValidator.class,BoardErrorAdvice.class})
class BoardB01SecurityTest {
    @Autowired MockMvc mvc;
    @Autowired BoardDatasetValidator json;
    @MockBean BoardCalculationService calculation;
    @MockBean BoardPlanService plans;
    @MockBean BoardAdminService admin;
    @MockBean JwtTokenService tokens;
    @MockBean UserRepository users;
    @MockBean AdminRepository admins;
    UsernamePasswordAuthenticationToken auth(String role){return new UsernamePasswordAuthenticationToken(new CurrentUser(101L,role,"synthetic-principal"),null,List.of(new SimpleGrantedAuthority("ROLE_"+role)));}
    @Test void fourExactPostsPublicButOtherMethodsAndPathsProtected()throws Exception{
        when(calculation.recommend(any())).thenReturn(json.object());when(calculation.evaluate(any())).thenReturn(json.object());when(calculation.compare(any())).thenReturn(json.object());when(calculation.replacements(any())).thenReturn(json.object());when(calculation.publicView(any())).thenAnswer(i->i.getArgument(0));
        for(String p:List.of("recommend","evaluate","replacements","compare"))mvc.perform(post("/api/skate/board-assistant/"+p).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        for(String p:List.of("recommend","evaluate","replacements","compare","evaluate/extra"))mvc.perform(get("/api/skate/board-assistant/"+p)).andExpect(status().isUnauthorized());mvc.perform(post("/api/skate/board-assistant/evaluate/extra").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }
    @Test void unauthenticatedSnapshotAndAdminRequestsAre401()throws Exception{for(String p:List.of("/api/user/board-plans","/api/user/board-plans/key","/api/admin/board-assistant/products"))mvc.perform(get(p)).andExpect(status().isUnauthorized());for(String p:List.of("/api/user/board-plans","/api/user/board-plans/key/copy","/api/admin/board-assistant/trial","/api/admin/board-assistant/quotes/q/activation"))mvc.perform(post(p).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());verifyNoInteractions(plans,admin);}
    @Test void userCannotAdminAndAdminCannotImpersonateUserPrincipal()throws Exception{mvc.perform(post("/api/admin/board-assistant/trial").with(authentication(auth("USER"))).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());mvc.perform(post("/api/user/board-plans").with(authentication(auth("ADMIN"))).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());verifyNoInteractions(plans,admin);}
    @Test void snapshotSaveUsesPrincipalIdAndKeepsConflictData()throws Exception{when(plans.save(eq(101L),any())).thenReturn(json.object().put("errorCode","PRICE_CHANGED").set("evaluation",json.object().put("totalCents",1847)));mvc.perform(post("/api/user/board-plans").with(authentication(auth("USER"))).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.errorCode").value("PRICE_CHANGED")).andExpect(jsonPath("$.data.evaluation.totalCents").value(1847));verify(plans).save(eq(101L),any());}
    @Test void missingAndCrossUserSnapshotsExposeTheSame404()throws Exception{when(plans.detail(eq(101L),anyString())).thenThrow(new BoardRequestException(404,"方案不存在"));for(String key:List.of("absent","other-users-key"))mvc.perform(get("/api/user/board-plans/"+key).with(authentication(auth("USER")))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));}
    @Test void adminActorAndVersionConflictAreStructured()throws Exception{when(admin.patch(eq("quotes"),eq("q"),any(),eq(101L))).thenReturn(json.object().put("errorCode","VERSION_CONFLICT").put("currentVersion",2));mvc.perform(patch("/api/admin/board-assistant/quotes/q").with(authentication(auth("ADMIN"))).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.currentVersion").value(2));verify(admin).patch(eq("quotes"),eq("q"),any(),eq(101L));}
    @Test void publicDuplicateTrailingAndOversizedJsonAre400()throws Exception{for(String body:List.of("{\"demand\":1,\"demand\":2}","{} {}"," ".repeat(BoardDatasetValidator.MAX_BYTES+1)))mvc.perform(post("/api/skate/board-assistant/evaluate").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));verifyNoInteractions(calculation);}
}
