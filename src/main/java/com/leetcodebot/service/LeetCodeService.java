package com.leetcodebot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import java.io.IOException;
import java.io.FileOutputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LeetCodeService {
    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String recentSubmissionsQuery;
    private String csrfToken;
    private String leetcodeSession;
    private final Properties config;
    private final String configPath;

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private static final String PROBLEM_DETAIL_QUERY = """
        query problemData($titleSlug: String!) {
            question(titleSlug: $titleSlug) {
                difficulty
                title
                titleSlug
            }
        }
    """;

    public LeetCodeService(Properties config) {
        this.client = new OkHttpClient.Builder()
                .followRedirects(true)
                .cookieJar(new CookieJar() {
                    private List<Cookie> cookies = new ArrayList<>();
                    
                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        this.cookies = cookies;
                    }
                    
                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        return cookies;
                    }
                })
                .build();
        this.objectMapper = new ObjectMapper();
        this.apiUrl = config.getProperty("leetcode.api.url");
        this.recentSubmissionsQuery = config.getProperty("leetcode.api.recent_submissions_query");
        this.config = config;
        
        // Get the config file path
        String resourcePath = getClass().getClassLoader().getResource("config.properties").getPath();
        this.configPath = resourcePath;
        
        // Try to use existing tokens or get new ones
        this.csrfToken = config.getProperty("leetcode.csrf_token");
        this.leetcodeSession = config.getProperty("leetcode.session");
        
        if (csrfToken == null || leetcodeSession == null || 
            csrfToken.equals("your_csrf_token_here") || 
            leetcodeSession.equals("your_leetcode_session_here")) {
            String username = config.getProperty("leetcode.username");
            String password = config.getProperty("leetcode.password");
            
            if (username == null || password == null) {
                throw new IllegalStateException("LeetCode credentials not configured. Please set leetcode.username and leetcode.password in config.properties");
            }
            
            try {
                login(username, password);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to login to LeetCode: " + e.getMessage());
            }
        }
    }

    private void login(String username, String password) throws IOException {
        // First, get the CSRF token from the login page
        Request getRequest = new Request.Builder()
                .url("https://leetcode.com/accounts/login/")
                .header("User-Agent", USER_AGENT)
                .build();

        Response getResponse = client.newCall(getRequest).execute();
        String html = getResponse.body().string();
        
        // Extract CSRF token from the page
        Pattern pattern = Pattern.compile("name=\"csrfmiddlewaretoken\" value=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(html);
        if (!matcher.find()) {
            throw new IOException("Could not find CSRF token in login page");
        }
        String csrfmiddlewaretoken = matcher.group(1);

        // Perform login
        RequestBody formBody = new FormBody.Builder()
                .add("login", username)
                .add("password", password)
                .add("csrfmiddlewaretoken", csrfmiddlewaretoken)
                .build();

        Request loginRequest = new Request.Builder()
                .url("https://leetcode.com/accounts/login/")
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://leetcode.com/accounts/login/")
                .header("Origin", "https://leetcode.com")
                .post(formBody)
                .build();

        Response loginResponse = client.newCall(loginRequest).execute();
        
        if (!loginResponse.isSuccessful()) {
            throw new IOException("Login failed: " + loginResponse.code());
        }

        // Extract cookies
        List<Cookie> cookies = client.cookieJar().loadForRequest(HttpUrl.parse("https://leetcode.com"));
        for (Cookie cookie : cookies) {
            if (cookie.name().equals("csrftoken")) {
                this.csrfToken = cookie.value();
            } else if (cookie.name().equals("LEETCODE_SESSION")) {
                this.leetcodeSession = cookie.value();
            }
        }

        if (this.csrfToken == null || this.leetcodeSession == null) {
            throw new IOException("Failed to obtain authentication tokens after login");
        }

        // Update config file with new tokens
        config.setProperty("leetcode.csrf_token", this.csrfToken);
        config.setProperty("leetcode.session", this.leetcodeSession);
        
        try (FileOutputStream out = new FileOutputStream(configPath)) {
            config.store(out, "Updated LeetCode authentication tokens");
        }
    }

    public void refreshTokensIfNeeded() {
        String username = config.getProperty("leetcode.username");
        String password = config.getProperty("leetcode.password");
        
        if (username != null && password != null) {
            try {
                login(username, password);
            } catch (IOException e) {
                System.err.println("Failed to refresh tokens: " + e.getMessage());
            }
        }
    }

    public List<Submission> getRecentSubmissions(String username) throws IOException {
        ObjectNode variables = objectMapper.createObjectNode();
        variables.put("username", username);
        variables.put("limit", 5);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("query", recentSubmissionsQuery);
        requestBody.set("variables", variables);
        requestBody.put("operationName", "recentAcSubmissionList");

        String jsonBody = requestBody.toString();
        System.out.println("Request body: " + jsonBody);

        Request request = new Request.Builder()
                .url(apiUrl)
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .header("Referer", "https://leetcode.com/")
                .header("Origin", "https://leetcode.com")
                .header("Cookie", String.format("csrftoken=%s; LEETCODE_SESSION=%s", csrfToken, leetcodeSession))
                .header("X-Csrftoken", csrfToken)
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("x-requested-with", "XMLHttpRequest")
                .build();

        Response response = null;
        try {
            System.out.println("Making request to LeetCode API...");
            response = client.newCall(request).execute();
            String responseBody = response.body().string();
            
            if (!response.isSuccessful()) {
                System.err.println("LeetCode API request failed with status: " + response.code());
                System.err.println("Response body: " + responseBody);
                throw new IOException("LeetCode API request failed with status: " + response.code());
            }

            JsonNode responseJson = objectMapper.readTree(responseBody);
            JsonNode errors = responseJson.path("errors");
            
            if (!errors.isMissingNode()) {
                String errorMessage = errors.path(0).path("message").asText("Unknown error");
                throw new IOException("LeetCode API error: " + errorMessage);
            }

            JsonNode submissions = responseJson
                    .path("data")
                    .path("recentAcSubmissionList");

            List<Submission> result = new ArrayList<>();
            for (JsonNode submission : submissions) {
                result.add(new Submission(
                    submission.path("id").asText(),
                    submission.path("title").asText(),
                    submission.path("titleSlug").asText(),
                    submission.path("timestamp").asLong()
                ));
            }
            return result;
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    public Map<String, List<Map<String, String>>> getDailyStatistics(String username, Set<String> submissionIds) throws IOException {
        Map<String, List<Map<String, String>>> statistics = new HashMap<>();
        statistics.put("Easy", new ArrayList<>());
        statistics.put("Medium", new ArrayList<>());
        statistics.put("Hard", new ArrayList<>());

        for (String submissionId : submissionIds) {
            Submission submission = getSubmissionById(submissionId);
            if (submission != null) {
                String difficulty = getProblemDifficulty(submission.getTitleSlug());
                Map<String, String> problemInfo = new HashMap<>();
                problemInfo.put("id", submission.getId());
                problemInfo.put("title", submission.getTitle());
                problemInfo.put("titleSlug", submission.getTitleSlug());
                
                statistics.get(difficulty).add(problemInfo);
            }
        }

        return statistics;
    }

    public String getProblemDifficulty(String titleSlug) throws IOException {
        ObjectNode variables = objectMapper.createObjectNode();
        variables.put("titleSlug", titleSlug);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("query", PROBLEM_DETAIL_QUERY);
        requestBody.set("variables", variables);

        Request request = new Request.Builder()
                .url(apiUrl)
                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .header("Referer", "https://leetcode.com/")
                .header("Origin", "https://leetcode.com")
                .header("Cookie", String.format("csrftoken=%s; LEETCODE_SESSION=%s", csrfToken, leetcodeSession))
                .header("X-Csrftoken", csrfToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get problem difficulty: " + response.code());
            }

            JsonNode jsonResponse = objectMapper.readTree(response.body().string());
            return jsonResponse.path("data")
                             .path("question")
                             .path("difficulty")
                             .asText("Unknown");
        }
    }

    private Submission getSubmissionById(String id) {
        // For now, we'll get it from recent submissions
        // In a future enhancement, we could add a specific query for individual submissions
        try {
            List<Submission> submissions = getRecentSubmissions(id);
            return submissions.stream()
                            .filter(s -> s.getId().equals(id))
                            .findFirst()
                            .orElse(null);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static class Submission {
        private final String id;
        private final String title;
        private final String titleSlug;
        private final long submitTime;

        public Submission(String id, String title, String titleSlug, long submitTime) {
            this.id = id;
            this.title = title;
            this.titleSlug = titleSlug;
            this.submitTime = submitTime;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getTitleSlug() { return titleSlug; }
        public long getSubmitTime() { return submitTime; }
    }
} 