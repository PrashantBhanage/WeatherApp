package server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dao.DBUtil;
import server.WeatherServer.WeatherHandler.StaticVideoHandler;

import java.io.*;
import java.net.HttpURLConnection; // Assuming DBUtil exists and provides Connection
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WeatherServer {

    private static final String WEB_ROOT = "web"; // folder where your HTML/CSS/images are
    // simple in-memory sessions: token -> displayName
    private static final Map<String, String> SESSIONS = new ConcurrentHashMap<>();

    // ===== WeatherAPI.com  =====
    private static final String WEATHER_API_KEY =
            "5773a4cdf02541c990973658252711"; // your key

    private static final String WEATHER_API_BASE_URL =
            "https://api.weatherapi.com/v1/forecast.json";

    public static void main(String[] args) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8081), 2);

            // ========== PAGES ==========
            // root/dashboard -> home page (even for guests)
            server.createContext("/", new HomeHandler());
            server.createContext("/home", new HomeHandler());
            server.createContext("/dashboard", new HomeHandler());
             // DashboardHandler removed from routing

            // login page (index.html)
            server.createContext("/login", new FileHandler("index.html", "text/html"));

           // server.createContext("/locations", new FileHandler("locations.html", "text/html"));
            
            server.createContext("/register", new FileHandler("register.html", "text/html"));

            server.createContext("/profile", new ProfileHandler());
            
            // use external InformationHandler class (src/server/InformationHandler.java)
            
            server.createContext("/information", new InformationHandler());
            server.createContext("/logout", new LogoutHandler());

            // ========== STATIC ASSETS ==========
            server.createContext("/styles.css", new FileHandler("styles.css", "text/css"));
            
            server.createContext("/images", new ImageHandler());
            server.createContext("/videos", new StaticVideoHandler());

            // ========== FORM HANDLERS ==========
            server.createContext("/doLogin", new LoginHandler());
            server.createContext("/doRegister", new RegisterHandler());
            server.createContext("/getWeather", new WeatherHandler());
            server.createContext("/saveLocation", new SaveLocationHandler());
            server.createContext("/updateProfile", new UpdateProfileHandler());

            server.setExecutor(null);
            server.start();
            System.out.println("Server running at http://localhost:8081/");

        } catch (IOException e) {
            System.out.println("Error starting server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ---------- Serve HTML / CSS ----------
    static class FileHandler implements HttpHandler {
        private final String filePath;
        private final String contentType;

        public FileHandler(String filePath, String contentType) {
            this.filePath = filePath;
            this.contentType = contentType;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            File file = new File(WEB_ROOT, filePath);

            if (!file.exists() || !file.isFile()) {
                String notFound = "404 - File not found";
                byte[] bytes = notFound.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }

            byte[] bytes;
            try (FileInputStream fis = new FileInputStream(file)) {
                bytes = fis.readAllBytes();
            }

            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", contentType + "; charset=UTF-8");

            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // ================== HOME (FULL PAGE) ==================
    // Check Weather + Saved Locations + Recent History + Hero image
    static class HomeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String userName = getUserFromSession(exchange);
            String userEmail = getUserEmailFromCookie(exchange);

            boolean loggedIn = userName != null && !userName.isBlank() && userEmail != null;
            String displayName = loggedIn ? userName : "Guest";

            // load the HTML
            File file = new File(WEB_ROOT, "home.html");
            if (!file.exists() || !file.isFile()) {
                String notFound = "404 - File not found (home.html)";
                byte[] bytes = notFound.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }

            String html;
            try (FileInputStream fis = new FileInputStream(file)) {
                html = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
            }

            // navbar name + template
            html = html.replace("Hi, User", "Hi, " + escapeHtml(displayName));
            html = html.replace("{{USERNAME}}", escapeHtml(displayName));

            // Set active nav link
            html = html.replace("{{ACTIVE_HOME}}", "nav-link-active");
            html = html.replace("{{ACTIVE_PROFILE}}", "");
            html = html.replace("{{ACTIVE_INFO}}", "");

            // Decide what to show in navbar: Login or Logout
if (loggedIn) {
    html = html.replace("{{AUTH_LINK}}",
            "<a href=\"/logout\" class=\"nav-link nav-link-danger\">Logout</a>");
} else {
    html = html.replace("{{AUTH_LINK}}",
            "<a href=\"/login\" class=\"nav-link\">Login</a>");
}
if (loggedIn) {
    html = html.replace("{{PROFILE_LINK}}",
        "<a href=\"/profile\" class=\"nav-link {{ACTIVE_PROFILE}}\">Profile</a>");
} else {
    html = html.replace("{{PROFILE_LINK}}", "");
}




            // -------- Saved locations --------
            List<String> locations = new ArrayList<>();

            if (loggedIn) {
                try (Connection conn = DBUtil.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT city FROM saved_locations WHERE user_email = ? ORDER BY id DESC")) {

                    ps.setString(1, userEmail);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            locations.add(rs.getString("city"));
                        }
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }

            StringBuilder listHtml = new StringBuilder();
            if (!loggedIn) {
                listHtml.append(
                        "<li class=\"location-item\">" +
                        "<span class=\"dot\"></span><span>Login to save locations</span>" +
                        "<span class=\"tag-cool\">Guest mode</span>" +
                        "</li>");
            } else if (locations.isEmpty()) {
                listHtml.append(
                        "<li class=\"location-item\">" +
                        "<span class=\"dot\"></span><span>No saved locations yet</span>" +
                        "<span class=\"tag-cool\">Start adding</span>" +
                        "</li>");
            } else {
                for (String city : locations) {
                    listHtml.append(
                            "<li class=\"location-item\">" +
                            "<span class=\"dot\"></span>" +
                            "<span>")
                            .append(escapeHtml(city))
                            .append("</span>" +
                                    "<span class=\"tag-cool\">Saved</span>" +
                                    "</li>");
                }
            }
            // replace saved locations placeholder
            html = html.replace("{{SAVED_LOCATIONS}}", listHtml.toString());

            // -------- Weather history (last 5) --------
            List<HistoryRow> history = new ArrayList<>();

            if (loggedIn) {
                try (Connection conn = DBUtil.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT city, result_text, searched_at " +
                             "FROM weather_history WHERE user_email = ? " +
                             "ORDER BY searched_at DESC LIMIT 5")) {

                    ps.setString(1, userEmail);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            HistoryRow row = new HistoryRow();
                            row.city = rs.getString("city");
                            row.resultText = rs.getString("result_text");
                            row.time = rs.getTimestamp("searched_at").toString();
                            history.add(row);
                        }
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }

            StringBuilder historyHtml = new StringBuilder();
            if (!loggedIn) {
                historyHtml.append(
                        "<li class=\"location-item\">" +
                        "<span class=\"dot\"></span><span>Login to see your history</span>" +
                        "<span class=\"tag-cool\">Guest mode</span>" +
                        "</li>");
            } else if (history.isEmpty()) {
                historyHtml.append(
                        "<li class=\"location-item\">" +
                        "<span class=\"dot\"></span><span>No history yet</span>" +
                        "<span class=\"tag-cool\">Search city</span>" +
                        "</li>");
            } else {
                for (HistoryRow row : history) {
                    historyHtml.append(
                            "<li class=\"location-item\">" +
                            "<span class=\"dot\"></span>" +
                            "<span>")
                            .append(escapeHtml(row.city))
                            .append("</span>" +
                                    "<span class=\"tag-hot\">")
                            .append(row.resultText)   // keep <br> tags
                            .append("</span>" +
                                    "</li>");
                }
            }
            // replace history placeholder
            html = html.replace("{{WEATHER_HISTORY}}", historyHtml.toString());

            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // for history
    static class HistoryRow {
        String city;
        String resultText;
        String time;
    }

    // ================== DASHBOARD (MINI PAGE) ==================
    // Kept here in case you want /dashboard separate later, but not used in main()
    static class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String userName = getUserFromSession(exchange);
            String userEmail = getUserEmailFromCookie(exchange);

            if (userName == null || userName.isBlank() || userEmail == null) {
                Headers headers = exchange.getResponseHeaders();
                headers.add("Location", "/");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
                return;
            }

            File file = new File(WEB_ROOT, "dashboard.html");
            if (!file.exists() || !file.isFile()) {
                String notFound = "404 - File not found (dashboard.html)";
                byte[] bytes = notFound.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }

            String html;
            try (FileInputStream fis = new FileInputStream(file)) {
                html = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
            }

            html = html.replace("Hi, User", "Hi, " + escapeHtml(userName));
            html = html.replace("{{USERNAME}}", escapeHtml(userName));

            // saved locations (same as Home)
            List<String> locations = new ArrayList<>();
            try (Connection conn = DBUtil.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT city FROM saved_locations WHERE user_email = ? ORDER BY id DESC")) {

                ps.setString(1, userEmail);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        locations.add(rs.getString("city"));
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }

            StringBuilder listHtml = new StringBuilder();
            if (locations.isEmpty()) {
                listHtml.append(
                        "<li class=\"location-item\">" +
                        "<span class=\"dot\"></span><span>No saved locations yet</span>" +
                        "<span class=\"tag-cool\">Start adding</span>" +
                        "</li>");
            } else {
                for (String city : locations) {
                    listHtml.append(
                            "<li class=\"location-item\">" +
                            "<span class=\"dot\"></span>" +
                            "<span>")
                            .append(escapeHtml(city))
                            .append("</span>" +
                                    "<span class=\"tag-cool\">Saved</span>" +
                                    "</li>");
                }
            }
            html = html.replace("{{SAVED_LOCATIONS}}", listHtml.toString());

            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // ---------- Profile page (dynamic, from DB) ----------
    static class ProfileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String userName = getUserFromSession(exchange);
            String userEmail = getUserEmailFromCookie(exchange);

            boolean loggedIn = userName != null && !userName.isBlank() && userEmail != null;

            if (!loggedIn) {
                Headers headers = exchange.getResponseHeaders();
                headers.add("Location", "/login");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
                return;
            }

            File file = new File(WEB_ROOT, "profile.html");
            if (!file.exists() || !file.isFile()) {
                String notFound = "404 - File not found (profile.html)";
                byte[] bytes = notFound.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }

            String html;
            try (FileInputStream fis = new FileInputStream(file)) {
                html = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
            }

            // Fetch latest name + email from DB
            String dbName = userName;
            String dbEmail = userEmail;

            try (Connection conn = DBUtil.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT name, email FROM users WHERE email = ?")) {

                ps.setString(1, userEmail);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        dbName = rs.getString("name");
                        dbEmail = rs.getString("email");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            if (dbName == null || dbName.isBlank()) dbName = dbEmail;

            // Populate profile data for the form placeholders
            html = html.replace("{{NAME}}", escapeHtml(dbName));
            html = html.replace("{{EMAIL}}", escapeHtml(dbEmail));

            // Set active nav link
            html = html.replace("{{USERNAME}}", escapeHtml(userName));
            html = html.replace("{{ACTIVE_HOME}}", "");
            html = html.replace("{{ACTIVE_PROFILE}}", "nav-link-active");
            html = html.replace("{{ACTIVE_INFO}}", "");
            html = html.replace("{{STATUS_MESSAGE}}", ""); // no status initially
            // Show Logout in navbar on profile (user is definitely logged in here)
            html = html.replace("{{PROFILE_LINK}}",
        "<a href=\"/profile\" class=\"nav-link {{ACTIVE_PROFILE}}\">Profile</a>");
html = html.replace("{{AUTH_LINK}}",
        "<a href=\"/logout\" class=\"nav-link nav-link-danger\">Logout</a>");

        html = html.replace("{{AUTH_LINK}}",
        "<a href=\"/logout\" class=\"nav-link nav-link-danger\">Logout</a>");


            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // ---------- Profile update handler ----------
    static class UpdateProfileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String currentEmail = getUserEmailFromCookie(exchange);
            String sessionToken = getSessionToken(exchange);

            if (currentEmail == null || sessionToken == null) {
                Headers headers = exchange.getResponseHeaders();
                headers.add("Location", "/login");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            String newName = "";
            String newEmail = "";
            String newPassword = "";

            String[] pairs = body.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    String key = URLDecoder.decode(kv[0], "UTF-8");
                    String value = URLDecoder.decode(kv[1], "UTF-8");
                    if ("name".equals(key)) newName = value;
                    else if ("email".equals(key)) newEmail = value;
                    else if ("password".equals(key)) newPassword = value;
                }
            }

            boolean ok = false;
            String dbError = null;

            if (!newName.isBlank() && !newEmail.isBlank() && !newPassword.isBlank()) {
                try (Connection conn = DBUtil.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "UPDATE users SET name = ?, email = ?, password = ? WHERE email = ?")) {

                    ps.setString(1, newName);
                    ps.setString(2, newEmail);
                    ps.setString(3, newPassword);
                    ps.setString(4, currentEmail);

                    ok = ps.executeUpdate() > 0;

                } catch (SQLException e) {
                    e.printStackTrace();
                    dbError = e.getMessage();
                }
            } else {
                dbError = "All fields are required.";
            }

            // reload profile.html with status message
            File file = new File(WEB_ROOT, "profile.html");
            if (!file.exists() || !file.isFile()) {
                exchange.sendResponseHeaders(500, -1);
                return;
            }

            String html;
            try (FileInputStream fis = new FileInputStream(file)) {
                html = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
            }

            String statusHtml;
            if (ok) {
                // update session display name + email cookie
                String displayName = newName.isBlank() ? newEmail : newName;
                SESSIONS.put(sessionToken, displayName);

                Headers headers = exchange.getResponseHeaders();
                // update email cookie
                String encodedEmail = URLEncoder.encode(newEmail, StandardCharsets.UTF_8);
                headers.add("Set-Cookie", "userEmail=" + encodedEmail + "; Path=/; HttpOnly");

                statusHtml =
                        "<div class='success-box'>" +
                        "<p><b>Profile updated successfully ✔</b></p>" +
                        "</div>";

                html = html.replace("Hi, User", "Hi, " + escapeHtml(displayName));
                html = html.replace("{{NAME}}", escapeHtml(newName));
                html = html.replace("{{EMAIL}}", escapeHtml(newEmail));

            } else {
                // keep old values in form
                String displayName = getUserFromSession(exchange);
                if (displayName == null) displayName = currentEmail;

                html = html.replace("Hi, User", "Hi, " + escapeHtml(displayName));

                String displayFormName = newName.isBlank() ? displayName : newName;
                String displayFormEmail = newEmail.isBlank() ? currentEmail : newEmail;

                html = html.replace("{{NAME}}", escapeHtml(displayFormName));
                html = html.replace("{{EMAIL}}", escapeHtml(displayFormEmail));

                statusHtml =
                        "<div class='success-box' style='background:#fef2f2;border-left-color:#dc2626;color:#991b1b;'>" +
                        "<p><b>Could not update profile.</b></p>" +
                        "<p style='font-size:12px;'>" + escapeHtml(dbError == null ? "Unknown error" : dbError) + "</p>" +
                        "</div>";
            }

            // Set active nav link & username & status
            String sessionName = getUserFromSession(exchange);
            if (sessionName == null) sessionName = currentEmail;

            html = html.replace("{{USERNAME}}", escapeHtml(sessionName));
            html = html.replace("{{ACTIVE_HOME}}", "");
            html = html.replace("{{ACTIVE_PROFILE}}", "nav-link-active");
            html = html.replace("{{ACTIVE_INFO}}", "");
            html = html.replace("{{STATUS_MESSAGE}}", statusHtml);
            html = html.replace("{{PROFILE_LINK}}",
        "<a href=\"/profile\" class=\"nav-link {{ACTIVE_PROFILE}}\">Profile</a>");
html = html.replace("{{AUTH_LINK}}",
        "<a href=\"/logout\" class=\"nav-link nav-link-danger\">Logout</a>");

            html = html.replace("{{AUTH_LINK}}",
        "<a href=\"/logout\" class=\"nav-link nav-link-danger\">Logout</a>");
        


            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // ---------- Logout ----------
    static class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String token = getSessionToken(exchange);
            if (token != null) {
                SESSIONS.remove(token);
            }

            Headers headers = exchange.getResponseHeaders();
            // expire cookies
            headers.add("Set-Cookie", "sessionId=deleted; Path=/; Max-Age=0");
            headers.add("Set-Cookie", "userEmail=deleted; Path=/; Max-Age=0");
            headers.add("Location", "/login");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        }
    }

    // ---------- Serve images ----------
    static class ImageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestPath = exchange.getRequestURI().getPath(); // e.g. /images/logo.png
            String fileName = requestPath.replaceFirst("/images/?", ""); // logo.png

            File imageFile = new File(WEB_ROOT + File.separator + "images", fileName);

            if (!imageFile.exists() || !imageFile.isFile()) {
                String notFound = "404 - Image not found";
                byte[] bytes = notFound.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }

            byte[] imageBytes;
            try (FileInputStream fis = new FileInputStream(imageFile)) {
                imageBytes = fis.readAllBytes();
            }

            String contentType = guessImageContentType(fileName);
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", contentType);

            exchange.sendResponseHeaders(200, imageBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(imageBytes);
            }
        }

        private String guessImageContentType(String fileName) {
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".gif")) return "image/gif";
            return "application/octet-stream";
        }
    }

    // ---------- REGISTER (direct JDBC) ----------
    static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html;
            int status = 200;

            try {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    status = 405;
                    html = "<html><body><h1>Method Not Allowed</h1></body></html>";
                } else {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

                    String name = "";
                    String email = "";
                    String password = "";

                    String[] pairs = body.split("&");
                    for (String pair : pairs) {
                        String[] kv = pair.split("=", 2);
                        if (kv.length == 2) {
                            String key = URLDecoder.decode(kv[0], "UTF-8");
                            String value = URLDecoder.decode(kv[1], "UTF-8");
                            if ("name".equals(key)) name = value;
                            else if ("email".equals(key)) email = value;
                            else if ("password".equals(key)) password = value;
                        }
                    }

                    boolean ok = false;
                    String dbError = null;

                    try (Connection conn = DBUtil.getConnection();
                         PreparedStatement ps = conn.prepareStatement(
                                 "INSERT INTO users(name, email, password) VALUES (?, ?, ?)")) {

                        ps.setString(1, name);
                        ps.setString(2, email);
                        ps.setString(3, password);
                        ok = ps.executeUpdate() > 0;

                    } catch (SQLException e) {
                        e.printStackTrace();
                        dbError = e.getMessage();
                    }

                    if (ok) {
                        html =
                                "<html><head><title>Register</title><link rel='stylesheet' href='/styles.css'></head>" +
                                "<body class='result-page'><div class='result-card'>" +
                                "<span class='badge'>Register</span>" +
                                "<h2>Account created 🎉</h2>" +
                                "<p>Email: " + escapeHtml(email) + "</p>" +
                                "<a href='/login' class='btn-primary'>Go to Login</a>" +
                                "</div></body></html>";
                    } else {
                        html =
                                "<html><head><title>Register</title><link rel='stylesheet' href='/styles.css'></head>" +
                                "<body class='result-page'><div class='result-card'>" +
                                "<span class='badge'>Error</span>" +
                                "<h2>Registration failed 😓</h2>" +
                                "<p class='subtitle'>Could not save user. Maybe email exists or DB issue.</p>";
                        if (dbError != null) {
                            html += "<p style='font-size:12px;color:red;'>" +
                                    escapeHtml(dbError) + "</p>";
                        }
                        html += "<a href='/register' class='btn-primary'>Try Again</a>" +
                                "</div></body></html>";
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                status = 500;
                html =
                        "<html><head><title>Error</title><link rel='stylesheet' href='/styles.css'></head>" +
                        "<body class='result-page'><div class='result-card'>" +
                        "<span class='badge'>Error</span>" +
                        "<h2>Server Error</h2>" +
                        "<p class='subtitle'>" + escapeHtml(e.toString()) + "</p>" +
                        "<a href='/register' class='btn-primary'>Back to Register</a>" +
                        "</div></body></html>";
            }

            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // ---------- LOGIN (direct JDBC + sessions + userEmail cookie) ----------
    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html;
            int status = 200;
            String newSessionToken = null;
            String loggedInEmail = null;

            try {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    status = 405;
                    html = "<html><body><h1>Method Not Allowed</h1></body></html>";
                } else {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

                    String email = "";
                    String password = "";

                    String[] pairs = body.split("&");
                    for (String pair : pairs) {
                        String[] kv = pair.split("=", 2);
                        if (kv.length == 2) {
                            String key = URLDecoder.decode(kv[0], "UTF-8");
                            String value = URLDecoder.decode(kv[1], "UTF-8");
                            if ("email".equals(key)) email = value;
                            else if ("password".equals(key)) password = value;
                        }
                    }

                    boolean loggedIn = false;
                    String dbEmail = null;
                    String dbName = null;
                    String dbError = null;

                    try (Connection conn = DBUtil.getConnection();
                         PreparedStatement ps = conn.prepareStatement(
                                 "SELECT name, email FROM users WHERE email = ? AND password = ?")) {

                        ps.setString(1, email);
                        ps.setString(2, password);

                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                loggedIn = true;
                                dbName = rs.getString("name");
                                dbEmail = rs.getString("email");
                            }
                        }

                    } catch (SQLException e) {
                        e.printStackTrace();
                        dbError = e.getMessage();
                    }

                    if (loggedIn) {
                        String displayName = (dbName != null && !dbName.isBlank()) ? dbName : dbEmail;
                        newSessionToken = UUID.randomUUID().toString();
                        SESSIONS.put(newSessionToken, displayName);
                        loggedInEmail = dbEmail;

                        html =
                                "<html><head><title>Login</title><link rel='stylesheet' href='/styles.css'></head>" +
                                "<body class='result-page'><div class='result-card'>" +
                                "<span class='badge'>Login</span>" +
                                "<h2>Welcome back 👋</h2>" +
                                "<p>Logged in as: " + escapeHtml(displayName) + "</p>" +
                                "<a href='/home' class='btn-primary'>Go to Home</a>" +
                                "</div></body></html>";
                    } else {
                        html =
                                "<html><head><title>Login</title><link rel='stylesheet' href='/styles.css'></head>" +
                                "<body class='result-page'><div class='result-card'>" +
                                "<span class='badge'>Login</span>" +
                                "<h2>Login failed 😓</h2>" +
                                "<p class='subtitle'>Invalid email/password OR user not found.</p>";
                        if (dbError != null) {
                            html += "<p style='font-size:12px;color:red;'>" +
                                    escapeHtml(dbError) + "</p>";
                        }
                        html += "<a href='/login' class='btn-primary'>Try Again</a>" +
                                "<a href='/register' class='link'>Create Account</a>" +
                                "</div></body></html>";
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                status = 500;
                html =
                        "<html><head><title>Error</title><link rel='stylesheet' href='/styles.css'></head>" +
                        "<body class='result-page'><div class='result-card'>" +
                        "<span class='badge'>Error</span>" +
                        "<h2>Server Error</h2>" +
                        "<p class='subtitle'>" + escapeHtml(e.toString()) + "</p>" +
                        "<a href='/login' class='btn-primary'>Back to Login</a>" +
                        "</div></body></html>";
            }

            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", "text/html; charset=UTF-8");

            if (newSessionToken != null && loggedInEmail != null) {
                headers.add("Set-Cookie", "sessionId=" + newSessionToken + "; Path=/; HttpOnly");
                String encodedEmail = URLEncoder.encode(loggedInEmail, StandardCharsets.UTF_8);
                headers.add("Set-Cookie", "userEmail=" + encodedEmail + "; Path=/; HttpOnly");
            }

            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // ---------- LIVE WEATHER (WeatherAPI.com) ----------
    static class WeatherHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String formData = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseFormData(formData);

            String cityRaw = params.getOrDefault("city", "Unknown");
            String city = escapeHtml(cityRaw);

            // TRY CALLING API
            String weatherSummary;
            String forecastHtml = "";
            try {
            String jsonResponse = fetchWeatherJsonFromApi(cityRaw);  // Get raw JSON
            weatherSummary = parseWeatherSummary(jsonResponse);       // Parse current weather
            forecastHtml = parseForecastDays(jsonResponse);           // Parse forecas  t
                }catch (Exception e) {
                e.printStackTrace();
                weatherSummary = "Error fetching live weather ❌";
                forecastHtml = "";
}

            // Save to weather_history if user is logged in and API worked
            String userEmail = getUserEmailFromCookie(exchange);
            if (userEmail != null && !weatherSummary.startsWith("Error")) {
                try (Connection conn = DBUtil.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "INSERT INTO weather_history(user_email, city, result_text) VALUES (?, ?, ?)")) {

                    ps.setString(1, userEmail);
                    ps.setString(2, cityRaw);
                    ps.setString(3, weatherSummary);
                    ps.executeUpdate();

                } catch (SQLException e) {
                    e.printStackTrace(); // just log, don't break the page
                }
            }

     String response =
    "<!DOCTYPE html>" +
    "<html><head>" +
    "<meta charset='UTF-8'>" +
    "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
    "<title>Weather in " + city + "</title>" +
    "<link rel='stylesheet' href='/styles.css'>" +
    "</head>" +
    "<body class='weather-result-body' onload='window.scrollTo(0,0)'>" +
    "<div class='weather-container'>" +
    "<div class='current-weather-card'>" +
    "<div class='weather-header'>" +
    "<h1 class='city-name'>" + city + "</h1>" +
    "<span class='weather-badge'>Live Weather</span>" +
    "</div>" +
    "<div class='current-temp-section'>" +
    "<div class='temp-display'>" + extractTemp(weatherSummary) + "</div>" +
    "<div class='weather-icon-large'>" + getWeatherEmoji(weatherSummary) + "</div>" +
    "</div>" +
    "<div class='weather-condition'>" + extractCondition(weatherSummary) + "</div>" +
    "<div class='weather-details-grid'>" +
    extractWeatherDetails(weatherSummary) +
    "</div>" +
    "</div>" +
    "<div class='forecast-section'>" +
    "<h2 class='forecast-title'>3-Day Forecast</h2>" +
    "<div class='forecast-cards'>" +
    forecastHtml +
    "</div>" +
    "</div>" +
    "<a href='/home' class='back-button'>← Back to Home</a>" +
    "<p class='powered-by'>Powered by WeatherAPI.com</p>" +
    "</div>" +
    "</body></html>";

