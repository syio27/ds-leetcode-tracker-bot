package com.leetcodebot.config;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.h2.server.web.WebServlet;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class DatabaseConfig {
    private static EntityManagerFactory entityManagerFactory;
    private static HttpServer consoleServer;
    private static final String ADMIN_USERNAME = System.getenv().getOrDefault("DB_ADMIN_USERNAME", "admin");
    private static final String ADMIN_PASSWORD = System.getenv().getOrDefault("DB_ADMIN_PASSWORD", "admin");

    public static EntityManagerFactory getEntityManagerFactory() {
        if (entityManagerFactory == null) {
            try {
                // Get the database URL from environment or use default
                String dbUrl = System.getenv().getOrDefault("DB_URL", 
                    "jdbc:h2:file:./data/leetcodebot;AUTO_SERVER=TRUE;DB_CLOSE_ON_EXIT=FALSE");
                
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
        consoleServer.createContext("/h2-console", new H2ConsoleHandler());
        consoleServer.setExecutor(null);
        consoleServer.start();

        System.out.println("H2 Console started on port " + port);
        System.out.println("Database URL: " + dbUrl);
        System.out.println("Username: " + System.getenv().getOrDefault("DB_USER", "sa"));
    }

    private static class H2ConsoleHandler implements HttpHandler {
        private final WebServlet h2servlet = new WebServlet();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Check Basic Auth
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth == null || !isValidAuth(auth)) {
                exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"H2 Console\"");
                exchange.sendResponseHeaders(401, -1);
                return;
            }

            // Proxy to H2 Console
            String response = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>H2 Console</title>
                    <style>
                        body { margin: 0; }
                        iframe { border: none; width: 100vw; height: 100vh; }
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
            exchange.getResponseBody().close();
        }

        private boolean isValidAuth(String auth) {
            if (!auth.startsWith("Basic ")) {
                return false;
            }
            String credentials = new String(Base64.getDecoder().decode(auth.substring(6)), StandardCharsets.UTF_8);
            String[] parts = credentials.split(":");
            return parts.length == 2 && 
                   parts[0].equals(ADMIN_USERNAME) && 
                   parts[1].equals(ADMIN_PASSWORD);
        }
    }

    public static void shutdown() {
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
        if (consoleServer != null) {
            consoleServer.stop(0);
        }
    }
} 