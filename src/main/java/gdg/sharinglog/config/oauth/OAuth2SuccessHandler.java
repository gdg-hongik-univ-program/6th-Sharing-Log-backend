package gdg.sharinglog.config.oauth;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2SuccessHandler
        extends SavedRequestAwareAuthenticationSuccessHandler {

    private final OAuth2UserPersistenceService userPersistenceService;

    public OAuth2SuccessHandler(
            OAuth2UserPersistenceService userPersistenceService,
            @Value("${app.frontend-base-url:http://localhost:5173}")
            String frontendBaseUrl
    ) {
        this.userPersistenceService = userPersistenceService;

        String normalizedFrontendUrl =
                frontendBaseUrl.replaceFirst("/+$", "");

        // Google 로그인 성공 후 React 하우스 선택 화면으로 이동
        setDefaultTargetUrl(
                normalizedFrontendUrl + "/house-choice"
        );

        // 과거에 저장된 백엔드 주소 대신 위 React 주소를 항상 사용
        // ** 초대 코드로 들어왔다가 로그인하는 경우에는 초대 코드가 사라질 수 있음
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        if (authentication instanceof OAuth2AuthenticationToken oAuth2Token) {
            userPersistenceService.saveOrUpdate(
                    oAuth2Token.getAuthorizedClientRegistrationId(),
                    oAuth2Token.getPrincipal()
            );
        }

        super.onAuthenticationSuccess(
                request,
                response,
                authentication
        );
    }
}
