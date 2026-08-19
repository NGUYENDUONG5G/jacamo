package jacamo.platform;

import java.io.*;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import jason.mas2j.AgentParameters;

/**
 * Web Inspector for JaCaMo Goal Modeling & Failure Recovery Visual Studio.
 * Serves the Goal Modeler UI on port 3274 (or next available).
 */
public class GoalModelWebInspector extends DefaultPlatformImpl {

    private static GoalModelWebInspector singleton = null;
    public static GoalModelWebInspector get() { return singleton; }

    private static final Logger logger = Logger.getLogger(GoalModelWebInspector.class.getName());

    private HttpServer httpServer = null;
    private int httpServerPort = 3274;
    private String httpServerURL = "http://localhost:" + httpServerPort;
    private boolean webOn = true;

    @Override
    public void init(String[] args) {
        singleton = this;
        if (args != null && args.length == 1) {
            webOn = !"false".equalsIgnoreCase(args[0]);
        }
        if (webOn) {
            startHttpServer();
        }
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
        if (httpServer != null) {
            try {
                httpServer.stop(0);
                httpServer = null;
                logger.info("GoalModelWebInspector HTTP Server stopped.");
            } catch (Exception e) {
                logger.warning("Error stopping GoalModelWebInspector: " + e.getMessage());
            }
        }
    }

    public synchronized String startHttpServer() {
        if (httpServer == null) {
            try {
                httpServer = HttpServer.create(new InetSocketAddress(httpServerPort), 0);
                httpServer.setExecutor(Executors.newCachedThreadPool());
                registerHttpHandlers();
                httpServer.start();
                httpServerURL = "http://localhost:" + httpServerPort;
                System.out.println("🎯 JaCaMo Goal Model Web Inspector running on " + httpServerURL);
            } catch (BindException e) {
                System.out.println("Port " + httpServerPort + " already in use, trying next port...");
                httpServerPort++;
                return startHttpServer();
            } catch (IOException e) {
                logger.warning("Error starting GoalModelWebInspector HTTP Server: " + e.getMessage());
                return null;
            }
        }
        return httpServerURL;
    }

    protected void registerHttpHandlers() {
        if (httpServer == null) return;

        // Static files handler
        httpServer.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String path = exchange.getRequestURI().getPath();
                if (path == null || path.equals("/") || path.isEmpty()) {
                    path = "/index.html";
                }

                // Check API routes
                if (path.startsWith("/api/")) {
                    handleApiRequest(exchange, path);
                    return;
                }

                byte[] fileBytes = loadResourceOrFile(path);
                if (fileBytes != null) {
                    String contentType = getMimeType(path);
                    Headers headers = exchange.getResponseHeaders();
                    headers.set("Content-Type", contentType);
                    headers.set("Access-Control-Allow-Origin", "*");
                    exchange.sendResponseHeaders(200, fileBytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(fileBytes);
                    os.close();
                } else {
                    String msg = "404 Not Found: " + path;
                    exchange.sendResponseHeaders(404, msg.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(msg.getBytes(StandardCharsets.UTF_8));
                    os.close();
                }
            }
        });
    }

    private void handleApiRequest(HttpExchange exchange, String apiPath) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Access-Control-Allow-Origin", "*");

        if (apiPath.equals("/api/project-asl")) {
            String json = buildProjectAslJson();
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
            return;
        }

        String notFound = "{\"error\": \"Unknown API endpoint\"}";
        byte[] bytes = notFound.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(404, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private String buildProjectAslJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"agents\": [");

        boolean first = true;
        java.util.Set<String> processedPaths = new java.util.HashSet<>();

        try {
            if (this.project != null && this.project.getAgents() != null) {
                for (AgentParameters ap : this.project.getAgents()) {
                    String agName = ap.getAgName();
                    String srcPath = "";
                    String code = "";
                    try {
                        if (ap.getSourceAsFile() != null && ap.getSourceAsFile().exists()) {
                            File f = ap.getSourceAsFile();
                            srcPath = f.getPath();
                            processedPaths.add(f.getAbsolutePath());
                            code = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                        }
                    } catch (Exception ignored) {}

                    if (!first) sb.append(",");
                    first = false;
                    sb.append("{");
                    sb.append("\"name\": \"").append(escapeJson(agName)).append("\",");
                    sb.append("\"aslSource\": \"").append(escapeJson(srcPath)).append("\",");
                    sb.append("\"aslCode\": \"").append(escapeJson(code)).append("\"");
                    sb.append("}");
                }
            }
        } catch (Exception e) {
            logger.warning("Error reading agents from project: " + e.getMessage());
        }

        // Fallback: Scan src/agt directory for .asl files
        try {
            File agtDir = new File("src/agt");
            if (agtDir.exists() && agtDir.isDirectory()) {
                java.util.List<File> aslFiles = new java.util.ArrayList<>();
                collectAslFiles(agtDir, aslFiles);
                for (File f : aslFiles) {
                    if (!processedPaths.contains(f.getAbsolutePath())) {
                        String agName = f.getName().replace(".asl", "");
                        String code = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                        if (!first) sb.append(",");
                        first = false;
                        sb.append("{");
                        sb.append("\"name\": \"").append(escapeJson(agName)).append("\",");
                        sb.append("\"aslSource\": \"").append(escapeJson(f.getPath())).append("\",");
                        sb.append("\"aslCode\": \"").append(escapeJson(code)).append("\"");
                        sb.append("}");
                    }
                }
            }
        } catch (Exception ignored) {}

        sb.append("]}");
        return sb.toString();
    }

    private void collectAslFiles(File dir, java.util.List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectAslFiles(f, result);
            } else if (f.isFile() && f.getName().endsWith(".asl")) {
                result.add(f);
            }
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private byte[] loadResourceOrFile(String relativePath) {
        String cleanPath = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;

        // 1. Try local project path: tools/asl-visual-editor/dist/... or resources
        String[] possibleFilePaths = {
            "tools/asl-visual-editor/dist/" + cleanPath,
            "../../tools/asl-visual-editor/dist/" + cleanPath,
            "src/main/resources/asl-visual-editor/" + cleanPath,
            "../../src/main/resources/asl-visual-editor/" + cleanPath,
            cleanPath
        };

        for (String p : possibleFilePaths) {
            File f = new File(p);
            if (f.exists() && f.isFile()) {
                try {
                    return Files.readAllBytes(f.toPath());
                } catch (IOException ignored) {}
            }
        }

        // 2. Try ClassLoader Resource: /asl-visual-editor/...
        try (InputStream is = getClass().getResourceAsStream("/asl-visual-editor/" + cleanPath)) {
            if (is != null) {
                return is.readAllBytes();
            }
        } catch (Exception ignored) {}

        return null;
    }

    private String getMimeType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".html")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".asl")) return "text/plain; charset=utf-8";
        if (lower.endsWith(".jcm")) return "text/plain; charset=utf-8";
        return "text/plain; charset=utf-8";
    }
}
