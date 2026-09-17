package server.routing;

import server.http.HttpRequest;
import server.http.HttpResponse;

@FunctionalInterface
public interface RequestHandler {
    HttpResponse handle(HttpRequest request) throws Exception;
}
