package com.skatehub.board;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skatehub.config.SecurityConfig;
import com.skatehub.dao.AdminRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers={BoardCatalogController.class,BoardImportController.class})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,BoardDatasetValidator.class,BoardErrorAdvice.class})
class BoardCatalogSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired BoardDatasetValidator datasets;
    @MockBean BoardCatalogService service;
    @MockBean JwtTokenService tokens;
    @MockBean UserRepository users;
    @MockBean AdminRepository admins;
    @Test void onlyExactTwoGetsArePublic() throws Exception {
        when(service.catalog(null,1,20)).thenReturn(new PageResult<>(0,List.of()));when(service.catalogVersion()).thenReturn("a".repeat(64));when(service.variant("test-key")).thenReturn(json.createObjectNode());
        mvc.perform(get("/api/skate/board-assistant/catalog")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0)).andExpect(jsonPath("$.data.list").isArray()).andExpect(header().string("X-Catalog-Version","a".repeat(64)));
        mvc.perform(get("/api/skate/board-assistant/variants/test-key")).andExpect(status().isOk());
        for(String path:List.of("/api/skate/board-assistant/catalog","/api/admin/board-assistant/import","/api/admin/board-assistant/import/validate","/api/user/board-plans"))mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/skate/board-assistant/variants/test-key/extra")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/board-assistant/products")).andExpect(status().isUnauthorized());
    }
    @Test @WithMockUser(roles="USER") void userCannotValidateOrImport() throws Exception {
        for(String path:List.of("/api/admin/board-assistant/import","/api/admin/board-assistant/import/validate"))mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(roles="ADMIN") void validationOnlyInvokesReadOnlyService() throws Exception {
        var report=new BoardDtos.ValidationReport(true,List.of(),json.createArrayNode(),"a".repeat(64));when(service.validate(any())).thenReturn(report);
        mvc.perform(post("/api/admin/board-assistant/import/validate").contentType(MediaType.APPLICATION_JSON).content(BoardTestData.real(datasets).toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.valid").value(true));
        verify(service).validate(any());verify(service,never()).importDataset(any(),anyString(),anyLong());
    }
    @Test void adminImportPreservesStructuredConflictAndActor() throws Exception {
        var principal=new CurrentUser(17L,"ADMIN","synthetic-test-admin");var auth=new UsernamePasswordAuthenticationToken(principal,null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(service.importDataset(any(),eq("a".repeat(64)),eq(17L))).thenReturn(new BoardDtos.ImportResult(0,0,0,0,List.of(),json.createArrayNode(),"CATALOG_CHANGED","b".repeat(64)));
        String body="{\"dataset\":{},\"expectedCatalogVersion\":\""+"a".repeat(64)+"\"}";
        mvc.perform(post("/api/admin/board-assistant/import").with(authentication(auth)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.data.errorCode").value("CATALOG_CHANGED"));
        verify(service).importDataset(any(),eq("a".repeat(64)),eq(17L));
    }
    @Test @WithMockUser(roles="ADMIN") void malformedOrOversizeInputIs400() throws Exception {
        for(String body:List.of("{\"dataset\":{},\"expectedCatalogVersion\":123}","{\"x\":1,\"x\":2}"," ".repeat(BoardDatasetValidator.MAX_BYTES+1)))mvc.perform(post("/api/admin/board-assistant/import").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void hardImportErrorsKeepStructuredDataWithHttpAndEnvelope400() throws Exception {
        var principal=new CurrentUser(17L,"ADMIN","synthetic-test-admin");var auth=new UsernamePasswordAuthenticationToken(principal,null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        var error=new BoardDtos.Issue("SYNTHETIC-Q","INVALID_CENTS","integer required");
        when(service.importDataset(any(),anyString(),eq(17L))).thenReturn(new BoardDtos.ImportResult(0,0,0,1,List.of(error),json.createArrayNode(),"SELECTION_INVALID","a".repeat(64)));
        String body="{\"dataset\":{},\"expectedCatalogVersion\":\""+"a".repeat(64)+"\"}";
        mvc.perform(post("/api/admin/board-assistant/import").with(authentication(auth)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400)).andExpect(jsonPath("$.data.errorCode").value("SELECTION_INVALID")).andExpect(jsonPath("$.data.errors[0].code").value("INVALID_CENTS"));
    }
    @Test void invalidPagingAndNotFoundUseHttpStatus() throws Exception {
        mvc.perform(get("/api/skate/board-assistant/catalog?size=51")).andExpect(status().isBadRequest());
        when(service.variant("missing")).thenThrow(new BoardRequestException(404,"规格不存在"));mvc.perform(get("/api/skate/board-assistant/variants/missing")).andExpect(status().isNotFound());
    }
}
