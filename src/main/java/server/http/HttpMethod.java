package server.http;

public enum HttpMethod {
    GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH;

    public static HttpMethod fromString(String method) {
        if (method == null || method.trim().isEmpty()) {
            throw new IllegalArgumentException("HTTP method cannot be null or empty");
        }
        return HttpMethod.valueOf(method.trim().toUpperCase());
    }
}
