package com.petshop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import com.petshop.entity.Role;
import com.petshop.entity.User;
import com.petshop.repository.RoleRepository;
import com.petshop.repository.UserRepository;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:liveauth;DB_CLOSE_DELAY=-1;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@ActiveProfiles("test")
class PetShopApplicationTests {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;

    @Test
    void freshAnonymousAuthPagesRenderCompleteHtmlAndCreateSessionBeforeCommit() throws Exception {
        for (String path : List.of("/login", "/register", "/forgot-password", "/login?error=true", "/login?logout=true")) {
            var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            var client = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
            var page = get(client, path);
            assertEquals(200, page.statusCode(), path);
            assertTrue(page.body().stripTrailing().endsWith("</html>"), "Truncated response: " + path);
            assertFalse(csrf(page.body()).isBlank());
            assertTrue(cookies.getCookieStore().getCookies().stream().anyMatch(c -> "JSESSIONID".equals(c.getName())));
            if (path.equals("/login")) {
                assertTrue(page.body().contains("name=\"username\""));
                var failed = post(client, "/login", "username=missing%40test.local&password=Wrong%40123&_csrf=" + encode(csrf(page.body())));
                assertEquals(302, failed.statusCode());
                assertTrue(failed.headers().firstValue("location").orElseThrow().endsWith("/login?error=true"));
            }
        }
    }

    @Test
    void realHttpLoginAndLogoutWorkForCustomerStaffAndAdmin() throws Exception {
        int index = 0;
        for (String code : List.of("CUSTOMER", "STAFF", "ADMIN")) {
            var role = roles.findByCode(code).orElseGet(() -> roles.save(Role.builder().code(code).name(code).build()));
            var account = users.save(User.builder().role(role).fullName("HTTP " + code).email(code.toLowerCase() + "@liveauth.test")
                    .phone("090100010" + index++).passwordHash(encoder.encode("Login@123")).status("ACTIVE").build());
            var client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                    .connectTimeout(Duration.ofSeconds(5)).build();
            var page = get(client, "/login");
            var loggedIn = post(client, "/login", "username=" + encode(account.getEmail())
                    + "&password=Login%40123&_csrf=" + encode(csrf(page.body())));
            assertEquals(302, loggedIn.statusCode());
            String destination = code.equals("ADMIN") ? "/admin/dashboard" : code.equals("STAFF") ? "/staff/dashboard" : "/";
            assertTrue(loggedIn.headers().firstValue("location").orElseThrow().endsWith(destination));
            var profile = get(client, "/account/profile");
            assertEquals(200, profile.statusCode());
            assertTrue(profile.body().stripTrailing().endsWith("</html>"));
            var loggedOut = post(client, "/logout", "_csrf=" + encode(csrf(profile.body())));
            assertEquals(302, loggedOut.statusCode());
            assertTrue(loggedOut.headers().firstValue("location").orElseThrow().endsWith("/login?logout=true"));
            assertEquals(302, get(client, "/account/profile").statusCode());
        }
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(HttpClient client, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String csrf(String html) {
        var match = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertTrue(match.find(), "Missing CSRF field");
        return org.springframework.web.util.HtmlUtils.htmlUnescape(match.group(1));
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
