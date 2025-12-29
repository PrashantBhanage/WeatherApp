package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class AirQualityRankingHandler implements HttpHandler {

    static class AQICity {
        int rank;
        String city;
        String country;
        String countryCode;
        int aqi;

        AQICity(int rank, String city, String country, String countryCode, int aqi) {
            this.rank = rank;
            this.city = city;
            this.country = country;
            this.countryCode = countryCode;
            this.aqi = aqi;
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        List<AQICity> cities = List.of(
            new AQICity(1, "Samalkha", "India", "in", 676),
            new AQICity(2, "Baramulla", "India", "in", 666),
            new AQICity(3, "Jatani", "India", "in", 585),
            new AQICity(4, "Eluru", "India", "in", 581),
            new AQICity(5, "Khairabad", "India", "in", 577),
            new AQICity(6, "Pedana", "India", "in", 555),
            new AQICity(7, "Bhubaneswar", "India", "in", 532),
            new AQICity(8, "Bhimavaram", "India", "in", 527),
            new AQICity(9, "Narasapur", "India", "in", 509),
            new AQICity(10, "Ghazipur", "India", "in", 493)
        );

        StringBuilder rows = new StringBuilder();

        for (AQICity c : cities) {

            String level =
                    c.aqi >= 300 ? "hazardous" :
                    c.aqi >= 200 ? "very-unhealthy" :
                    c.aqi >= 150 ? "unhealthy" :
                    "moderate";

            rows.append("""
                <tr>
                    <td>%d</td>
                    <td class="city-cell">
                        <img src="/images/flags/%s.png">
                        %s
                    </td>
                    <td>%s</td>
                    <td>
                        <div class="aqi-meter aqi-%s">%d</div>
                    </td>
                    <td class="status %s">%s</td>
                    <td><b>%dx</b> above standard</td>
                    <td>♡</td>
                </tr>
            """.formatted(
                    c.rank,
                    c.countryCode,
                    c.city,
                    c.country,
                    level,
                    c.aqi,
                    level,
                    level.replace("-", " "),
                    c.aqi / 15
            ));
        }

        String html = RankingsTemplate.getPage(rows.toString());

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
