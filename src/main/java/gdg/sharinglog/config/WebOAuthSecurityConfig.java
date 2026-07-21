package gdg.sharinglog.config;

import gdg.sharinglog.config.oauth.OAuth2UserCustomService;
import gdg.sharinglog.config.oauth.OAuth2SuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class WebOAuthSecurityConfig {

    private final OAuth2UserCustomService oAuth2UserCustomService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    public WebOAuthSecurityConfig(OAuth2UserCustomService oAuth2UserCustomService,
                                  OAuth2SuccessHandler oAuth2SuccessHandler) {
        this.oAuth2UserCustomService = oAuth2UserCustomService;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/login",
                                "/css/**",
                                "/img/**",
                                "/js/**",
                                "/favicon.ico",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(oAuth2UserCustomService))
                        .successHandler(oAuth2SuccessHandler)
                )

                // 해당 요청만 CSRF 예외 처리하고
                // Spring Security 로그아웃 필터가 세션 무효화, 인증 정보 삭제, JSESSIONID 쿠키 삭제 후
                // 204 No Content를 반환하도록 설정
                .csrf(csrf -> csrf.ignoringRequestMatchers(WebOAuthSecurityConfig::isApiLogoutRequest))
                .logout(logout -> logout
                        .logoutRequestMatcher(WebOAuthSecurityConfig::isApiLogoutRequest)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value()))
                )
                .build();
    }

    private static boolean isApiLogoutRequest(jakarta.servlet.http.HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        return "POST".equals(request.getMethod()) && "/api/auth/logout".equals(path);
    }
}
