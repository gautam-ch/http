package server;

import server.http.HttpParser;
import server.http.HttpRequest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
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

                    OutputStream out = clientSocket.getOutputStream();
                    String path = request.getPath();
                    StringBuilder response = new StringBuilder();

                    if ("/".equals(path)) {
                        response.append("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
                    } else if ("/user-agent".equals(path)) {
                        String userAgent = request.getHeader("User-Agent");
                        if (userAgent == null) userAgent = "";
                        byte[] body = userAgent.getBytes(StandardCharsets.UTF_8);
                        response.append("HTTP/1.1 200 OK\r\n")
                                .append("Content-Type: text/plain\r\n")
                                .append("Content-Length: ").append(body.length).append("\r\n\r\n")
                                .append(userAgent);
                    } else {
                        response.append("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n");
                    }

                    out.write(response.toString().getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            }
        } catch (Exception e) {
            System.err.println("Server exception: " + e.getMessage());
        }
    }
}
