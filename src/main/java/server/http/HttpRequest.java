package server.http;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public class HttpRequest {
    private final HttpMethod method;
    private final String path;
    private final String httpVersion;
    private final Map<String, String> headers;
    private final byte[] body;

    public HttpRequest(HttpMethod method, String path, String httpVersion, Map<String, String> headers, byte[] body) {
        this.method = method;
        this.path = path;
        this.httpVersion = httpVersion;
        Map<String, String> caseInsensitiveHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            caseInsensitiveHeaders.putAll(headers);
        }
        this.headers = Collections.unmodifiableMap(caseInsensitiveHeaders);
        this.body = body != null ? body : new byte[0];
    }

    public HttpMethod getMethod() { return method; }
    public String getPath() { return path; }
    public String getHttpVersion() { return httpVersion; }
    public Map<String, String> getHeaders() { return headers; }
    public String getHeader(String headerName) { return headers.get(headerName); }
    public byte[] getBody() { return body; }
    public String getBodyAsString() { return new String(body, StandardCharsets.UTF_8); }
}
