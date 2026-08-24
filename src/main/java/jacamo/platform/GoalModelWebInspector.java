package jacamo.platform;

import java.io.*;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import jason.mas2j.AgentParameters;
import jacamo.infra.JaCaMoAgArch;
import jacamo.infra.JaCaMoLauncher;
import jacamo.project.JaCaMoProject;
import jacamo.project.JaCaMoAgentParameters;
import jacamo.project.JaCaMoFailureModelParameters;
import jacamo.project.JaCaMoGroupParameters;
import jacamo.project.JaCaMoOrgParameters;
import jacamo.project.JaCaMoSchemeParameters;

public class GoalModelWebInspector extends DefaultPlatformImpl {

    private static GoalModelWebInspector singleton = null;
    public static GoalModelWebInspector get() { return singleton; }

    private static final Logger logger = Logger.getLogger(GoalModelWebInspector.class.getName());
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private HttpServer httpServer = null;
    private int httpServerPort = 3274;
    private String httpServerURL = "http://localhost:" + httpServerPort;
    private boolean webOn = true;

    private final List<Map<String, String>> failureEvents = new CopyOnWriteArrayList<>();

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
                if (path == null || path.isEmpty()) {
                    path = "/";
                }

                if (path.startsWith("/api/")) {
                    handleApiRequest(exchange, path);
                    return;
                }

                boolean isSpaRoute = path.equals("/") || path.equals("/index.html") || (!path.contains(".") && !path.startsWith("/api/"));