// ✅ SEND RESPONSE — THIS WAS MISSING
byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
Headers headers = exchange.getResponseHeaders();
headers.add("Content-Type", "text/html; charset=UTF-8");
exchange.sendResponseHeaders(200, bytes.length);

try (OutputStream os = exchange.getResponseBody()) {
    os.write(bytes);
}
    }
}

    static class SaveLocationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }

                String userEmail = getUserEmailFromCookie(exchange);
                if (userEmail == null) {
                    Headers headers = exchange.getResponseHeaders();
                    headers.add("Location", "/login");
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                    return;
                }

                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

                String city = "";
                String[] pairs = body.split("&");
                for (String pair : pairs) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        String key = URLDecoder.decode(kv[0], "UTF-8");
                        String value = URLDecoder.decode(kv[1], "UTF-8");
                        if ("city".equals(key)) city = value;
                    }
                }

                if (!city.isBlank()) {
                    try (Connection conn = DBUtil.getConnection();
                         PreparedStatement ps = conn.prepareStatement(
                                 "INSERT INTO saved_locations(user_email, city) VALUES (?, ?)")) {
                        ps.setString(1, userEmail);
                        ps.setString(2, city);
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }

                Headers headers = exchange.getResponseHeaders();
                headers.add("Location", "/home");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();

            } catch (Exception e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, -1);
            }
        }
    }

    // ================== UTILITIES ==================

    // Call WeatherAPI.com
   private static String fetchWeatherJsonFromApi(String city) throws IOException {
    if (city == null || city.isBlank()) {
        throw new IOException("City is empty");
    }

    String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
    String urlStr = WEATHER_API_BASE_URL
            + "?key=" + WEATHER_API_KEY
            + "&q=" + encodedCity
            + "&days=5"
            + "&aqi=yes";

    HttpURLConnection conn = null;
    try {
        URL url = new URL(urlStr);
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IOException("API response code: " + code);
        }

        try (InputStream is = conn.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);  // Return raw JSON
        }
    } catch (IOException e) {
        e.printStackTrace();
        throw e;
    } finally {
        if (conn != null) conn.disconnect();
    }
}

    // Parse WeatherAPI.com current.json
    private static String parseWeatherSummary(String json) {
        try {
            // TEMP
            double tempC = 0;
            int tempIdx = json.indexOf("\"temp_c\":");
            if (tempIdx != -1) {
                int start = tempIdx + "\"temp_c\":".length();
                int end = json.indexOf(",", start);
                tempC = Double.parseDouble(json.substring(start, end).trim());
            }

            // FEELS LIKE
            double feelsLikeC = 0;
            int feelsIdx = json.indexOf("\"feelslike_c\":");
            if (feelsIdx != -1) {
                int start = feelsIdx + "\"feelslike_c\":".length();
                int end = json.indexOf(",", start);
                feelsLikeC = Double.parseDouble(json.substring(start, end).trim());
            }

            // CONDITION TEXT
            String condition = "";
            int textIdx = json.indexOf("\"text\":\"");
            if (textIdx != -1) {
                int start = textIdx + "\"text\":\"".length();
                int end = json.indexOf("\"", start);
                condition = json.substring(start, end);
            }

            // HUMIDITY
            int humidity = 0;
            int humIdx = json.indexOf("\"humidity\":");
            if (humIdx != -1) {
                int start = humIdx + "\"humidity\":".length();
                int end = json.indexOf(",", start);
                humidity = Integer.parseInt(json.substring(start, end).trim());
            }

            // WIND
            double windKph = 0;
            int windIdx = json.indexOf("\"wind_kph\":");
            if (windIdx != -1) {
                int start = windIdx + "\"wind_kph\":".length();
                int end = json.indexOf(",", start);
                windKph = Double.parseDouble(json.substring(start, end).trim());
            }

            // VISIBILITY
            double visKm = 0;
            int visIdx = json.indexOf("\"vis_km\":");
            if (visIdx != -1) {
                int start = visIdx + "\"vis_km\":".length();
                int end = json.indexOf(",", start);
                visKm = Double.parseDouble(json.substring(start, end).trim());
            }

            // AQI: PM2.5
            double pm25 = 0;
            int pm25Idx = json.indexOf("\"pm2_5\":");
            if (pm25Idx != -1) {
                int start = pm25Idx + "\"pm2_5\":".length();
                int end = json.indexOf(",", start);
                pm25 = Double.parseDouble(json.substring(start, end).trim());
            }

            // AQI: PM10
            double pm10 = 0;
            int pm10Idx = json.indexOf("\"pm10\":");
            if (pm10Idx != -1) {
                int start = pm10Idx + "\"pm10\":".length();
                int end = json.indexOf(",", start);
                pm10 = Double.parseDouble(json.substring(start, end).trim());
            }

            // US EPA INDEX
            int epaIndex = 0;
            int epaIdx = json.indexOf("\"us-epa-index\":");
            if (epaIdx != -1) {
                int start = epaIdx + "\"us-epa-index\":".length();
                int end = json.indexOf(",", start);
                epaIndex = Integer.parseInt(json.substring(start, end).trim());
            }

            String aqiLabel = mapEpaIndexToLabel(epaIndex);

            if (condition.isEmpty()) condition = "No description";

            String tempText  = String.format("%.1f°C", tempC);
            String feelsText = String.format("%.1f°C", feelsLikeC);
            String windText  = String.format("%.1f km/h", windKph);

            // NEW: calculate AQI from PM2.5 only
            int aqiFromPm25 = pm25ToAqi(pm25);

            return ""
                    + tempText + " · " + condition + "<br>"
                    + "Feels like " + feelsText + "<br>"
                    + "Humidity " + humidity + "%, Wind " + windText + "<br>"
                    + String.format("Visibility %.1f km<br>", visKm)
                    + (aqiFromPm25 >= 0
                        ? "AQI " + aqiFromPm25 + " (" + aqiLabel + ")<br>"
                        : "")
                    + String.format("PM2.5 %.1f, PM10 %.1f", pm25, pm10);

        } catch (Exception e) {
            e.printStackTrace();
            return "Live data (raw): " + json.substring(0, Math.min(120, json.length())) + "...";
        }
    }

    // Extract temperature from summary
