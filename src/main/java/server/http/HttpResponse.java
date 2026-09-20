package server.http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class HttpResponse {
    private final HttpStatus status;
    private final Map<String, String> headers;
    private final byte[] body;

    private HttpResponse(Builder builder) {
        this.status = builder.status;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.headers));
        this.body = builder.body;
    }

    public HttpStatus getStatus() { return status; }
    public byte[] getBody() { return body; }

    public void writeTo(OutputStream out) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(status.toStatusLine()).append("\r\n");
        sb.append("Date: ").append(DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC))).append("\r\n");
        sb.append("Server: CoreJava-HttpServer/1.0\r\n");
        sb.append("Connection: close\r\n");
        for (Map.Entry<String, String> h : headers.entrySet()) {
            if (!h.getKey().equalsIgnoreCase("Date") && !h.getKey().equalsIgnoreCase("Server") &&
                !h.getKey().equalsIgnoreCase("Connection") && !h.getKey().equalsIgnoreCase("Content-Length")) {
                sb.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
            }
        }
        sb.append("Content-Length: ").append(body != null ? body.length : 0).append("\r\n\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.US_ASCII));
        if (body != null && body.length > 0) {
            out.write(body);
        }
        out.flush();
    }

    public static Builder builder() { return new Builder(); }
    public static HttpResponse ok(String text) {
        return builder().status(HttpStatus.OK).header("Content-Type", "text/plain; charset=UTF-8").body(text).build();
    }
    public static HttpResponse ok(byte[] data, String contentType) {
        return builder().status(HttpStatus.OK).header("Content-Type", contentType).body(data).build();
    }
    public static HttpResponse json(String json) {
        return builder().status(HttpStatus.OK).header("Content-Type", "application/json; charset=UTF-8").body(json).build();
    }
    public static HttpResponse created(String text) {
        return builder().status(HttpStatus.CREATED).header("Content-Type", "text/plain; charset=UTF-8").body(text).build();
    }
    public static HttpResponse notFound(String message) {
        return builder().status(HttpStatus.NOT_FOUND).header("Content-Type", "text/plain; charset=UTF-8").body(message).build();
    }
    public static HttpResponse badRequest(String message) {
        return builder().status(HttpStatus.BAD_REQUEST).header("Content-Type", "text/plain; charset=UTF-8").body(message).build();
    }
    public static HttpResponse methodNotAllowed(String allow) {
        return builder().status(HttpStatus.METHOD_NOT_ALLOWED).header("Allow", allow).header("Content-Type", "text/plain; charset=UTF-8").body("405 Method Not Allowed\n").build();
    }
    public static HttpResponse internalServerError(String message) {
        return builder().status(HttpStatus.INTERNAL_SERVER_ERROR).header("Content-Type", "text/plain; charset=UTF-8").body(message).build();
    }
    public static HttpResponse serviceUnavailable(String message) {
        return builder().status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "5").header("Content-Type", "text/plain; charset=UTF-8").body(message).build();
    }

    public static class Builder {
        private HttpStatus status = HttpStatus.OK;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private byte[] body = new byte[0];

        public Builder status(HttpStatus status) { this.status = status; return this; }
        public Builder header(String name, String value) { headers.put(name, value); return this; }
        public Builder body(String text) { this.body = text != null ? text.getBytes(StandardCharsets.UTF_8) : new byte[0]; return this; }
        public Builder body(byte[] data) { this.body = data != null ? data : new byte[0]; return this; }
        public HttpResponse build() { return new HttpResponse(this); }
    }
}
