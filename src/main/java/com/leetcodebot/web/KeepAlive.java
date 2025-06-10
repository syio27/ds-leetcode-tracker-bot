package com.leetcodebot.web;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;

public class KeepAlive {
    private final HttpServer server;

    public KeepAlive() throws IOException {
        // Create a simple HTTP server on port 8080 (or get from environment)
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Add a simple handler that returns "Bot is running!"
        server.createContext("/", exchange -> {
            String response = "Bot is running!";
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.getResponseBody().close();
        });
        
        server.setExecutor(null);
    }

    public void start() {
        server.start();
        System.out.println("Keep-alive server started on port " + server.getAddress().getPort());
    }
} 