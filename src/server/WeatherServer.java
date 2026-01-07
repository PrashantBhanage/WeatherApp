package server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dao.DBUtil;
import server.WeatherServer.ProfileHandler.ImageHandler;
import server.WeatherServer.ProfileHandler.LoginHandler;
import server.WeatherServer.ProfileHandler.RegisterHandler;
import server.WeatherServer.ProfileHandler.SaveLocationHandler;
import server.WeatherServer.ProfileHandler.StaticVideoHandler;
import server.WeatherServer.ProfileHandlerb.UpdateProfileHandler;
import server.WeatherServer.ProfileHandler.WeatherHandler;

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
import java.util.Arrays;
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
            
        

        server.createContext("/rankings", new AirQualityRankingHandler());
     server.createContext("/about", new AboutHandler());
     




             // DashboardHandler removed from routing

            // login page (index.html)
            server.createContext("/login", new FileHandler("index.html", "text/html"));

           // server.createContext("/locations", new FileHandler("locations.html", "text/html"));
            
            server.createContext("/register", new FileHandler("register.html", "text/html"));

            server.createContext("/profile", new ProfileHandler());
            
            // use external InformationHandler class (src/server/InformationHandler.java)
            
            server.createContext("/blogs", new BlogsHandler());

            server.createContext("/logout", new LogoutHandler());

            // ========== STATIC ASSETS ==========
            server.createContext("/styles.css", new FileHandler("styles.css", "text/css"));
            server.createContext("/blogs.css", new FileHandler("blogs.css", "text/css"));
            server.createContext("/rankings.css", new FileHandler("rankings.css", "text/css"));
            server.createContext("/about.css", new FileHandler("about.css", "text/css"));
            server.createContext("/profile.css", new FileHandler("profile.css", "text/css"));
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
            // ===== DASHBOARD DEFAULT WEATHER (SOLAPUR) =====
