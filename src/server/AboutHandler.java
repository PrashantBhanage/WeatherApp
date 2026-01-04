package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class AboutHandler implements HttpHandler {

    private static final String WEB_ROOT = "web";

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        File file = new File(WEB_ROOT, "about.html");
        if (!file.exists() || !file.isFile()) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        String html;
        try (FileInputStream fis = new FileInputStream(file)) {
            html = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
        }

        // ✅ Inject navbar
        html = html.replace(
            "{{NAVBAR}}",
            WeatherServer.renderNavbar(exchange, "about")
        );

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
