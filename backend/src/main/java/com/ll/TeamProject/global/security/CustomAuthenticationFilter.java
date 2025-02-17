package com.ll.TeamProject.global.security;

import com.ll.TeamProject.domain.user.entity.SiteUser;
import com.ll.TeamProject.domain.user.service.UserService;
import com.ll.TeamProject.global.userContext.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationFilter extends OncePerRequestFilter {

    private final UserContext userContext;
    private final UserService userService;

    record AuthTokens(
            String apiKey,
            String accessToken
    ) { }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (List.of("/api/admin/login", "/api/admin/logout").contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        AuthTokens authTokens = getAuthTokensFromRequest();

        if (authTokens == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = authTokens.apiKey;
        String accessToken = authTokens.accessToken;

        SiteUser user = userService.getUserFromAccessToken(accessToken);

        if (user == null)
            user = refreshAccessTokenByApiKey(apiKey);

        if (user != null)
            userContext.setLogin(user);

        filterChain.doFilter(request, response);
    }

    private SiteUser refreshAccessTokenByApiKey(String apiKey) {
        Optional<SiteUser> opUser = userService.findByApiKey(apiKey);
        if (opUser.isEmpty()) return null;
        SiteUser user = opUser.get();

        refreshAccessToken(user);
        return user;
    }

    private void refreshAccessToken(SiteUser user) {
        String newAccessToken = userService.genAccessToken(user);

        userContext.setHeader("Authorization", "Bearer " + user.getApiKey() + " " + newAccessToken);
        userContext.setCookie("accessToken", newAccessToken);
    }

    private AuthTokens getAuthTokensFromRequest() {
        // 요청 헤더에서 Authorization 얻기
        String authorization = userContext.getHeader("Authorization");

        // Authorization null 아니고 Bearer 시작하면 토큰값 얻기
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring("Bearer ".length());
            String[] tokenBits = token.split(" ", 2);

            if (tokenBits.length == 2)
                return new AuthTokens(tokenBits[0], tokenBits[1]);
        }

        // 헤더에 토큰이 없다면 쿠키에서 토큰값 얻기
        String apikey = userContext.getCookieValue("apiKey");
        String accessToken = userContext.getCookieValue("accessToken");

        if (apikey != null && accessToken != null)
            return new AuthTokens(apikey, accessToken);

        return null;
    }
}
