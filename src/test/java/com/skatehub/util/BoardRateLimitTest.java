package com.skatehub.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skatehub.config.RateLimitProperties;
import com.skatehub.service.RateLimitService;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.*;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BoardRateLimitTest {
    final ObjectMapper mapper=new ObjectMapper();
    final RateLimitProperties properties=new RateLimitProperties();
    final StringRedisTemplate redis=mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked") final ValueOperations<String,String> values=mock(ValueOperations.class);
    final Map<String,Long> counts=new HashMap<>();
    RateLimitFilter filter;
    @BeforeEach void setup(){
        properties.setEnabled(true);properties.setBoardIpPerMinute(2);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenAnswer(i->counts.merge(i.getArgument(0),1L,Long::sum));
        filter=new RateLimitFilter(new RateLimitService(redis,properties),properties,mapper);
    }
    MockHttpServletResponse call(String path,String peer,String forwarded,AtomicInteger accepted)throws Exception{
        var request=new MockHttpServletRequest("POST",path);request.setRemoteAddr(peer);
        if(forwarded!=null)request.addHeader("X-Forwarded-For",forwarded);
        var response=new MockHttpServletResponse();filter.doFilter(request,response,(req,res)->accepted.incrementAndGet());return response;
    }
    @ParameterizedTest @ValueSource(strings={"recommend","evaluate","compare","replacements"})
    void eachCalculationRejectsThirdRequestWithSyntheticBudgetTwo(String route)throws Exception{
        String path="/api/skate/board-assistant/"+route;AtomicInteger accepted=new AtomicInteger();
        assertThat(call(path,"198.51.100.4",null,accepted).getStatus()).isEqualTo(200);
        assertThat(call(path,"198.51.100.4",null,accepted).getStatus()).isEqualTo(200);
        var denied=call(path,"198.51.100.4",null,accepted);assertThat(denied.getStatus()).isEqualTo(429);
        assertThat(mapper.readTree(denied.getContentAsString()).path("code").asInt()).isEqualTo(429);assertThat(accepted.get()).isEqualTo(2);
    }
    @Test void fourRoutesShareBudgetAndUntrustedHeadersCannotRotateBuckets()throws Exception{
        AtomicInteger accepted=new AtomicInteger();call("/api/skate/board-assistant/recommend","198.51.100.4","1.1.1.1",accepted);call("/api/skate/board-assistant/evaluate","198.51.100.4","2.2.2.2",accepted);
        assertThat(call("/api/skate/board-assistant/compare","198.51.100.4","3.3.3.3",accepted).getStatus()).isEqualTo(429);
        assertThat(counts.keySet()).containsExactly("rate-limit:board:ip:198.51.100.4");
    }
    @Test void redisFailureIsExplicitlyDeniedOrExplicitlyConfiguredFailOpen()throws Exception{
        when(values.increment(anyString())).thenThrow(new RedisConnectionFailureException("synthetic unavailable"));AtomicInteger accepted=new AtomicInteger();
        var denied=call("/api/skate/board-assistant/recommend","198.51.100.4",null,accepted);assertThat(denied.getStatus()).isEqualTo(503);assertThat(mapper.readTree(denied.getContentAsString()).path("code").asInt()).isEqualTo(503);assertThat(accepted.get()).isZero();
        properties.setRedisFailureDeniesProtected(false);assertThat(call("/api/skate/board-assistant/recommend","198.51.100.4",null,accepted).getStatus()).isEqualTo(200);assertThat(accepted.get()).isEqualTo(1);
    }
    @Test void disabledFilterIsNotRegisteredAndDisabledServiceDoesNotAccessRedis(){
        new ApplicationContextRunner().withUserConfiguration(RateLimitFilter.class)
            .withBean(RateLimitService.class,()->new RateLimitService(redis,properties)).withBean(RateLimitProperties.class,()->properties).withBean(ObjectMapper.class,()->mapper)
            .withPropertyValues("app.rate-limit.enabled=false").run(ctx->assertThat(ctx).doesNotHaveBean(RateLimitFilter.class));
        properties.setEnabled(false);assertThat(new RateLimitService(redis,properties).allow("ignored",2,java.time.Duration.ofMinutes(1)).allowed()).isTrue();verifyNoInteractions(values);
    }
    @Test void enabledContextRegistersExistingFilter(){
        new ApplicationContextRunner().withUserConfiguration(RateLimitFilter.class)
            .withBean(RateLimitService.class,()->new RateLimitService(redis,properties)).withBean(RateLimitProperties.class,()->properties).withBean(ObjectMapper.class,()->mapper)
            .withPropertyValues("app.rate-limit.enabled=true").run(ctx->assertThat(ctx).hasSingleBean(RateLimitFilter.class));
    }
    @Test void serializedEnvelopeEscapesQuotesBackslashesAndChinese()throws Exception{
        RateLimitService service=mock(RateLimitService.class);when(service.isEnabled()).thenReturn(true);
        String message="引号\"与反斜杠\\不可用";when(service.allow(anyString(),anyInt(),any())).thenReturn(RateLimitService.RateLimitDecision.rejected(503,message));filter=new RateLimitFilter(service,properties,mapper);
        var response=call("/api/skate/board-assistant/recommend","198.51.100.4",null,new AtomicInteger());assertThat(response.getStatus()).isEqualTo(503);assertThat(mapper.readTree(response.getContentAsString()).path("message").asText()).isEqualTo(message);
    }
    @ParameterizedTest @ValueSource(strings={"/api/auth/login/password","/api/auth/register","/api/ai/generate","/api/files/upload/image"})
    void existingProtectedPathsStillCheckRedis(String path)throws Exception{
        call(path,"198.51.100.4",null,new AtomicInteger());verify(values,atLeastOnce()).increment(anyString());assertThat(counts).isNotEmpty();
    }
    @Test void trustedChainsAreParsedFromVerifiedPeerToFirstUntrustedHop(){
        List<String> trusted=List.of("192.0.2.0/24","2001:db8:1::/48");
        assertThat(ClientIpResolver.resolve("192.0.2.10","6.6.6.6, 198.51.100.4, 192.0.2.20",trusted)).isEqualTo("198.51.100.4");
        assertThat(ClientIpResolver.resolve("198.51.100.4","6.6.6.6",trusted)).isEqualTo("198.51.100.4");
        assertThat(ClientIpResolver.resolve("192.0.2.10","2001:db8:2::5, 2001:db8:1::7",trusted)).isEqualTo("2001:db8:2:0:0:0:0:5");
        assertThat(ClientIpResolver.resolve("192.0.2.10","198.51.100.4",List.of())).isEqualTo("192.0.2.10");
    }
    @ParameterizedTest @ValueSource(strings={"",",198.51.100.4","198.51.100.4,","unknown","host.example","300.1.2.3","01.2.3.4","1.2.3.4:80","[2001:db8::1]:80","2001:db8::gg","fe80::1%eth0","2001:db8::1,,192.0.2.2"})
    void malformedForwardingFallsBackToSocketPeer(String header){assertThat(ClientIpResolver.resolve("192.0.2.10",header,List.of("192.0.2.0/24"))).isEqualTo("192.0.2.10");}
    @org.springframework.web.bind.annotation.RestController
    static class OutsideBoardController {
        @org.springframework.web.bind.annotation.GetMapping("/synthetic-outside-board")
        public String fail(){throw new IllegalStateException("synthetic outside board");}
    }
    @Test void localBoard503AdviceDoesNotChangeOtherModulesGlobalEnvelope()throws Exception{
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new OutsideBoardController())
            .setControllerAdvice(new com.skatehub.board.BoardErrorAdvice(),new GlobalExceptionHandler()).build();
        var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/synthetic-outside-board")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);var envelope=mapper.readTree(response.getContentAsString());assertThat(envelope.path("code").asInt()).isEqualTo(500);assertThat(envelope.path("message").asText()).isEqualTo("synthetic outside board");
    }
    @Test void malformedTrustConfigurationIsRejectedAndNginxDropsClientPrefix()throws Exception{
        for(String bad:List.of("private.example","1.2.3.4/33","2001:db8::/129","1.2.3.4/24/1"))assertThatThrownBy(()->properties.setTrustedProxies(List.of(bad))).isInstanceOf(IllegalArgumentException.class);
        String nginx=java.nio.file.Files.readString(java.nio.file.Path.of("deploy/docker/nginx/default.conf.template"));assertThat(nginx).doesNotContain("$proxy_add_x_forwarded_for");assertThat(nginx.split("proxy_set_header X-Forwarded-For \\$remote_addr;",-1).length-1).isEqualTo(3);
        String compose=java.nio.file.Files.readString(java.nio.file.Path.of("deploy/docker/docker-compose.yml"));assertThat(compose).doesNotContain("8080:8080");
    }
}
