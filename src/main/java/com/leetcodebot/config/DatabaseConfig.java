package com.leetcodebot.config;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.io.File;

public class DatabaseConfig {
    private static EntityManagerFactory entityManagerFactory;
    private static HttpServer consoleServer;
    private static final String DEFAULT_DB_PATH = System.getProperty("user.home") + "/leetcodebot-data/db";

    public static EntityManagerFactory getEntityManagerFactory() {
        if (entityManagerFactory == null) {
            try {
                // Get the database URL from environment or construct it
                String dbUrl = System.getenv("DB_URL");
                if (dbUrl == null || dbUrl.isEmpty()) {
                    // Create the database directory if it doesn't exist
                    File dbDir = new File(DEFAULT_DB_PATH).getParentFile();
                    if (!dbDir.exists()) {
                        dbDir.mkdirs();
                    }
                    
                    // Use the absolute path for the database
                    dbUrl = String.format("jdbc:h2:file:%s;AUTO_SERVER=TRUE", DEFAULT_DB_PATH);
                }
                
                System.out.println("Initializing database with URL: " + dbUrl);
                
                // Create properties for persistence unit
                Map<String, String> properties = new HashMap<>();
                properties.put("hibernate.hikari.dataSource.url", dbUrl);
                
                // Create EntityManagerFactory with properties
                entityManagerFactory = Persistence.createEntityManagerFactory("leetcodebotPU", properties);
                
                System.out.println("Successfully created EntityManagerFactory");
                startH2Console(dbUrl);
            } catch (Exception e) {
                System.err.println("Failed to create EntityManagerFactory: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return entityManagerFactory;
    }

    private static void startH2Console(String dbUrl) throws IOException {
        // Start H2 Console on a separate port (default Railway port or 8082)
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8082"));
        consoleServer = HttpServer.create(new InetSocketAddress(port), 0);

        // Proxy the H2 Console with basic auth
        consoleServer.createContext("/", new H2ConsoleHandler());
        consoleServer.setExecutor(null);
        consoleServer.start();

        System.out.println("H2 Console started on port " + port);
        System.out.println("Database URL: " + dbUrl);
        System.out.println("Username: " + System.getenv().getOrDefault("DB_USER", "sa"));
    }

    private static class H2ConsoleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>H2 Console</title>
                    <style>
                        body, html {
                            margin: 0;
                            padding: 0;
                            height: 100%;
                            overflow: hidden;
                        }
                        iframe {
                            width: 100%;
                            height: 100%;
                            border: none;
                        }
                    </style>
                </head>
                <body>
                    <iframe src="/h2-console"></iframe>
                </body>
                </html>
                """;

            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();
        }
    }
} 