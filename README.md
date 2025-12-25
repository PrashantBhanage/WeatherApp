# WeatherApp

A **core Java Weather Application** built using the built-in `HttpServer` API, JDBC for database connectivity, and a static HTML/CSS frontend. This project demonstrates backend fundamentals without using frameworks like Spring, making the logic transparent and interview-friendly.

---

##  Features

* User registration & login
* Weather dashboard
* Profile management
* Static pages (Home, Information, Profile)
* MySQL database integration using JDBC
* Serves HTML, CSS, images, and videos

---

##  Tech Stack

* **Language:** Java (Core Java)
* **Backend:** `com.sun.net.httpserver.HttpServer`
* **Database:** MySQL
* **Frontend:** HTML, CSS
* **Build Tool:** None (manual compilation)

---

##  Project Structure

```
WeatherApp/
├── src/
│   ├── dao/        # Database access classes
│   ├── model/     # Java models
│   └── server/    # HTTP server & handlers
├── web/            # HTML, CSS, images, videos
├── lib/            # External JARs (MySQL Connector)
├── out/            # Compiled .class files (ignored in git)
├── .gitignore
└── README.md
```

---

## How to Run (CMD Method)

### 1) Go to project directory

```bash
cd WeatherApp
```

### 2) Compile the project

```bash
javac -cp ".;lib/mysql-connector-j-9.2.0.jar" -d out src/dao/*.java src/model/*.java src/server/*.java
```

### 3) Run the server

```bash
java -cp ".;out;lib/mysql-connector-j-9.2.0.jar" server.WeatherServer
```

### 4) Open in browser

```
http://localhost:8080
```

---

## Run Using VS Code

1. Open the **WeatherApp** folder in VS Code
2. Open `src/server/WeatherServer.java`
3. Right-click and choose **Run Java**

---

##  Database Setup

* Make sure **MySQL Server** is running
* Update DB credentials in:

```
src/dao/DBUtil.java
```

Example fields to check:

* Database name
* Username
* Password
* Port (usually 3306)


## Notes

* `out/` folder contains compiled files and is ignored by Git
* `.gitignore` is configured properly
* Suitable for BCA final-year project and MCA interviews

---

## Author

**Prashant Bhanage**
GitHub: [https://github.com/PrashantBhanage](https://github.com/PrashantBhanage)
