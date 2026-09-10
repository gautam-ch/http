package server.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HttpParser {
    public static HttpRequest parse(BufferedReader reader) throws IOException, HttpParseException {
        String requestLine = reader.readLine();
        while (requestLine != null && requestLine.trim().isEmpty()) {
            requestLine = reader.readLine();
        }
        if (requestLine == null) return null;

        String[] parts = requestLine.split("\\s+");
        if (parts.length < 3) {
            throw new HttpParseException("Malformed request line: " + requestLine);
        }

        HttpMethod method;
        try {
            method = HttpMethod.fromString(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new HttpParseException("Invalid method: " + parts[0]);
        }

        String path = parts[1];
        String version = parts[2];

        Map<String, String> headers = new HashMap<>();
        String headerLine;
        while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
            int colon = headerLine.indexOf(':');
            if (colon != -1) {
                String name = headerLine.substring(0, colon).trim();
                String value = headerLine.substring(colon + 1).trim();
                headers.put(name, value);
            }
        }

        byte[] body = new byte[0];
        String clStr = headers.get("Content-Length");
        if (clStr != null) {
            int contentLength = Integer.parseInt(clStr.trim());
            if (contentLength > 0) {
                char[] chars = new char[contentLength];
                int total = 0;
                while (total < contentLength) {
                    int r = reader.read(chars, total, contentLength - total);
                    if (r == -1) break;
                    total += r;
                }
                body = new String(chars, 0, total).getBytes(StandardCharsets.UTF_8);
            }
        }

        return new HttpRequest(method, path, version, headers, body);
    }
}
