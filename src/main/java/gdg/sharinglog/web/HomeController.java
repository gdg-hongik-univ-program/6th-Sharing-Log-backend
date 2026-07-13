package gdg.sharinglog.web;

import java.util.Optional;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

@Controller
public class HomeController {

    @GetMapping("/")
    @ResponseBody
    public String home(@AuthenticationPrincipal OAuth2User user) {
        if (user == null) {
            return """
                    <!doctype html>
                    <html lang="ko">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <title>Sharing Log</title>
                        <link rel="stylesheet" href="/css/app.css">
                    </head>
                    <body>
                        <main class="shell">
                            <section class="panel">
                                <p class="eyebrow">Sharing Log</p>
                                <h1>공동생활을 조금 더 가볍게</h1>
                                <p class="copy">로그인하면 쉐어하우스 멤버와 공동 업무를 관리할 수 있습니다.</p>
                                <div class="login-buttons">
                                    <a class="google-button" href="/oauth2/authorization/google">
                                        <span class="google-mark" aria-hidden="true">G</span>
                                        <span>구글로 로그인하기</span>
                                    </a>
                                    <a class="naver-button" href="/oauth2/authorization/naver">
                                        <span class="naver-mark" aria-hidden="true">N</span>
                                        <span>네이버로 로그인하기</span>
                                    </a>
                                </div>
                            </section>
                        </main>
                    </body>
                    </html>
                    """;
        }

        String safeName = HtmlUtils.htmlEscape(attribute(user, "name").orElse("사용자"));
        String safeEmail = HtmlUtils.htmlEscape(attribute(user, "email").orElse(""));

        return """
                <!doctype html>
                <html lang="ko">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Sharing Log</title>
                    <link rel="stylesheet" href="/css/app.css">
                </head>
                <body>
                    <main class="shell">
                        <section class="panel">
                            <p class="eyebrow">Sharing Log</p>
                            <h1>%s님, 로그인되었습니다.</h1>
                            <p class="copy">%s</p>
                            <a class="secondary-link" href="/login">로그인 화면 보기</a>
                        </section>
                    </main>
                </body>
                </html>
                """.formatted(safeName, safeEmail);
    }

    private Optional<String> attribute(OAuth2User user, String name) {
        Object value = user.getAttribute(name);
        if (value == null || !StringUtils.hasText(value.toString())) {
            return Optional.empty();
        }
        return Optional.of(value.toString());
    }
}
