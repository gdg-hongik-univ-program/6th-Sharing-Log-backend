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
                    <script src="/js/group-setup.js" defer></script>
                </head>
                <body>
                    <main class="shell">
                        <section class="panel">
                            <p class="eyebrow">Sharing Log</p>
                            <h1>%s님, 로그인되었습니다.</h1>
                            <p class="copy">%s</p>

                            <hr>
                            <h2>그룹 생성 및 초대 링크 확인</h2>
                            <p>발급 결과에서 APP_PUBLIC_BASE_URL 적용 여부를 확인할 수 있습니다.</p>
                            <form id="group-form">
                                <p>
                                    <label for="group-name">그룹 이름</label><br>
                                    <input id="group-name" name="name" type="text" maxlength="50" autocomplete="off" required>
                                    <button id="create-group-button" type="submit">그룹 생성</button>
                                </p>
                            </form>
                            <pre id="group-result" role="status" aria-live="polite"></pre>

                            <button id="issue-invitation-button" type="button" disabled>초대 링크 발급</button>
                            <pre id="invitation-result" role="status" aria-live="polite"></pre>
                            <p>
                                <label for="invite-url">발급된 초대 링크</label><br>
                                <input id="invite-url" type="text" readonly>
                            </p>
                            <a id="invite-link" href="#" target="_blank" rel="noopener noreferrer" hidden>초대 링크 열기</a>

                            <hr>
                            <section id="group-members">
                            <h2>그룹 멤버 목록</h2>
                            <form id="members-form">
                                <p>
                                    <label for="member-group-id">그룹 ID</label><br>
                                    <input id="member-group-id" name="groupId" type="number" min="1" required>
                                    <button id="load-members-button" type="submit">멤버 조회</button>
                                </p>
                            </form>
                            <div id="member-list-result" role="status" aria-live="polite"></div>
                            </section>

                            <hr>
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