                if (isSpaRoute) {
                    byte[] fileBytes = loadResourceOrFile("index.html");
                    if (fileBytes != null) {
                        Headers headers = exchange.getResponseHeaders();
                        headers.set("Content-Type", "text/html; charset=utf-8");
                        headers.set("Access-Control-Allow-Origin", "*");
                        exchange.sendResponseHeaders(200, fileBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(fileBytes);
                        os.close();
                        return;
                    }
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

        if (apiPath.equals("/api/simulation/failures")) {
            String json = buildFailuresJson();
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
            return;
        }

        if (apiPath.equals("/api/simulation/failure-events")) {
            String json = buildFailureEventsJson();
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
            return;
        }

        if (apiPath.equals("/api/simulation/inject-failure") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = readRequestBody(exchange);
            String responseJson = handleInjectFailure(body);
            byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
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

        if (apiPath.equals("/api/simulation/artifact-property") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = readRequestBody(exchange);
            String responseJson = handleArtifactProperty(body);
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

                            // Agent MoISE roles
                            sb.append("\"roles\": [");
                            boolean firstRole = true;
                            if (this.project != null && this.project.getAgents() != null) {
                                for (AgentParameters ap : this.project.getAgents()) {
                                    if (ap.getAgName().equals(agName) && ap instanceof JaCaMoAgentParameters) {
                                        JaCaMoAgentParameters jap = (JaCaMoAgentParameters) ap;
                                        if (jap.getRoles() != null) {
                                            for (String[] r : jap.getRoles()) {
                                                if (r != null && r.length >= 3) {
                                                    if (!firstRole) sb.append(",");
                                                    firstRole = false;
                                                    sb.append("{");
                                                    sb.append("\"org\": \"").append(escapeJson(r[0])).append("\",");
                                                    sb.append("\"group\": \"").append(escapeJson(r[1])).append("\",");
                                                    sb.append("\"role\": \"").append(escapeJson(r[2])).append("\"");
                                                    sb.append("}");
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            sb.append("],");

                            // Agent Goals & Intentions
                            sb.append("\"currentGoal\": ");
                            String currentGoalStr = null;
                            jason.asSemantics.Circumstance C = agArch.getTS().getC();
                            if (C != null) {
                                jason.asSemantics.Intention selInt = C.getSelectedIntention();
                                if (selInt != null && !selInt.isFinished() && selInt.size() > 0) {
                                    jason.asSemantics.IntendedMeans topIm = selInt.peek();
                                    if (topIm != null && topIm.getTrigger() != null) {
                                        currentGoalStr = topIm.getTrigger().toString();
                                    }
                                }
                                if (currentGoalStr == null && C.getAllIntentions() != null) {
                                    java.util.Iterator<jason.asSemantics.Intention> it = C.getAllIntentions();
                                    while (it.hasNext()) {
                                        jason.asSemantics.Intention in = it.next();
                                        if (in != null && !in.isFinished() && in.size() > 0) {
                                            jason.asSemantics.IntendedMeans topIm = in.peek();
                                            if (topIm != null && topIm.getTrigger() != null) {
                                                currentGoalStr = topIm.getTrigger().toString();
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                            if (currentGoalStr != null) {
                                sb.append("\"").append(escapeJson(currentGoalStr)).append("\",");
                            } else {
                                sb.append("null,");
                            }

                            // Intentions list
                            sb.append("\"intentions\": [");
                            boolean firstInt = true;
                            if (C != null && C.getAllIntentions() != null) {
                                java.util.Iterator<jason.asSemantics.Intention> it = C.getAllIntentions();
                                while (it.hasNext()) {
                                    jason.asSemantics.Intention in = it.next();
                                    if (in != null && !in.isFinished() && in.size() > 0) {
                                        if (!firstInt) sb.append(",");
                                        firstInt = false;
                                        sb.append("{");
                                        sb.append("\"id\": ").append(in.getId()).append(",");
                                        sb.append("\"isSuspended\": ").append(in.isSuspended()).append(",");
                                        
                                        String rootGoal = "";
                                        String curSubGoal = "";
                                        jason.asSemantics.IntendedMeans topIm = in.peek();
                                        if (topIm != null && topIm.getTrigger() != null) {
                                            curSubGoal = topIm.getTrigger().toString();
                                        }

                                        sb.append("\"currentGoal\": \"").append(escapeJson(curSubGoal)).append("\",");
                                        sb.append("\"stack\": [");
                                        boolean firstStack = true;
                                        for (jason.asSemantics.IntendedMeans im : in) {
                                            if (im != null && im.getTrigger() != null) {
                                                if (rootGoal.isEmpty()) {
                                                    rootGoal = im.getTrigger().toString();
                                                }
                                                if (!firstStack) sb.append(",");
                                                firstStack = false;
                                                sb.append("\"").append(escapeJson(im.getTrigger().toString())).append("\"");
                                            }
                                        }
                                        sb.append("],");
                                        sb.append("\"rootGoal\": \"").append(escapeJson(rootGoal)).append("\"");
                                        sb.append("}");
                                    }
                                }
                            }
                            sb.append("],");

                            // Events list (Pending triggers / goals)
                            sb.append("\"events\": [");
                            boolean firstEvt = true;
                            if (C != null && C.getEvents() != null) {
                                for (jason.asSemantics.Event evt : C.getEvents()) {
                                    if (evt != null && evt.getTrigger() != null) {
                                        if (!firstEvt) sb.append(",");
                                        firstEvt = false;
                                        sb.append("\"").append(escapeJson(evt.getTrigger().toString())).append("\"");
                                    }
                                }
                            }
                            sb.append("],");

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
                                                    String valStr = "";
                                                    sb.append("\"values\": [");
                                                    if (vals != null) {
                                                        for (int vi = 0; vi < vals.length; vi++) {
                                                            if (vi > 0) sb.append(",");
                                                            sb.append("\"").append(escapeJson(String.valueOf(vals[vi]))).append("\"");
                                                        }
                                                        if (vals.length == 1) {
                                                            valStr = String.valueOf(vals[0]);
                                                        } else if (vals.length > 1) {
                                                            StringBuilder vsb = new StringBuilder();
                                                            for (int vi = 0; vi < vals.length; vi++) {
                                                                if (vi > 0) vsb.append(", ");
                                                                vsb.append(String.valueOf(vals[vi]));
                                                            }
                                                            valStr = vsb.toString();
                                                        }
                                                    }
                                                    sb.append("],");
                                                    sb.append("\"value\": \"").append(escapeJson(valStr)).append("\",");
                                                    sb.append("\"arity\": ").append(vals != null ? vals.length : 0);
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

        sb.append("], \"organisations\": [");

        boolean firstOrg = true;
        try {
            if (this.project != null && this.project.getOrgs() != null) {
                for (jacamo.project.JaCaMoOrgParameters org : this.project.getOrgs()) {
                    if (!firstOrg) sb.append(",");
                    firstOrg = false;
                    sb.append("{");
                    sb.append("\"name\": \"").append(escapeJson(org.getName())).append("\",");
                    String src = org.getParameter("source");
                    if (src == null) src = org.getParameter("file");
                    if (src == null) src = "";
                    sb.append("\"source\": \"").append(escapeJson(src)).append("\",");

                    // Groups
                    sb.append("\"groups\": [");
                    boolean firstGrp = true;
                    if (org.getGroups() != null) {
                        for (jacamo.project.JaCaMoGroupParameters grp : org.getGroups()) {
                            if (!firstGrp) sb.append(",");
                            firstGrp = false;
                            sb.append("{");
                            sb.append("\"name\": \"").append(escapeJson(grp.getName())).append("\",");
                            sb.append("\"type\": \"").append(escapeJson(grp.getType() != null ? grp.getType() : "")).append("\",");
                            sb.append("\"responsibleFor\": [");
                            if (grp.getResponsibleFor() != null) {
                                for (int i = 0; i < grp.getResponsibleFor().size(); i++) {
                                    if (i > 0) sb.append(",");
                                    sb.append("\"").append(escapeJson(grp.getResponsibleFor().get(i))).append("\"");
                                }
                            }
                            sb.append("],");
                            sb.append("\"players\": [");
                            boolean firstPl = true;
                            if (this.project.getAgents() != null) {
                                for (AgentParameters ap : this.project.getAgents()) {
                                    if (ap instanceof JaCaMoAgentParameters) {
                                        JaCaMoAgentParameters jap = (JaCaMoAgentParameters) ap;
                                        if (jap.getRoles() != null) {
                                            for (String[] r : jap.getRoles()) {
                                                if (r != null && r.length >= 3 && grp.getName().equals(r[1])) {
                                                    if (!firstPl) sb.append(",");
                                                    firstPl = false;
                                                    sb.append("{");
                                                    sb.append("\"agent\": \"").append(escapeJson(ap.getAgName())).append("\",");
                                                    sb.append("\"role\": \"").append(escapeJson(r[2])).append("\"");
                                                    sb.append("}");
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            sb.append("]");
                            sb.append("}");
                        }
                    }
                    sb.append("],");

                    // Schemes
                    sb.append("\"schemes\": [");
                    boolean firstSch = true;
                    if (org.getSchemes() != null) {
                        for (jacamo.project.JaCaMoSchemeParameters sch : org.getSchemes()) {
                            if (!firstSch) sb.append(",");
                            firstSch = false;
                            sb.append("{");
                            sb.append("\"name\": \"").append(escapeJson(sch.getName())).append("\",");
                            sb.append("\"type\": \"").append(escapeJson(sch.getType() != null ? sch.getType() : "")).append("\"");
                            sb.append("}");
                        }
                    }
                    sb.append("]");
                    sb.append("}");
                }
            }
        } catch (Exception e) {
            logger.warning("Error reading Organisations state: " + e.getMessage());
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
                    if (beliefStr.startsWith("!") || beliefStr.startsWith("+!")) {
                        String trigStr = beliefStr.startsWith("+") ? beliefStr : "+" + beliefStr;
                        jason.asSyntax.Trigger tr = jason.asSyntax.ASSyntax.parseTrigger(trigStr);
                        agArch.getTS().getC().addEvent(new jason.asSemantics.Event(tr, jason.asSemantics.Intention.EmptyInt));
                        if (agArch.getTS().getUserAgArch() != null) {
                            agArch.getTS().getUserAgArch().wake();
                        }
                        logger.info("🎯 [Web Simulation] Triggered Goal: " + trigStr + " for agent: " + agentName);
                        return "{\"success\": true, \"message\": \"Goal triggered successfully\", \"agent\": \"" + escapeJson(agentName) + "\", \"goal\": \"" + escapeJson(trigStr) + "\"}";
                    }

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

    private List<String> getJsonArrayField(String json, String fieldName) {
        List<String> list = new ArrayList<>();
        if (json == null) return list;
        String pattern = "\"" + fieldName + "\"\\s*:\\s*\\[(.*?)\\]";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.DOTALL).matcher(json);
        if (m.find()) {
            String content = m.group(1);
            java.util.regex.Matcher itemM = java.util.regex.Pattern.compile("\"([^\"]*)\"|'([^']*)'|([^,\\s\\]]+)").matcher(content);
            while (itemM.find()) {
                if (itemM.group(1) != null) list.add(itemM.group(1));
                else if (itemM.group(2) != null) list.add(itemM.group(2));
                else if (itemM.group(3) != null) list.add(itemM.group(3));
            }
        }
        return list;
    }

    private String[] splitTopLevelCommas(String text) {
        if (text == null) return new String[0];
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;
        int parenDepth = 0;
        int bracketDepth = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                current.append(c);
                if (c == quoteChar && (i == 0 || text.charAt(i - 1) != '\\')) {
                    inQuotes = false;
                }
            } else {
                if (c == '"' || c == '\'') {
                    inQuotes = true;
                    quoteChar = c;
                    current.append(c);
                } else if (c == '(') {
                    parenDepth++;
                    current.append(c);
                } else if (c == ')') {
                    parenDepth--;
                    current.append(c);
                } else if (c == '[') {
                    bracketDepth++;
                    current.append(c);
                } else if (c == ']') {
                    bracketDepth--;
                    current.append(c);
                } else if (c == ',' && parenDepth == 0 && bracketDepth == 0) {
                    result.add(current.toString().trim());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        if (current.length() > 0) {
            result.add(current.toString().trim());
        }
        return result.toArray(new String[0]);
    }

    private String handleArtifactProperty(String body) {
        String wspName = getJsonField(body, "workspace");
        String artName = getJsonField(body, "artifact");
        String propName = getJsonField(body, "property");
        String propVal = getJsonField(body, "value");
        String action = getJsonField(body, "action"); // "define", "update", "remove"
        if (action.isEmpty()) action = "define";

        if (artName.isEmpty() || propName.isEmpty()) {
            return "{\"success\": false, \"message\": \"Missing artifact name or property name\"}";
        }

        try {
            // Parse multiple values
            List<Object> parsedList = new ArrayList<>();
            List<String> valuesArr = getJsonArrayField(body, "values");
            if (!valuesArr.isEmpty()) {
                for (String v : valuesArr) {
                    if (v != null && !v.trim().isEmpty()) {
                        parsedList.add(parsePropertyValue(v));
                    }
                }
            } else if (propVal != null && !propVal.trim().isEmpty()) {
                String[] parts = splitTopLevelCommas(propVal);
                for (String p : parts) {
                    if (!p.trim().isEmpty()) {
                        parsedList.add(parsePropertyValue(p));
                    }
                }
            }
            Object[] parsedValues = parsedList.toArray(new Object[0]);

            // 1. Update Artifact in Cartago
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
                                updateCartagoArtifactProperty(w, artName, propName, parsedValues, action);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            // 2. Also propagate belief into Jason agents focusing on this workspace/artifact
            String beliefStr;
            if (parsedValues != null && parsedValues.length > 0) {
                StringBuilder bsb = new StringBuilder(propName).append("(");
                for (int i = 0; i < parsedValues.length; i++) {
                    if (i > 0) bsb.append(", ");
                    Object pv = parsedValues[i];
                    if (pv instanceof String) {
                        String strPv = (String) pv;
                        if (strPv.matches("^[a-z][a-zA-Z0-9_]*$") || strPv.matches("^-?\\d+(\\.\\d+)?$")) {
                            bsb.append(strPv);
                        } else {
                            bsb.append("\"").append(strPv.replace("\"", "\\\"")).append("\"");
                        }
                    } else {
                        bsb.append(pv);
                    }
                }
                bsb.append(")");
                beliefStr = bsb.toString();
            } else {
                beliefStr = propName;
            }

            JaCaMoProject currentProject = this.project != null ? this.project : (JaCaMoLauncher.getJaCaMoRunner() != null ? JaCaMoLauncher.getJaCaMoRunner().getJaCaMoProject() : null);

            if (jason.infra.local.RunLocalMAS.getRunner() != null) {
                var agsMap = jason.infra.local.RunLocalMAS.getRunner().getAgs();
                if (agsMap != null) {
                    for (String agName : agsMap.keySet()) {
                        boolean shouldPropagate = false;
                        if (currentProject != null && currentProject.getAgents() != null) {
                            for (AgentParameters ap : currentProject.getAgents()) {
                                if (ap.getAgName().equals(agName) && ap instanceof JaCaMoAgentParameters) {
                                    JaCaMoAgentParameters jap = (JaCaMoAgentParameters) ap;
                                    if (jap.getFocus() != null) {
                                        for (String[] foc : jap.getFocus()) {
                                            if (foc != null && foc.length > 0 && artName.equals(foc[0])) {
                                                shouldPropagate = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (!shouldPropagate) continue;

                        var agArch = jason.infra.local.RunLocalMAS.getRunner().getAg(agName);
                        if (agArch != null && agArch.getTS() != null && agArch.getTS().getAg() != null) {
                            jason.asSemantics.Agent ag = agArch.getTS().getAg();
                            try {
                                jason.asSyntax.Literal lit = jason.asSyntax.ASSyntax.parseLiteral(beliefStr);
                                if ("remove".equalsIgnoreCase(action)) {
                                    jason.asSyntax.Literal matched = ag.getBB().contains(lit);
                                    if (matched != null) ag.getBB().remove(matched);
                                    else ag.delBel(lit);
                                } else {
                                    if (lit.getArity() > 0) {
                                        try {
                                            jason.asSyntax.Literal pattern = jason.asSyntax.ASSyntax.createLiteral(lit.getFunctor(), new jason.asSyntax.VarTerm("_"));
                                            ag.abolish(pattern, new jason.asSemantics.Unifier());
                                        } catch (Exception ignored) {}
                                    }
                                    ag.addBel(lit);
                                }
                                if (agArch.getTS().getUserAgArch() != null) {
                                    agArch.getTS().getUserAgArch().wake();
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }

            logger.info("📦 [Artifact Belief] " + action + " property '" + propName + "' on artifact '" + artName + "' (Values: " + java.util.Arrays.toString(parsedValues) + ")");
            return "{\"success\": true, \"message\": \"Artifact property/belief updated: " + escapeJson(beliefStr) + "\", \"property\": \"" + escapeJson(propName) + "\", \"belief\": \"" + escapeJson(beliefStr) + "\"}";
        } catch (Exception e) {
            logger.warning("Error updating artifact property: " + e.getMessage());
            return "{\"success\": false, \"message\": \"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    private Object parsePropertyValue(String val) {
        if (val == null || val.trim().isEmpty()) return "";
        String s = val.trim();
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            if (s.length() >= 2) s = s.substring(1, s.length() - 1);
            return s;
        }
        if ("true".equalsIgnoreCase(s)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(s)) return Boolean.FALSE;
        try {
            if (s.matches("-?\\d+")) {
                return Long.parseLong(s);
            }
            if (s.matches("-?\\d+\\.\\d+")) {
                return Double.parseDouble(s);
            }
        } catch (Exception ignored) {}
        return s;
    }

    private cartago.Artifact extractArtifact(Object obj) {
        if (obj == null) return null;
        if (obj instanceof cartago.Artifact) return (cartago.Artifact) obj;
        try {
            for (java.lang.reflect.Method m : obj.getClass().getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && cartago.Artifact.class.isAssignableFrom(m.getReturnType())) {
                    m.setAccessible(true);
                    Object res = m.invoke(obj);
                    if (res instanceof cartago.Artifact) return (cartago.Artifact) res;
                }
            }
            for (java.lang.reflect.Field f : obj.getClass().getDeclaredFields()) {
                if (cartago.Artifact.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object res = f.get(obj);
                    if (res instanceof cartago.Artifact) return (cartago.Artifact) res;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void updateCartagoArtifactProperty(cartago.Workspace w, String artName, String propName, Object[] parsedValues, String action) {
        try {
            cartago.ArtifactId aid = w.getArtifact(artName);
            if (aid == null) return;

            cartago.Artifact artObj = null;
            // Search fields of Workspace to find Artifact
            for (java.lang.reflect.Field f : w.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object fVal = f.get(w);
                if (fVal instanceof java.util.Map) {
                    java.util.Map<?, ?> map = (java.util.Map<?, ?>) fVal;
                    for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                        cartago.Artifact candidate = extractArtifact(entry.getValue());
                        if (candidate != null) {
                            String name = "";
                            try {
                                java.lang.reflect.Method mGetId = cartago.Artifact.class.getDeclaredMethod("getId");
                                mGetId.setAccessible(true);
                                Object aidObj = mGetId.invoke(candidate);
                                if (aidObj instanceof cartago.ArtifactId) {
                                    name = ((cartago.ArtifactId) aidObj).getName();
                                }
                            } catch (Exception ignored) {}
                            if (artName.equals(name) || aid.getName().equals(name) || artName.equals(String.valueOf(entry.getKey()))) {
                                artObj = candidate;
                                break;
                            }
                        }
                    }
                } else if (fVal != null && fVal.getClass().getName().contains("ArtifactRegistry")) {
                    for (java.lang.reflect.Field rf : fVal.getClass().getDeclaredFields()) {
                        rf.setAccessible(true);
                        Object rVal = rf.get(fVal);
                        if (rVal instanceof java.util.Map) {
                            java.util.Map<?, ?> map = (java.util.Map<?, ?>) rVal;
                            for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                                cartago.Artifact candidate = extractArtifact(entry.getValue());
                                if (candidate != null) {
                                    String name = "";
                                    try {
                                        java.lang.reflect.Method mGetId = cartago.Artifact.class.getDeclaredMethod("getId");
                                        mGetId.setAccessible(true);
                                        Object aidObj = mGetId.invoke(candidate);
                                        if (aidObj instanceof cartago.ArtifactId) {
                                            name = ((cartago.ArtifactId) aidObj).getName();
                                        }
                                    } catch (Exception ignored) {}
                                    if (artName.equals(name) || aid.getName().equals(name) || artName.equals(String.valueOf(entry.getKey()))) {
                                        artObj = candidate;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                if (artObj != null) break;
            }

            if (artObj != null) {
                synchronized (artObj) {
                    java.lang.reflect.Method mGetProp = cartago.Artifact.class.getDeclaredMethod("getObsProperty", String.class);
                    mGetProp.setAccessible(true);
                    Object op = mGetProp.invoke(artObj, propName);

                    if ("remove".equalsIgnoreCase(action)) {
                        java.lang.reflect.Method mRem = cartago.Artifact.class.getDeclaredMethod("removeObsProperty", String.class);
                        mRem.setAccessible(true);
                        mRem.invoke(artObj, propName);
                    } else {
                        if (op != null) {
                            if (parsedValues != null && parsedValues.length > 0) {
                                try {
                                    java.lang.reflect.Method mUpd = cartago.ObsProperty.class.getDeclaredMethod("updateValues", Object[].class);
                                    mUpd.setAccessible(true);
                                    mUpd.invoke(op, new Object[]{ parsedValues });
                                } catch (Exception ex) {
                                    ((cartago.ObsProperty) op).updateValue(parsedValues[0]);
                                }
                            } else {
                                ((cartago.ObsProperty) op).updateValue("");
                            }
                        } else {
                            Object[] valuesToDefine = (parsedValues != null && parsedValues.length > 0) ? parsedValues : new Object[]{ "" };
                            java.lang.reflect.Method mDef = cartago.Artifact.class.getDeclaredMethod("defineObsProperty", String.class, Object[].class);
                            mDef.setAccessible(true);
                            mDef.invoke(artObj, new Object[]{ propName, valuesToDefine });
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error updating artifact property in workspace: " + e.getMessage());
        }
    }

    private String buildFailuresJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"agentFailures\": [");

        boolean firstAgFailure = true;
        try {
            if (this.project != null && this.project.getFailureModel() != null) {
                JaCaMoFailureModelParameters fm = this.project.getFailureModel();
                for (JaCaMoFailureModelParameters.FailureParameters f : fm.getFailures()) {
                    // Find all agents that use this failure
                    List<String> targetAgents = new ArrayList<>();
                    if (this.project.getAgents() != null) {
                        for (AgentParameters ap : this.project.getAgents()) {
                            if (ap instanceof JaCaMoAgentParameters) {
                                JaCaMoAgentParameters jap = (JaCaMoAgentParameters) ap;
                                if (jap.getFailures() != null && jap.getFailures().contains(f.getGoalId())) {
                                    targetAgents.add(jap.getAgName());
                                }
                            }
                        }
                    }
                    if (targetAgents.isEmpty() && this.project.getAgents() != null) {
                        for (AgentParameters ap : this.project.getAgents()) {
                            targetAgents.add(ap.getAgName());
                        }
                    }

                    if (!firstAgFailure) sb.append(",");
                    firstAgFailure = false;
                    sb.append("{");
                    sb.append("\"goalId\": \"").append(escapeJson(f.getGoalId())).append("\",");
                    sb.append("\"targetAgents\": [");
                    for (int i = 0; i < targetAgents.size(); i++) {
                        if (i > 0) sb.append(",");
                        sb.append("\"").append(escapeJson(targetAgents.get(i))).append("\"");
                    }
                    sb.append("],");
                    sb.append("\"errors\": [");

                    boolean firstErr = true;
                    for (JaCaMoFailureModelParameters.ErrorParameters ep : f.getErrors()) {
                        if (!firstErr) sb.append(",");
                        firstErr = false;
                        sb.append("{");
                        sb.append("\"errorName\": \"").append(escapeJson(ep.getErrorName())).append("\",");
                        sb.append("\"conditions\": [");
                        for (int c = 0; c < ep.getConditions().size(); c++) {
                            if (c > 0) sb.append(",");
                            sb.append("\"").append(escapeJson(ep.getConditions().get(c))).append("\"");
                        }
                        sb.append("],");
                        sb.append("\"recoveryActivities\": [");
                        for (int r = 0; r < ep.getRecoveryActivities().size(); r++) {
                            if (r > 0) sb.append(",");
                            String rec = ep.getRecoveryActivities().get(r);
                            String type = "unknown";
                            if (rec.startsWith("goal:")) type = "goal";
                            else if (rec.startsWith("env:")) type = "env";
                            else if (rec.startsWith("org:")) type = "org";
                            else if (rec.startsWith("plan:")) type = "plan";

                            sb.append("{");
                            sb.append("\"raw\": \"").append(escapeJson(rec)).append("\",");
                            sb.append("\"type\": \"").append(type).append("\",");
                            sb.append("\"action\": \"").append(escapeJson(rec.contains(":") ? rec.substring(rec.indexOf(":") + 1) : rec)).append("\"");
                            sb.append("}");
                        }
                        sb.append("]");
                        sb.append("}");
                    }
                    sb.append("]");
                    sb.append("}");
                }
            }
        } catch (Exception e) {
            logger.warning("Error reading agent failure models: " + e.getMessage());
        }

        sb.append("], \"orgFailures\": [");

        boolean firstOrgFailure = true;
        try {
            if (this.project != null && this.project.getFailureModel() != null && this.project.getOrgs() != null) {
                JaCaMoFailureModelParameters fm = this.project.getFailureModel();
                Map<String, JaCaMoFailureModelParameters.FailureParameters> fMap = new HashMap<>();
                for (JaCaMoFailureModelParameters.FailureParameters f : fm.getFailures()) {
                    fMap.put(f.getGoalId(), f);
                }

                for (JaCaMoOrgParameters org : this.project.getOrgs()) {
                    // Group failures
                    if (org.getGroups() != null) {
                        for (JaCaMoGroupParameters grp : org.getGroups()) {
                            if (grp.getFailures() != null) {
                                for (String fId : grp.getFailures()) {
                                    JaCaMoFailureModelParameters.FailureParameters f = fMap.get(fId);
                                    if (f != null) {
                                        if (!firstOrgFailure) sb.append(",");
                                        firstOrgFailure = false;
                                        sb.append(buildOrgFailureJsonItem("group", grp.getName(), org.getName(), f));
                                    }
                                }
                            }
                        }
                    }

                    // Scheme failures
                    if (org.getSchemes() != null) {
                        for (JaCaMoSchemeParameters sch : org.getSchemes()) {
                            if (sch.getFailures() != null) {
                                for (String fId : sch.getFailures()) {
                                    JaCaMoFailureModelParameters.FailureParameters f = fMap.get(fId);
                                    if (f != null) {
                                        if (!firstOrgFailure) sb.append(",");
                                        firstOrgFailure = false;
                                        sb.append(buildOrgFailureJsonItem("scheme", sch.getName(), org.getName(), f));
                                    }
                                }
                            }
                        }
                    }

                    // Org-level failures
                    if (org.getFailures() != null) {
                        for (String fId : org.getFailures()) {
                            JaCaMoFailureModelParameters.FailureParameters f = fMap.get(fId);
                            if (f != null) {
                                if (!firstOrgFailure) sb.append(",");
                                firstOrgFailure = false;
                                sb.append(buildOrgFailureJsonItem("org", org.getName(), org.getName(), f));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error reading org failure models: " + e.getMessage());
        }

        sb.append("], \"activeAgents\": [");

        boolean firstActAg = true;
        try {
            if (jason.infra.local.RunLocalMAS.getRunner() != null) {
                var agsMap = jason.infra.local.RunLocalMAS.getRunner().getAgs();
                if (agsMap != null) {
                    for (String agName : agsMap.keySet()) {
                        var agArch = jason.infra.local.RunLocalMAS.getRunner().getAg(agName);
                        if (!firstActAg) sb.append(",");
                        firstActAg = false;
                        sb.append("{");
                        sb.append("\"name\": \"").append(escapeJson(agName)).append("\",");
                        var jcmArch = getJaCaMoAgArch(agArch);
                        boolean hasMgr = jcmArch != null && jcmArch.getFailureManager() != null;
                        int count = 0;
                        if (hasMgr) {
                            var mgr = jcmArch.getFailureManager();
                            count = mgr.getFailures() != null ? mgr.getFailures().size() : 0;
                        }
                        sb.append("\"hasFailureManager\": ").append(hasMgr).append(",");
                        sb.append("\"monitoredFailuresCount\": ").append(count);
                        sb.append("}");
                    }
                }
            }
        } catch (Exception ignored) {}

        sb.append("]}");
        return sb.toString();
    }

    private String buildOrgFailureJsonItem(String scopeType, String scopeName, String orgName, JaCaMoFailureModelParameters.FailureParameters f) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"scopeType\": \"").append(scopeType).append("\",");
        sb.append("\"scopeName\": \"").append(escapeJson(scopeName)).append("\",");
        sb.append("\"orgName\": \"").append(escapeJson(orgName)).append("\",");
        sb.append("\"failureName\": \"").append(escapeJson(f.getGoalId())).append("\",");
        sb.append("\"errors\": [");

        boolean firstErr = true;
        for (JaCaMoFailureModelParameters.ErrorParameters ep : f.getErrors()) {
            if (!firstErr) sb.append(",");
            firstErr = false;
            sb.append("{");
            sb.append("\"errorName\": \"").append(escapeJson(ep.getErrorName())).append("\",");
            sb.append("\"conditions\": [");
            for (int c = 0; c < ep.getConditions().size(); c++) {
                if (c > 0) sb.append(",");
                sb.append("\"").append(escapeJson(ep.getConditions().get(c))).append("\"");
            }
            sb.append("],");
            sb.append("\"recoveryActivities\": [");
            for (int r = 0; r < ep.getRecoveryActivities().size(); r++) {
                if (r > 0) sb.append(",");
                String rec = ep.getRecoveryActivities().get(r);
                String type = "unknown";
                if (rec.startsWith("goal:")) type = "goal";
                else if (rec.startsWith("env:")) type = "env";
                else if (rec.startsWith("org:")) type = "org";
                else if (rec.startsWith("plan:")) type = "plan";

                sb.append("{");
                sb.append("\"raw\": \"").append(escapeJson(rec)).append("\",");
                sb.append("\"type\": \"").append(type).append("\",");
                sb.append("\"action\": \"").append(escapeJson(rec.contains(":") ? rec.substring(rec.indexOf(":") + 1) : rec)).append("\"");
                sb.append("}");
            }
            sb.append("]");
            sb.append("}");
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private String buildFailureEventsJson() {
        StringBuilder sb = new StringBuilder("{\"events\": [");
        for (int i = failureEvents.size() - 1; i >= 0; i--) {
            Map<String, String> ev = failureEvents.get(i);
            if (i < failureEvents.size() - 1) sb.append(",");
            sb.append("{");
            sb.append("\"timestamp\": \"").append(escapeJson(ev.getOrDefault("timestamp", ""))).append("\",");
            sb.append("\"type\": \"").append(escapeJson(ev.getOrDefault("type", "agent"))).append("\",");
            sb.append("\"target\": \"").append(escapeJson(ev.getOrDefault("target", ""))).append("\",");
            sb.append("\"failure\": \"").append(escapeJson(ev.getOrDefault("failure", ""))).append("\",");
            sb.append("\"error\": \"").append(escapeJson(ev.getOrDefault("error", ""))).append("\",");
            sb.append("\"condition\": \"").append(escapeJson(ev.getOrDefault("condition", ""))).append("\",");
            sb.append("\"detail\": \"").append(escapeJson(ev.getOrDefault("detail", ""))).append("\",");
            sb.append("\"status\": \"").append(escapeJson(ev.getOrDefault("status", "TRIGGERED"))).append("\"");
            sb.append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String handleInjectFailure(String body) {
        String agentName = getJsonField(body, "agent");
        String failureName = getJsonField(body, "failure");
        String errorName = getJsonField(body, "error");
        String condition = getJsonField(body, "condition");
        String mode = getJsonField(body, "mode"); // "inject_condition" or "trigger_recovery"
        if (mode.isEmpty()) mode = "inject_condition";

        String time = LocalDateTime.now().format(TIME_FORMATTER);

        try {
            if ("inject_condition".equalsIgnoreCase(mode)) {
                if (agentName.isEmpty() || condition.isEmpty()) {
                    return "{\"success\": false, \"message\": \"Missing agent or condition string\"}";
                }

                if (jason.infra.local.RunLocalMAS.getRunner() != null) {
                    var agArch = jason.infra.local.RunLocalMAS.getRunner().getAg(agentName);
                    if (agArch != null && agArch.getTS() != null && agArch.getTS().getAg() != null) {
                        jason.asSemantics.Agent ag = agArch.getTS().getAg();
                        jason.asSyntax.Literal lit = jason.asSyntax.ASSyntax.parseLiteral(condition);

                        if (lit.getArity() > 0) {
                            try {
                                jason.asSyntax.Literal pattern = jason.asSyntax.ASSyntax.createLiteral(lit.getFunctor(), new jason.asSyntax.VarTerm("_"));
                                ag.abolish(pattern, new jason.asSemantics.Unifier());
                            } catch (Exception ignored) {}
                        }
                        ag.addBel(lit);
                        logger.info("⚠️ [Failure Simulation] Injected error condition belief: " + condition + " into agent " + agentName);

                        var jcmArch = getJaCaMoAgArch(agArch);
                        if (jcmArch != null) {
                            var mgr = jcmArch.getFailureManager();
                            if (mgr != null) {
                                mgr.monitor();
                            }
                        }

                        if (agArch.getTS().getUserAgArch() != null) {
                            agArch.getTS().getUserAgArch().wake();
                        }

                        Map<String, String> ev = new HashMap<>();
                        ev.put("timestamp", time);
                        ev.put("type", "agent");
                        ev.put("target", agentName);
                        ev.put("failure", failureName.isEmpty() ? "failure_model" : failureName);
                        ev.put("error", errorName.isEmpty() ? "error_event" : errorName);
                        ev.put("condition", condition);
                        ev.put("detail", "Injected condition belief '" + condition + "' -> Triggered Failure Monitor");
                        ev.put("status", "CONDITION_INJECTED");
                        failureEvents.add(ev);

                        return "{\"success\": true, \"message\": \"Injected condition '" + escapeJson(condition) + "' into " + escapeJson(agentName) + "\", \"timestamp\": \"" + time + "\"}";
                    }
                }
            } else if ("trigger_recovery".equalsIgnoreCase(mode)) {
                // Find recovery activities for this failure & error
                List<String> activities = new ArrayList<>();
                if (this.project != null && this.project.getFailureModel() != null) {
                    for (var f : this.project.getFailureModel().getFailures()) {
                        if (f.getGoalId().equals(failureName) || failureName.isEmpty()) {
                            for (var err : f.getErrors()) {
                                if (err.getErrorName().equals(errorName) || errorName.isEmpty()) {
                                    activities.addAll(err.getRecoveryActivities());
                                }
                            }
                        }
                    }
                }

                if (activities.isEmpty()) {
                    String rawAct = getJsonField(body, "activity");
                    if (!rawAct.isEmpty()) activities.add(rawAct);
                }

                int executedCount = 0;
                if (!agentName.isEmpty() && jason.infra.local.RunLocalMAS.getRunner() != null) {
                    var agArch = jason.infra.local.RunLocalMAS.getRunner().getAg(agentName);
                    if (agArch != null && agArch.getTS() != null && agArch.getTS().getAg() != null) {
                        jason.asSemantics.Agent ag = agArch.getTS().getAg();

                        for (String act : activities) {
                            if (act.startsWith("goal:")) {
                                String g = act.substring(5).trim();
                                try {
                                    ag.addBel(jason.asSyntax.ASSyntax.parseLiteral("recovery_triggered(" + g + ")"));
                                    agArch.getTS().getC().addAchvGoal(jason.asSyntax.ASSyntax.parseLiteral(g), jason.asSemantics.Intention.EmptyInt);
                                    executedCount++;
                                } catch (Exception ignored) {}
                            } else if (act.startsWith("env:")) {
                                String opTarget = act.substring(4).trim();
                                String[] parts = opTarget.split("\\.");
                                if (parts.length >= 2) {
                                    String wsp = parts.length >= 3 ? parts[0] : "w1";
                                    String art = parts.length >= 3 ? parts[1] : parts[0];
                                    String op = parts.length >= 3 ? parts[2] : parts[1];
                                    handleArtifactOp("{\"workspace\": \"" + wsp + "\", \"artifact\": \"" + art + "\", \"operation\": \"" + op + "\"}");
                                    executedCount++;
                                }
                            }
                        }

                        if (agArch.getTS().getUserAgArch() != null) {
                            agArch.getTS().getUserAgArch().wake();
                        }
                    }
                }

                Map<String, String> ev = new HashMap<>();
                ev.put("timestamp", time);
                ev.put("type", "recovery");
                ev.put("target", agentName);
                ev.put("failure", failureName);
                ev.put("error", errorName);
                ev.put("condition", condition);
                ev.put("detail", "Executed " + executedCount + " adaptation activities: " + String.join(", ", activities));
                ev.put("status", "RECOVERY_EXECUTED");
                failureEvents.add(ev);

                logger.info("🛡️ [Failure Simulation] Executed direct adaptation activities for: " + failureName + " -> " + errorName);
                return "{\"success\": true, \"message\": \"Executed adaptation recovery activities successfully\", \"executed\": " + executedCount + "}";
            }
        } catch (Exception e) {
            logger.warning("Error simulating failure: " + e.getMessage());
            return "{\"success\": false, \"message\": \"" + escapeJson(e.getMessage()) + "\"}";
        }

        return "{\"success\": false, \"message\": \"Invalid simulation request\"}";
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

    private JaCaMoAgArch getJaCaMoAgArch(jason.infra.local.LocalAgArch agArch) {
        if (agArch == null || agArch.getTS() == null) return null;
        jason.architecture.AgArch arch = agArch.getTS().getUserAgArch();
        while (arch != null) {
            if (arch instanceof JaCaMoAgArch) {
                return (JaCaMoAgArch) arch;
            }
            arch = arch.getNextAgArch();
        }
        return null;
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
            "../tools/asl-visual-editor/dist/" + cleanPath,
            "../../tools/asl-visual-editor/dist/" + cleanPath,
            "../../../tools/asl-visual-editor/dist/" + cleanPath,
            "src/main/resources/asl-visual-editor/" + cleanPath,
            "../src/main/resources/asl-visual-editor/" + cleanPath,
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