try {
    String city = "Solapur";

    String json = fetchWeatherJsonFromApi(city);
    String summary = parseWeatherSummary(json);

    String temp = extractTemp(summary);
    String condition = extractCondition(summary);
    String icon = getWeatherEmoji(summary);

    String feelsLike = extractLine(summary, "Feels like");
    String humidity = extractLine(summary, "Humidity");

    int pm25 = extractPm(json, "\"pm2_5\":");
    int pm10 = extractPm(json, "\"pm10\":");
    int aqi = pm25ToAqi(pm25);
    // AQI COLOR CLASS
String aqiClass =
        aqi <= 50 ? "aqi-good" :
        aqi <= 100 ? "aqi-moderate" :
        aqi <= 150 ? "aqi-unhealthy" :
        "aqi-danger";

html = html.replace("{{AQI_CLASS}}", aqiClass);


    String aqiStatus =
        aqi <= 50 ? "GOOD" :
        aqi <= 100 ? "MODERATE" :
        aqi <= 150 ? "UNHEALTHY" :
        aqi <= 200 ? "VERY UNHEALTHY" :
        "HAZARDOUS";


    html = html.replace("{{CITY_NAME}}", city);
    html = html.replace("{{TEMP_C}}", temp);
    html = html.replace("{{CONDITION}}", condition);
    html = html.replace("{{WEATHER_ICON}}", icon);
    html = html.replace("{{FEELS_LIKE}}", feelsLike);
    html = html.replace("{{HUMIDITY}}", humidity);
    html = html.replace("{{AQI}}", String.valueOf(aqi));
    html = html.replace("{{PM25}}", String.valueOf(pm25));
    html = html.replace("{{PM10}}", String.valueOf(pm10));
    html = html.replace("{{AQI_STATUS}}", aqiStatus);
    String weatherBadge =
        aqi <= 50 ? "Excellent" :
        aqi <= 100 ? "Pleasant" :
        aqi <= 150 ? "Moderate" :
        "Poor";

html = html.replace("{{WEATHER_BADGE}}", weatherBadge);





    html = html.replace("{{LAST_UPDATED}}",
    java.time.LocalDateTime.now()
        .withNano(0)
        .toString()
        .replace("T", " "));

} catch (Exception e) {
    e.printStackTrace();
}


           html = html.replace(
    "{{NAVBAR}}",
    renderNavbar(exchange, "home")
);




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
        
        // ✅ INJECT NAVBAR with correct active page
        html = html.replace("{{NAVBAR}}", renderNavbar(exchange, "profile"));

        // Fetch latest name + avatar from DB
        String dbName = userName;
        String dbEmail = userEmail;

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT name, avatar FROM users WHERE email = ?")) {

            ps.setString(1, userEmail);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    dbName = rs.getString("name");
                    String avatar = rs.getString("avatar");

                    if (avatar == null || avatar.isBlank()) {
                        html = html.replace("{{AVATAR_URL}}", "/images/default-avatar.png");
                    } else {
                        html = html.replace(
                            "{{AVATAR_URL}}",
                            "/images/" + avatar + "?t=" + System.currentTimeMillis()
                        );
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (dbName == null || dbName.isBlank()) dbName = dbEmail;

        // Populate profile data
        html = html.replace("{{NAME}}", escapeHtml(dbName));
        html = html.replace("{{EMAIL}}", escapeHtml(dbEmail));
        html = html.replace("{{USERNAME}}", escapeHtml(dbName));
        html = html.replace("{{STATUS_MESSAGE}}", "");
        
        // ❌ REMOVE ALL THESE - They're already in the navbar:
        // html = html.replace("{{ACTIVE_HOME}}", "");
        // html = html.replace("{{ACTIVE_PROFILE}}", "nav-link-active");
        // html = html.replace("{{ACTIVE_INFO}}", "");
        // html = html.replace("{{PROFILE_LINK}}", ...);
        // html = html.replace("{{AUTH_LINK}}", ...);

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.add("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
private static String applyNavbar(HttpExchange exchange, String html) {
    String sessionId = getSessionId(exchange);
    String username = sessionId == null ? null : SESSIONS.get(sessionId);

    if (username != null) {
        html = html.replace("{{AUTH_LINK}}", "<a href='/logout' class='nav-link'>Logout</a>");
        html = html.replace("{{PROFILE_LINK}}", "<a href='/profile' class='nav-link'>Profile</a>");
        html = html.replace("{{USERNAME}}", username);
    } else {
        html = html.replace("{{AUTH_LINK}}", "<a href='/login' class='nav-link'>Login</a>");
        html = html.replace("{{PROFILE_LINK}}", "");
        html = html.replace("{{USERNAME}}", "Guest");
    }

    return html;
}


   
   // ================== FIXED UPDATE HANDLER ==================
    static class UpdateProfileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println(">>> Update Profile Request Received"); // Debug

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String userEmail = getUserEmailFromCookie(exchange);
            if (userEmail == null) {
                System.out.println(">>> User not logged in"); // Debug
                exchange.getResponseHeaders().add("Location", "/login");
                exchange.sendResponseHeaders(302, -1);
                return;
            }

            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.contains("multipart/form-data")) {
                System.out.println(">>> Content type is not multipart: " + contentType); // Debug
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            // 1. Extract Boundary safely
            String boundary = "";
            for (String part : contentType.split(";")) {
                part = part.trim();
                if (part.startsWith("boundary=")) {
                    boundary = part.substring("boundary=".length());
                    // Remove quotes if present
                    if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
                        boundary = boundary.substring(1, boundary.length() - 1);
                    }
                    break;
                }
            }

            if (boundary.isEmpty()) {
                System.out.println(">>> No boundary found in header"); // Debug
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            // The body uses "--" + boundary
            String finalBoundary = "--" + boundary;
            
            // 2. Read the entire body
            byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
            System.out.println(">>> Body bytes read: " + bodyBytes.length); // Debug

            // 3. Extract Data
            String name = extractMultipartField(bodyBytes, finalBoundary, "name");
           String password = extractMultipartField(bodyBytes, finalBoundary, "password");



            // Use the "Fixed" version you wrote
String avatarPath = extractAndSaveAvatar(bodyBytes, finalBoundary, userEmail);

if (avatarPath == null || avatarPath.isBlank()) {
    avatarPath = getExistingAvatarPath(userEmail);
}



            System.out.println(">>> Extracted Name: " + name); // Debug
            System.out.println(">>> Extracted Avatar Path: " + avatarPath); // Debug
            System.out.println(">>> FINAL avatarPath before DB: " + avatarPath);


            // 4. Update Database
            try (Connection conn = DBUtil.getConnection()) {
                // If name is empty, keep existing name
                if (name == null || name.isBlank()) {
                    name = getUserNameFromDB(userEmail);
                }

              
                String sql;
                PreparedStatement ps;

                if (password != null && !password.isBlank()) {
                    sql = "UPDATE users SET name = ?, avatar = ?, password = ? WHERE email = ?";
                    ps = conn.prepareStatement(sql);
                    ps.setString(1, name);
                    ps.setString(2, avatarPath);
                    ps.setString(3, password);
                    ps.setString(4, userEmail);
                } else {
                    sql = "UPDATE users SET name = ?, avatar = ? WHERE email = ?";
                    ps = conn.prepareStatement(sql);
                    ps.setString(1, name);
                    ps.setString(2, avatarPath);
                    ps.setString(3, userEmail);
                }

                int rows = ps.executeUpdate();
                System.out.println(">>> DB Updated, Rows affected: " + rows); // Debug
                ps.close();

            } catch (SQLException e) {
                e.printStackTrace();
            }

            // 5. Redirect back to profile
            exchange.getResponseHeaders().add("Location", "/profile?updated=true");
            exchange.sendResponseHeaders(302, -1);
        }
    }

    // ================== FIXED MULTIPART PARSERS ==================

    /**
     * Extracts a simple text field (like name or password)
     */
    private static String extractMultipartField(byte[] bodyBytes, String boundary, String fieldName) {
        // We use ISO_8859_1 because it maps bytes 1-to-1 to characters, keeping indices valid
        String body = new String(bodyBytes, StandardCharsets.ISO_8859_1);
        
        String searchKey = "name=\"" + fieldName + "\"";
        int idx = body.indexOf(searchKey);
        
        if (idx == -1) return null;

        // Find the double newline indicating start of content
        int start = body.indexOf("\r\n\r\n", idx);
        if (start == -1) return null;
        start += 4; // skip the \r\n\r\n

        // Find the next boundary indicating end of content
        int end = body.indexOf(boundary, start);
        if (end == -1) return null;

        // The content is between start and end (minus the trailing \r\n)
        String value = body.substring(start, end).trim(); 
        return new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8); // Convert back to UTF-8
    }

    /**
     * Extracts the file binary and saves it to disk
     */
    private static String extractAndSaveAvatar(byte[] bodyBytes, String boundary, String userEmail) {
        try {
            // Map bytes to string for searching
            String body = new String(bodyBytes, StandardCharsets.ISO_8859_1);

            // 1. Find the File Part
            int nameIdx = body.indexOf("name=\"avatar\"");
            if (nameIdx == -1) {
                System.out.println(">>> Parser: 'name=avatar' not found");
                return null;
            }

            // 2. Find the Filename
            int filenameIdx = body.indexOf("filename=\"", nameIdx);
            if (filenameIdx == -1) {
                System.out.println(">>> Parser: 'filename=' not found (User might not have selected a file)");
                return null;
            }

            int fnStart = filenameIdx + 10;
            int fnEnd = body.indexOf("\"", fnStart);
            String originalFilename = body.substring(fnStart, fnEnd);

            if (originalFilename.isEmpty()) {
                System.out.println(">>> Parser: Filename is empty");
                return null;
            }

            // 3. Find Start of Binary Data (after Content-Type and blank line)
            int partStart = body.indexOf("\r\n\r\n", filenameIdx);
            if (partStart == -1) return null;
            partStart += 4; // skip \r\n\r\n

            // 4. Find End of Binary Data (the next boundary)
            int partEnd = body.indexOf(boundary, partStart);
            if (partEnd == -1) {
                System.out.println(">>> Parser: Could not find end boundary");
                return null;
            }
            
            // Remove the \r\n that comes before the boundary
            partEnd = partEnd - 2; 

            System.out.println(">>> Parser: Saving file size: " + (partEnd - partStart) + " bytes");

            // 5. Extract the exact bytes
            byte[] imageBytes = Arrays.copyOfRange(bodyBytes, partStart, partEnd);

            // 6. Ensure Directory Exists
            File dir = new File(WEB_ROOT + File.separator + "images" + File.separator + "avatars");
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                System.out.println(">>> Created directory: " + created);
            }

            // 7. Create Unique Filename
            String extension = ".png";
            if (originalFilename.toLowerCase().endsWith(".jpg")) extension = ".jpg";
            if (originalFilename.toLowerCase().endsWith(".jpeg")) extension = ".jpeg";

            String newFilename = "avatar_" + Math.abs(userEmail.hashCode()) + "_" + System.currentTimeMillis() + extension;
            File targetFile = new File(dir, newFilename);

            // 8. Write to Disk
            Files.write(targetFile.toPath(), imageBytes);
            System.out.println(">>> File Saved at: " + targetFile.getAbsolutePath());

            // Return path for DB (relative to /images/)
            return "avatars/" + newFilename;

        } catch (Exception e) {
            System.out.println(">>> Error saving avatar: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }



// ✅ Helper to get file extension
private static String getFileExtension(String filename) {
    if (filename == null || filename.isEmpty()) {
        return ".png";
    }
    int dotIndex = filename.lastIndexOf('.');
    if (dotIndex == -1 || dotIndex == filename.length() - 1) {
        return ".png";
    }
    return filename.substring(dotIndex).toLowerCase();
}

// ✅ Helper to get existing user name
private static String getUserNameFromDB(String email) {
    try (Connection conn = DBUtil.getConnection();
         PreparedStatement ps = conn.prepareStatement("SELECT name FROM users WHERE email = ?")) {
        
        ps.setString(1, email);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getString("name");
            }
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }
    return email; // fallback to email
}

    // ---------- Serve images (FIXED) ----------
static class ImageHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath(); 
        
        // Remove "/images" from the start
        String fileName = requestPath.replaceFirst("^/images", "");
        
        // CRITICAL FIX: Remove leading slash if present (e.g., "/avatars/..." -> "avatars/...")
        if (fileName.startsWith("/") || fileName.startsWith("\\")) {
            fileName = fileName.substring(1);
        }

        // Now it properly joins: "web/images" + "avatars/file.png"
        File imageFile = new File(WEB_ROOT + File.separator + "images", fileName);

        if (!imageFile.exists() || !imageFile.isFile()) {
            String notFound = "404 - Image not found";
            exchange.sendResponseHeaders(404, notFound.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(notFound.getBytes(StandardCharsets.UTF_8));
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
        // Add cache control so the browser doesn't cache old profile pics forever
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");

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
        if (lower.endsWith(".webp")) return "image/webp";
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
                   

                    String name = "";
                    String email = "";
                    String password = "";
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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
    if (lower.contains("sunny") || lower.contains("clear")) return "🔆";
    if (lower.contains("cloudy") || lower.contains("overcast")) return "🌥️";
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
    private static String extractLine(String summary, String key) {
    for (String line : summary.split("<br>")) {
        if (line.contains(key)) {
            return line.replace(key, "").trim();
        }
    }
    return "N/A";
}

private static int extractPm(String json, String key) {
    int idx = json.indexOf(key);
    if (idx == -1) return 0;
    int start = idx + key.length();
    int end = json.indexOf(",", start);
    return (int) Double.parseDouble(json.substring(start, end).trim());
}

private static String extractFormField(String body, String field) {
    int idx = body.indexOf("name=\"" + field + "\"");
    if (idx == -1) return "";

    int start = body.indexOf("\r\n\r\n", idx);
    if (start == -1) return "";
    start += 4;

    int end = body.indexOf("\r\n--", start);
    if (end == -1) return "";

    return body.substring(start, end).trim();
}




private static String getExistingAvatarPath(String email) {
    try (Connection conn = DBUtil.getConnection();
         PreparedStatement ps = conn.prepareStatement(
             "SELECT avatar FROM users WHERE email = ?")) {

        ps.setString(1, email);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getString("avatar");
            }
        }

    } catch (SQLException e) {
        e.printStackTrace();
    }
    return null;
}
public static String renderNavbar(HttpExchange exchange, String activePage) {
    String userName = getUserFromSession(exchange);
    boolean loggedIn = userName != null;
    if (!loggedIn) userName = "Guest";

    String home = "";
    String blogs = "";
    String rankings = "";
    String profile = "";  // ADD THIS

    if ("home".equals(activePage)) home = "nav-link-active";
    if ("blogs".equals(activePage)) blogs = "nav-link-active";
    if ("rankings".equals(activePage)) rankings = "nav-link-active";
    if ("profile".equals(activePage)) profile = "nav-link-active";  // ADD THIS

    String profileLink = loggedIn
            ? "<a href='/profile' class='nav-link " + profile + "'>Profile</a>"  // ADD ACTIVE CLASS
            : "";

    String authLink = loggedIn
            ? "<a href='/logout' class='nav-link nav-link-danger'>Logout</a>"
            : "<a href='/login' class='nav-link'>Login</a>";

    return """
<header class="top-nav">
    <div class="top-nav-left">
        <div class="nav-logo-circle">
            <img src="/images/logo.png" alt="AQI Logo">
        </div>
        <div class="nav-titles">
            <div class="nav-app-name">AQI</div>
            <div class="nav-app-subtitle">Hi, %s</div>
        </div>
    </div>

    <nav class="top-nav-right">
        <div class="nav-links">
            <a href="/home" class="nav-link %s">Dashboard</a>
            <a href="/blogs" class="nav-link %s">Blogs</a>
            <a href="/rankings" class="nav-link %s">Rankings</a>
            %s
            %s
        </div>
    </nav>
</header>
""".formatted(userName, home, blogs, rankings, profileLink, authLink);
}



}