private static String extractTemp(String summary) {
    try {
        int idx = summary.indexOf("°C");
        if (idx != -1) {
            String temp = summary.substring(0, idx + 2);
            return temp.trim();
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    return "N/A";
}

// Extract condition from summary
private static String extractCondition(String summary) {
    try {
        String[] lines = summary.split("<br>");
        if (lines.length > 0) {
            String firstLine = lines[0];
            int dotIdx = firstLine.indexOf("·");
            if (dotIdx != -1) {
                return firstLine.substring(dotIdx + 1).trim();
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    return "Clear";
}

// Get weather emoji based on condition
private static String getWeatherEmoji(String summary) {
    String lower = summary.toLowerCase();
    if (lower.contains("sunny") || lower.contains("clear")) return "☀️";
    if (lower.contains("cloudy") || lower.contains("overcast")) return "☁️";
    if (lower.contains("rain") || lower.contains("drizzle")) return "🌧️";
    if (lower.contains("thunder") || lower.contains("storm")) return "⛈️";
    if (lower.contains("snow")) return "❄️";
    if (lower.contains("fog") || lower.contains("mist")) return "🌫️";
    return "🌤️";
}

// Extract weather details into grid items
private static String extractWeatherDetails(String summary) {
    StringBuilder details = new StringBuilder();
    
    String[] lines = summary.split("<br>");
    
    for (String line : lines) {
        if (line.contains("Feels like")) {
            String value = line.replace("Feels like", "").trim();
            details.append(
                "<div class='detail-item'>" +
                "<span class='detail-label'>Feels Like</span>" +
                "<span class='detail-value'>").append(value).append("</span>" +
                "</div>");
        }
        else if (line.contains("Humidity")) {
            String[] parts = line.split(",");
            for (String part : parts) {
                if (part.contains("Humidity")) {
                    String value = part.replace("Humidity", "").trim();
                    details.append(
                        "<div class='detail-item'>" +
                        "<span class='detail-label'>Humidity</span>" +
                        "<span class='detail-value'>").append(value).append("</span>" +
                        "</div>");
                }
                if (part.contains("Wind")) {
                    String value = part.replace("Wind", "").trim();
                    details.append(
                        "<div class='detail-item'>" +
                        "<span class='detail-label'>Wind Speed</span>" +
                        "<span class='detail-value'>").append(value).append("</span>" +
                        "</div>");
                }
            }
        }
        else if (line.contains("Visibility")) {
            String value = line.replace("Visibility", "").trim();
            details.append(
                "<div class='detail-item'>" +
                "<span class='detail-label'>Visibility</span>" +
                "<span class='detail-value'>").append(value).append("</span>" +
                "</div>");
        }
        else if (line.contains("AQI")) {
            details.append(
                "<div class='detail-item'>" +
                "<span class='detail-label'>Air Quality</span>" +
                "<span class='detail-value'>").append(line).append("</span>" +
                "</div>");
        }
    }
    
    return details.toString();
}
  private static String parseForecastDays(String json) {
    StringBuilder forecast = new StringBuilder();

    try {
        int start = json.indexOf("\"forecastday\":[");
        if (start == -1) {
            return "<p style='color:white;'>No forecast data available</p>";
        }

        String forecastPart = json.substring(start);

        int count = 0;
        int idx = 0;

        while (count < 5) {
            int dateIdx = forecastPart.indexOf("\"date\":\"", idx);
            if (dateIdx == -1) break;

            // ---- DATE ----
            int dateStart = dateIdx + 8;
            int dateEnd = forecastPart.indexOf("\"", dateStart);
            String date = forecastPart.substring(dateStart, dateEnd);

            // ---- MAX TEMP ----
            double maxTemp = extractDouble(forecastPart, "\"maxtemp_c\":", dateEnd);

            // ---- MIN TEMP ----
            double minTemp = extractDouble(forecastPart, "\"mintemp_c\":", dateEnd);

            // ---- CONDITION ----
            String condition = extractString(forecastPart, "\"text\":\"", dateEnd);

            // ---- RAIN ----
            int rainChance = (int) extractDouble(forecastPart, "\"daily_chance_of_rain\":", dateEnd);

            String emoji = getConditionEmoji(condition);
            String formattedDate = formatDate(date);

            forecast.append(
                "<div class='forecast-card'>" +
                "<div class='forecast-date'>" + formattedDate + "</div>" +
                "<div class='forecast-icon'>" + emoji + "</div>" +
                "<div class='forecast-temps'>" +
                "<span class='temp-high'>" + Math.round(maxTemp) + "°</span>" +
                "<span class='temp-low'>" + Math.round(minTemp) + "°</span>" +
                "</div>" +
                "<div class='forecast-condition'>" + condition + "</div>" +
                "<div class='forecast-rain'>💧 Chance: " + rainChance + "%</div>" +
                "</div>"
            );

            idx = dateEnd;
            count++;
        }

    } catch (Exception e) {
        e.printStackTrace();
        return "<p style='color:white;'>Error parsing forecast</p>";
    }

    return forecast.toString();
}
private static double extractDouble(String src, String key, int from) {
    int idx = src.indexOf(key, from);
    if (idx == -1) return 0;
    int start = idx + key.length();
    int end = src.indexOf(",", start);
    return Double.parseDouble(src.substring(start, end).trim());
}

private static String extractString(String src, String key, int from) {
    int idx = src.indexOf(key, from);
    if (idx == -1) return "";
    int start = idx + key.length();
    int end = src.indexOf("\"", start);
    return src.substring(start, end);
}


// Get emoji based on condition
private static String getConditionEmoji(String condition) {
    String lower = condition.toLowerCase();
    if (lower.contains("sunny") || lower.contains("clear")) return "☀️";
    if (lower.contains("cloudy") || lower.contains("overcast")) return "☁️";
    if (lower.contains("rain") || lower.contains("drizzle")) return "🌧️";
    if (lower.contains("thunder") || lower.contains("storm")) return "⛈️";
    if (lower.contains("snow")) return "❄️";
    if (lower.contains("fog") || lower.contains("mist")) return "🌫️";
    if (lower.contains("partly")) return "⛅";
    return "🌤️";
}

// Format date from YYYY-MM-DD to something nicer
private static String formatDate(String date) {
    try {
        String[] parts = date.split("-");
        String[] months = {"", "Jan", "Feb", "Mar", "Apr", "May", "Jun", 
                          "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        int month = Integer.parseInt(parts[1]);
        int day = Integer.parseInt(parts[2]);
        return months[month] + " " + day;
    } catch (Exception e) {
        return date;
    }
}

    private static String mapEpaIndexToLabel(int idx) {
        return switch (idx) {
            case 1 -> "Good";
            case 2 -> "Moderate";
            case 3 -> "Unhealthy for sensitive groups";
            case 4 -> "Unhealthy";
            case 5 -> "Very Unhealthy";
            case 6 -> "Hazardous";
            default -> "Unknown";
        };
    }

    // Convert PM2.5 (µg/m³) to AQI (0–500) using US EPA breakpoints
    private static int pm25ToAqi(double c) {
        if (c < 0) return -1; // invalid

        double bpLo, bpHi;
        int iLo, iHi;

        if (c <= 12.0) {
            bpLo = 0.0;   bpHi = 12.0;
            iLo = 0;      iHi = 50;
        } else if (c <= 35.4) {
            bpLo = 12.1;  bpHi = 35.4;
            iLo = 51;     iHi = 100;
        } else if (c <= 55.4) {
            bpLo = 35.5;  bpHi = 55.4;
            iLo = 101;    iHi = 150;
        } else if (c <= 150.4) {
            bpLo = 55.5;  bpHi = 150.4;
            iLo = 151;    iHi = 200;
        } else if (c <= 250.4) {
            bpLo = 150.5; bpHi = 250.4;
            iLo = 201;    iHi = 300;
        } else if (c <= 350.4) {
            bpLo = 250.5; bpHi = 350.4;
            iLo = 301;    iHi = 400;
        } else if (c <= 500.4) {
            bpLo = 350.5; bpHi = 500.4;
            iLo = 401;    iHi = 500;
        } else {
            // above official range, just clamp to 500
            return 500;
        }

        double aqi = ((iHi - iLo) / (bpHi - bpLo)) * (c - bpLo) + iLo;
        return (int) Math.round(aqi);
    }

    @SuppressWarnings("unused")
    private static String getFakeWeather(String city) {
        String c = city.toLowerCase();
        if (c.contains("mumbai")) return "30°C, Humid, Chance of rain";
        if (c.contains("pune"))   return "27°C, Cool, Partly cloudy";
        if (c.contains("delhi"))  return "32°C, Hot, Hazy";
        return "28°C, Clear sky (sample data)";
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    // tiny x-www-form-urlencoded parser
    private static Map<String, String> parseFormData(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isEmpty()) return map;

        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    String key = URLDecoder.decode(kv[0], "UTF-8");
                    String value = URLDecoder.decode(kv[1], "UTF-8");
                    map.put(key, value);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }
        return map;
    }

    // CHANGE private → public
public static String getUserFromSession(HttpExchange exchange) {
    String token = getSessionToken(exchange);
    if (token == null) return null;
    return SESSIONS.get(token);
}

public static String getSessionToken(HttpExchange exchange) {
    Headers headers = exchange.getRequestHeaders();
    String cookieHeader = headers.getFirst("Cookie");
    if (cookieHeader == null) return null;

    for (String cookie : cookieHeader.split(";")) {
        cookie = cookie.trim();
        if (cookie.startsWith("sessionId=")) {
            return cookie.substring("sessionId=".length());
        }
    }
    return null;
}

    private static String getUserEmailFromCookie(HttpExchange exchange) {
        Headers headers = exchange.getRequestHeaders();
        String cookieHeader = headers.getFirst("Cookie");
        if (cookieHeader == null) return null;

        String[] cookies = cookieHeader.split(";");
        for (String cookie : cookies) {
            cookie = cookie.trim();
            if (cookie.startsWith("userEmail=")) {
                String encoded = cookie.substring("userEmail=".length());
                try {
                    return URLDecoder.decode(encoded, StandardCharsets.UTF_8.name());
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        }
        return null;
    }

    // ---------- Serve videos ----------
    static class StaticVideoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath().replace("/videos", "");
            File file = new File(WEB_ROOT + File.separator + "videos" + path);

            if (!file.exists()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            byte[] bytes = Files.readAllBytes(file.toPath());

            String contentType = "video/mp4";
            String lower = path.toLowerCase();
            if (lower.endsWith(".ogg")) contentType = "video/ogg";
            else if (lower.endsWith(".webm")) contentType = "video/webm";

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);

            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }
}

