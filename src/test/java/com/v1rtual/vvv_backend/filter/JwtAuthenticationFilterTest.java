package com.v1rtual.vvv_backend.filter;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.core.context.SecurityContextHolder;

import com.v1rtual.vvv_backend.util.JwtUtil;

import static org.mockito.Mockito.mock;

class JwtAuthenticationFilterTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void skipsParsingAnInvalidBearerToken() throws Exception {
    JwtUtil jwtUtil = mock(JwtUtil.class);
    when(jwtUtil.validateToken("broken-token")).thenReturn(false);
    JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer broken-token");
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    verify(jwtUtil).validateToken("broken-token");
    verify(jwtUtil, never()).getUsernameFromToken("broken-token");
  }
}
