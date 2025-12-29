package server;

import com.sun.net.httpserver.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class BlogsHandler implements HttpHandler {

    private static final String WEB_ROOT = "web";

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        File file = new File(WEB_ROOT, "blogs.html");
        if (!file.exists()) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        String html;
        try (FileInputStream fis = new FileInputStream(file)) {
            html = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
        }

        String userName = WeatherServer.getUserFromSession(exchange);
        boolean loggedIn = userName != null;

        html = html.replace("{{USERNAME}}", loggedIn ? userName : "Guest");

        // active nav
        html = html.replace("{{ACTIVE_HOME}}", "");
        html = html.replace("{{ACTIVE_BLOGS}}", "nav-link-active");
        html = html.replace("{{ACTIVE_INFO}}", "");
        html = html.replace("{{ACTIVE_RANKINGS}}", "");

        if (loggedIn) {
            html = html.replace("{{PROFILE_LINK}}",
                    "<a href='/profile' class='nav-link'>Profile</a>");
            html = html.replace("{{AUTH_LINK}}",
                    "<a href='/logout' class='nav-link nav-link-danger'>Logout</a>");
        } else {
            html = html.replace("{{PROFILE_LINK}}", "");
            html = html.replace("{{AUTH_LINK}}",
                    "<a href='/login' class='nav-link'>Login</a>");
        }

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
