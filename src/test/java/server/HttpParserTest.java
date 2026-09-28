package server;

import server.http.HttpMethod;
import server.http.HttpParser;
import server.http.HttpRequest;

import java.io.BufferedReader;
import java.io.StringReader;

public class HttpParserTest {
    public static void main(String[] args) throws Exception {
        testBasicGetRequest();
        testCaseInsensitiveHeaders();
        testRequestBodyExtraction();
        System.out.println("-> [HttpParserTest]: All test assertions passed successfully.");
    }

    private static void testBasicGetRequest() throws Exception {
        String raw = "GET /echo/hello HTTP/1.1\r\nHost: localhost:4221\r\n\r\n";
        HttpRequest req = HttpParser.parse(new BufferedReader(new StringReader(raw)));
        assert req != null;
        assert req.getMethod() == HttpMethod.GET;
        assert "/echo/hello".equals(req.getPath());
        assert "HTTP/1.1".equals(req.getHttpVersion());
    }

    private static void testCaseInsensitiveHeaders() throws Exception {
        String raw = "GET /user-agent HTTP/1.1\r\nuser-agent: CustomAgent/2.0\r\n\r\n";
        HttpRequest req = HttpParser.parse(new BufferedReader(new StringReader(raw)));
        assert req != null;
        assert "CustomAgent/2.0".equals(req.getHeader("User-Agent"));
        assert "CustomAgent/2.0".equals(req.getHeader("USER-AGENT"));
    }

    private static void testRequestBodyExtraction() throws Exception {
        String body = "Payload Content 12345";
        String raw = "POST /files/data.txt HTTP/1.1\r\nContent-Length: " + body.length() + "\r\n\r\n" + body;
        HttpRequest req = HttpParser.parse(new BufferedReader(new StringReader(raw)));
        assert req != null;
        assert req.getMethod() == HttpMethod.POST;
        assert body.equals(req.getBodyAsString());
    }
}
