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

        httpServer.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String path = exchange.getRequestURI().getPath();
                if (path == null || path.equals("/") || path.isEmpty()) {
                    path = "/index.html";
                }

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
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        if (apiPath.equals("/api/project-asl")) {
            String json = buildProjectAslJson();
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
            return;
        }

        if (apiPath.equals("/api/simulation/state")) {
            String json = buildSimulationStateJson();
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
            return;
        }

        if (apiPath.equals("/api/simulation/inject-belief") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = readRequestBody(exchange);
            String responseJson = handleInjectBelief(body);
            byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
            return;
        }

        if (apiPath.equals("/api/simulation/artifact-op") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = readRequestBody(exchange);
            String responseJson = handleArtifactOp(body);
            byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
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

    private String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    private String getJsonField(String json, String fieldName) {
        if (json == null) return "";
        String pattern = "\"" + fieldName + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    private String buildSimulationStateJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"agents\": [");

        boolean firstAg = true;
        try {
            if (jason.infra.local.RunLocalMAS.getRunner() != null) {
                var agsMap = jason.infra.local.RunLocalMAS.getRunner().getAgs();
                if (agsMap != null) {
                    for (String agName : agsMap.keySet()) {
                        var agArch = jason.infra.local.RunLocalMAS.getRunner().getAg(agName);
                        if (agArch != null && agArch.getTS() != null && agArch.getTS().getAg() != null) {
                            if (!firstAg) sb.append(",");
                            firstAg = false;
                            sb.append("{");
                            sb.append("\"name\": \"").append(escapeJson(agName)).append("\",");
                            sb.append("\"beliefs\": [");

                            boolean firstBel = true;
                            jason.asSemantics.Agent ag = agArch.getTS().getAg();
                            for (jason.asSyntax.Literal bel : ag.getBB()) {
                                if (bel != null && !bel.isRule()) {
                                    if (!firstBel) sb.append(",");
                                    firstBel = false;
                                    sb.append("\"").append(escapeJson(bel.toString())).append("\"");
                                }
                            }
                            sb.append("]");
                            sb.append("}");
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error reading agents state: " + e.getMessage());
        }

        sb.append("], \"workspaces\": [");

        boolean firstWsp = true;
        try {
            if (this.project != null && this.project.getWorkspaces() != null) {
                cartago.CartagoEnvironment cenv = cartago.CartagoEnvironment.getInstance();
                for (jacamo.project.JaCaMoWorkspaceParameters wp : this.project.getWorkspaces()) {
                    String wName = wp.getName();
                    if (!firstWsp) sb.append(",");
                    firstWsp = false;
                    sb.append("{");
                    sb.append("\"name\": \"").append(escapeJson(wName)).append("\",");
                    sb.append("\"artifacts\": [");

                    boolean firstArt = true;
                    if (wp.getArtifacts() != null) {
                        for (java.util.Map.Entry<String, jason.mas2j.ClassParameters> entry : wp.getArtifacts().entrySet()) {
                            String artName = entry.getKey();
                            String artType = entry.getValue() != null ? entry.getValue().getClassName() : "Artifact";
                            if (!firstArt) sb.append(",");
                            firstArt = false;
                            sb.append("{");
                            sb.append("\"name\": \"").append(escapeJson(artName)).append("\",");
                            sb.append("\"type\": \"").append(escapeJson(artType)).append("\",");
                            sb.append("\"properties\": [");

                            boolean firstProp = true;
                            if (cenv != null) {
                                String[] candidates = { "/main/" + wName, wName, "/main/" + wName.replace("/main/", "") };
                                for (String cWsp : candidates) {
                                    try {
                                        var controller = cenv.getController(cWsp);
                                        if (controller != null) {
                                            cartago.ArtifactInfo aInfo = controller.getArtifactInfo(artName);
                                            if (aInfo != null && aInfo.getObsProperties() != null) {
                                                for (cartago.ArtifactObsProperty op : aInfo.getObsProperties()) {
                                                    if (!firstProp) sb.append(",");
                                                    firstProp = false;
                                                    sb.append("{");
                                                    sb.append("\"name\": \"").append(escapeJson(op.getName())).append("\",");
                                                    Object[] vals = op.getValues();
                                                    String valStr = (vals != null && vals.length > 0) ? String.valueOf(vals[0]) : "";
                                                    sb.append("\"value\": \"").append(escapeJson(valStr)).append("\"");
                                                    sb.append("}");
                                                }
                                                break;
                                            }
                                        }
                                    } catch (Exception ignored) {}
                                }
                            }

                            sb.append("]");
                            sb.append("}");
                        }
                    }
                    sb.append("]");
                    sb.append("}");
                }
            }
        } catch (Exception e) {
            logger.warning("Error reading Cartago state: " + e.getMessage());
        }

        sb.append("]}");
        return sb.toString();
    }

    private String handleInjectBelief(String body) {
        String agentName = getJsonField(body, "agent");
        String beliefStr = getJsonField(body, "belief");
        String action = getJsonField(body, "action");
        if (action.isEmpty()) action = "add";

        if (agentName.isEmpty() || beliefStr.isEmpty()) {
            return "{\"success\": false, \"message\": \"Missing agent or belief\"}";
        }

        try {
            if (jason.infra.local.RunLocalMAS.getRunner() != null) {
                var agArch = jason.infra.local.RunLocalMAS.getRunner().getAg(agentName);
                if (agArch != null && agArch.getTS() != null && agArch.getTS().getAg() != null) {
                    jason.asSemantics.Agent ag = agArch.getTS().getAg();
                    jason.asSyntax.Literal lit = jason.asSyntax.ASSyntax.parseLiteral(beliefStr);

                    if ("remove".equalsIgnoreCase(action)) {
                        jason.asSyntax.Literal matched = ag.getBB().contains(lit);
                        if (matched != null) {
                            ag.getBB().remove(matched);
                        } else {
                            ag.delBel(lit);
                        }
                        logger.info("❌ [Web Simulation] Removed belief: " + beliefStr + " from agent: " + agentName);
                    } else {

                        if (lit.getArity() > 0) {
                            try {
                                jason.asSyntax.Literal pattern = jason.asSyntax.ASSyntax.createLiteral(lit.getFunctor(), new jason.asSyntax.VarTerm("_"));
                                ag.abolish(pattern, new jason.asSemantics.Unifier());
                            } catch (Exception ignored) {}
                        }
                        ag.addBel(lit);
                        logger.info("🎯 [Web Simulation] Injected belief: " + beliefStr + " into agent: " + agentName);
                    }

                    if (agArch.getTS().getUserAgArch() != null) {
                        agArch.getTS().getUserAgArch().wake();
                    }

                    return "{\"success\": true, \"message\": \"Belief updated successfully\", \"agent\": \"" + escapeJson(agentName) + "\", \"belief\": \"" + escapeJson(beliefStr) + "\"}";
                } else {
                    return "{\"success\": false, \"message\": \"Agent " + escapeJson(agentName) + " not found or not active\"}";
                }
            }
        } catch (Exception e) {
            logger.warning("Error injecting belief: " + e.getMessage());
            return "{\"success\": false, \"message\": \"" + escapeJson(e.getMessage()) + "\"}";
        }

        return "{\"success\": false, \"message\": \"Runner not initialized\"}";
    }

    private String handleArtifactOp(String body) {
        String wspName = getJsonField(body, "workspace");
        String artName = getJsonField(body, "artifact");
        String opName = getJsonField(body, "operation");

        try {
            cartago.CartagoEnvironment cenv = cartago.CartagoEnvironment.getInstance();
            if (cenv != null) {
                String[] candidates = { wspName, "/main/" + wspName, "/main/" + wspName.replace("/main/", "") };
                for (String cWsp : candidates) {
                    try {
                        var controller = cenv.getController(cWsp);
                        if (controller != null) {
                            cartago.WorkspaceDescriptor wd = cenv.resolveWSP(cWsp);
                            if (wd != null && wd.getWorkspace() != null) {
                                cartago.Workspace w = wd.getWorkspace();
                                cartago.ICartagoContext ctx = w.joinWorkspace(new cartago.AgentIdCredential("web_sim_agent"), new cartago.ICartagoCallback() {
                                    public void notifyCartagoEvent(cartago.CartagoEvent arg0) {}
                                });
                                if (opName != null && !opName.isEmpty()) {
                                    ctx.doAction(1, new cartago.Op(opName), null, -1);
                                    logger.info("⚙️ [Web Simulation] Executed op " + opName + " on artifact " + artName + " in " + cWsp);
                                    return "{\"success\": true, \"message\": \"Operation " + escapeJson(opName) + " executed on " + escapeJson(artName) + "\"}";
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            logger.warning("Error executing artifact op: " + e.getMessage());
            return "{\"success\": false, \"message\": \"" + escapeJson(e.getMessage()) + "\"}";
        }

        return "{\"success\": true, \"message\": \"Operation processed\"}";
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

        sb.append("], \"allFiles\": [");

        try {
            File agtDir = new File("src/agt");
            if (agtDir.exists() && agtDir.isDirectory()) {
                java.util.List<File> aslFiles = new java.util.ArrayList<>();
                collectAslFiles(agtDir, aslFiles);
                boolean firstF = true;
                for (File f : aslFiles) {
                    try {
                        String code = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                        if (!firstF) sb.append(",");
                        firstF = false;
                        sb.append("{");
                        sb.append("\"filename\": \"").append(escapeJson(f.getName())).append("\",");
                        sb.append("\"path\": \"").append(escapeJson(f.getPath().replace("\\", "/"))).append("\",");
                        sb.append("\"code\": \"").append(escapeJson(code)).append("\"");
                        sb.append("}");
                    } catch (Exception ignored) {}
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
