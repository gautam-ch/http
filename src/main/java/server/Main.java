package server;

import server.http.HttpParser;
import server.http.HttpRequest;
import server.http.HttpResponse;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) {
        int port = 4221;
        System.out.println("HTTP Server listening on port " + port);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                    HttpRequest request = HttpParser.parse(reader);
                    if (request == null) continue;

                    String path = request.getPath();
                    HttpResponse response;

                    if ("/".equals(path)) {
                        response = HttpResponse.ok("Core Java HTTP Server is running.\n");
                    } else if (path.startsWith("/echo/")) {
                        String echoText = path.substring("/echo/".length());
                        response = HttpResponse.ok(echoText);
                    } else if ("/user-agent".equals(path)) {
                        String userAgent = request.getHeader("User-Agent");
                        response = HttpResponse.ok(userAgent != null ? userAgent : "");
                    } else {
                        response = HttpResponse.notFound("404 Not Found: " + path + "\n");
                    }

                    response.writeTo(clientSocket.getOutputStream());
                }
            }
        } catch (Exception e) {
            System.err.println("Server exception: " + e.getMessage());
        }
    }
}
