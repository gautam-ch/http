package server.routing;

import server.http.HttpMethod;
import server.http.HttpRequest;
import server.http.HttpResponse;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class Router {
    private final Map<HttpMethod, Map<String, RequestHandler>> exactRoutes = new EnumMap<>(HttpMethod.class);
    private final Map<HttpMethod, List<PatternRoute>> patternRoutes = new EnumMap<>(HttpMethod.class);

    private record PatternRoute(Pattern pattern, RequestHandler handler) {}

    public Router() {
        for (HttpMethod method : HttpMethod.values()) {
            exactRoutes.put(method, new HashMap<>());
            patternRoutes.put(method, new ArrayList<>());
        }
    }

    public Router get(String path, RequestHandler handler) { return route(HttpMethod.GET, path, handler); }
    public Router post(String path, RequestHandler handler) { return route(HttpMethod.POST, path, handler); }

    public Router route(HttpMethod method, String path, RequestHandler handler) {
        if (path.contains("*") || path.contains("{")) {
            String regex = path.replaceAll("\\{[^/]+\\}", "([^/]+)").replace("*", "(.*)");
            patternRoutes.get(method).add(new PatternRoute(Pattern.compile("^" + regex + "$"), handler));
        } else {
            exactRoutes.get(method).put(path, handler);
        }
        return this;
    }

    public HttpResponse dispatch(HttpRequest request) {
        HttpMethod method = request.getMethod();
        String path = request.getPath();

        RequestHandler handler = exactRoutes.get(method).get(path);
        if (handler != null) return invokeHandler(handler, request);

        for (PatternRoute pr : patternRoutes.get(method)) {
            if (pr.pattern.matcher(path).matches()) {
                return invokeHandler(pr.handler, request);
            }
        }
        return HttpResponse.notFound("404 Not Found: " + path + "\n");
    }

    private HttpResponse invokeHandler(RequestHandler handler, HttpRequest request) {
        try {
            return handler.handle(request);
        } catch (Exception e) {
            return HttpResponse.builder().status(server.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "text/plain")
                    .body("500 Internal Server Error: " + e.getMessage() + "\n").build();
        }
    }
}
