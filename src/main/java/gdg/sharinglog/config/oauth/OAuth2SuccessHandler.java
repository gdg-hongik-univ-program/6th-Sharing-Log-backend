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
        // 과거의 요청 주소를 무시하고 위 주소를 항상 사용(프론트엔드)
        // 프런트가 실행되지 않은 환경에서 테스트하려면
        // 백엔드 단독 테스트용으론
        // (true) 부분을
        // 주소를 /로 설정

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
