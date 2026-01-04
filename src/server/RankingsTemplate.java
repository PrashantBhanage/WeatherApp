package server;

public class RankingsTemplate {

    public static String getPage(String rows) {

        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Air Quality Rankings</title>

    <!-- MAIN APP STYLES -->
    <link rel="stylesheet" href="/styles.css">

    <!-- RANKINGS ONLY -->
    <link rel="stylesheet" href="/rankings.css">
</head>

<body class="app-body">

<!-- ===== SHARED NAVBAR ===== -->
<header class="top-nav">
    <div class="top-nav-left">
        <div class="nav-logo-circle">
            <img src="/images/logo.png" alt="AQI">
        </div>
        <div class="nav-titles">
            <div class="nav-app-name">AQI</div>
            <div class="nav-app-subtitle">Air Quality Index</div>
        </div>
    </div>

    <nav class="top-nav-right">
        <div class="nav-links">
            <a href="/home" class="nav-link">Dashboard</a>
            <a href="/rankings" class="nav-link nav-link-active">Rankings</a>
            <a href="/blogs" class="nav-link {{ACTIVE_BLOGS}}">Blogs</a>
            <a href="/about" class="nav-link">About Us</a>
        </div>
    </nav>
</header>

<!-- ===== MAIN CONTENT ===== -->
<main class="app-main">

    <span class="badge-danger">World's Most Polluted</span>

    <h1 class="rankings-title">World Most Polluted Cities 2025</h1>
    <p class="rankings-subtitle">
        Live AQI based on PM2.5 concentration
    </p>

    <div class="card rankings-card">
        <table class="aq-table">
            <thead>
                <tr>
                    <th>Rank</th>
                    <th>City</th>
                    <th>Country</th>
                    <th>AQI</th>
                    <th>AQI Status</th>
                    <th>Standard Value</th>
                    <th>Follow</th>
                </tr>
            </thead>

            <tbody>
                %s
            </tbody>
        </table>
    </div>

</main>

</body>
</html>
""".formatted(rows);
    }
}
