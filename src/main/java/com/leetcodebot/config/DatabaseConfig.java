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

public class DatabaseConfig {
    private static EntityManagerFactory entityManagerFactory;
    private static HttpServer consoleServer;
    private static final String ADMIN_USERNAME = System.getenv().getOrDefault("DB_ADMIN_USERNAME", "admin");
    private static final String ADMIN_PASSWORD = System.getenv().getOrDefault("DB_ADMIN_PASSWORD", "admin");

    public static EntityManagerFactory getEntityManagerFactory() {
        if (entityManagerFactory == null) {
            try {
                entityManagerFactory = Persistence.createEntityManagerFactory("leetcodebotPU");
                startH2Console();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return entityManagerFactory;
    }

    private static void startH2Console() throws IOException {
        // Start H2 Console on a separate port (default Railway port or 8082)
        int port = Integer.parseInt(System.getenv().getOrDefault("H2_CONSOLE_PORT", "8082"));
        consoleServer = HttpServer.create(new InetSocketAddress(port), 0);

        // Proxy the H2 Console with basic auth
        consoleServer.createContext("/h2-console", new H2ConsoleHandler());
        consoleServer.setExecutor(null);
        consoleServer.start();

        System.out.println("H2 Console started on port " + port);
        System.out.println("Access it at: https://your-railway-url/h2-console");
        System.out.println("JDBC URL to use: jdbc:h2:/data/leetcodebot");
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
                    <iframe src="http://localhost:8082/h2-console"></iframe>
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