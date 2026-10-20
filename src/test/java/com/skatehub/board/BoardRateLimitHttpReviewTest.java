package com.skatehub.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.skatehub.SkateHubApplication;
import com.skatehub.config.RateLimitProperties;
import com.skatehub.util.RateLimitFilter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.*;
import org.springframework.security.web.FilterChainProxy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real loopback HTTP and production Spring security/filter/controllers; Redis counters are synthetic. */
@SpringBootTest(classes=SkateHubApplication.class, webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"spring.config.location=classpath:t07-http.properties", "server.port=0",
        "spring.datasource.url=jdbc:h2:mem:board-rate-http-review;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "app.rate-limit.enabled=true", "app.rate-limit.board-ip-per-minute=2"})
@Import(BoardT07HttpIntegrationTest.T07Bootstrap.class)
class BoardRateLimitHttpReviewTest {
    @Autowired TestRestTemplate http;
    @Autowired RateLimitProperties properties;
    @Autowired FilterChainProxy security;
    @MockBean StringRedisTemplate redis;
    @SuppressWarnings("unchecked") ValueOperations<String,String> values=mock(ValueOperations.class);
    final Map<String,Long> counters=new ConcurrentHashMap<>();
    static final String PREFIX="/api/skate/board-assistant/";

    @BeforeEach void resetCounters(){
        counters.clear();reset(redis,values);
        properties.setEnabled(true);properties.setBoardIpPerMinute(2);
        properties.setTrustedProxies(List.of());properties.setRedisFailureDeniesProtected(true);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenAnswer(i->counters.merge(i.getArgument(0),1L,Long::sum));
    }
    ResponseEntity<JsonNode> post(String route,String forwarded){
        HttpHeaders headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For",forwarded);
        return http.exchange(PREFIX+route,HttpMethod.POST,new HttpEntity<>("{}",headers),JsonNode.class);
    }
    void status(ResponseEntity<JsonNode> response,int status){
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("code").asInt()).isEqualTo(status);
    }
    @Test void allFourRealRoutesRejectThirdRequestAndActuallyUseSecurityFilter(){
        assertThat(security.getFilterChains()).anySatisfy(chain->assertThat(chain.getFilters()).anyMatch(RateLimitFilter.class::isInstance));
        for(String route:List.of("recommend","evaluate","replacements","compare")){
            counters.clear();status(post(route,"198.51.100.1"),400);status(post(route,"198.51.100.2"),400);status(post(route,"198.51.100.3"),429);
            assertThat(counters).containsOnlyKeys("rate-limit:board:ip:127.0.0.1");
            assertThat(counters.values()).containsExactly(3L);
        }
    }
    @Test void sharedBudgetCannotBeRotatedBySpoofedForwarding(){
        status(post("recommend","198.51.100.1"),400);status(post("evaluate","198.51.100.2"),400);status(post("compare","198.51.100.3"),429);
        assertThat(counters).containsOnlyKeys("rate-limit:board:ip:127.0.0.1");
    }
    @Test void verifiedLoopbackProxyUsesFirstUntrustedHopAndNormalizesIpv6(){
        properties.setTrustedProxies(List.of("127.0.0.1/32","192.0.2.0/24"));
        status(post("recommend","1.1.1.1, 198.51.100.4, 192.0.2.2"),400);
        status(post("evaluate","2.2.2.2, 198.51.100.4, 192.0.2.2"),400);
        status(post("compare","3.3.3.3, 198.51.100.4, 192.0.2.2"),429);
        status(post("recommend","2001:db8::5"),400);
        status(post("evaluate","2001:0db8:0:0:0:0:0:5"),400);
        status(post("compare","2001:db8::5"),429);
        assertThat(counters).containsOnlyKeys("rate-limit:board:ip:198.51.100.4","rate-limit:board:ip:2001:db8:0:0:0:0:0:5");
    }
    @Test void disabledPolicyBypassesCounterAndRedisFailureIsExplicit(){
        properties.setEnabled(false);
        for(int i=0;i<3;i++)status(post("recommend","198.51.100.4"),400);
        verifyNoInteractions(values);
        properties.setEnabled(true);
        when(values.increment(anyString())).thenThrow(new RedisConnectionFailureException("synthetic unavailable"));
        status(post("recommend","198.51.100.4"),503);
        properties.setRedisFailureDeniesProtected(false);status(post("recommend","198.51.100.4"),400);
    }
}
