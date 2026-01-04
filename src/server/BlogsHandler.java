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
        html = html.replace("{{NAVBAR}}",
        WeatherServer.renderNavbar(exchange, "blogs")
);


        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
