package server;

import server.http.HttpParser;
import server.http.HttpRequest;
import server.http.HttpResponse;
import server.routing.Router;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) {
        int port = 4221;

        Router router = new Router();
        router.get("/", req -> HttpResponse.ok("Core Java HTTP Server is running.\n"));
        router.get("/echo/*", req -> HttpResponse.ok(req.getPath().substring("/echo/".length())));
        router.get("/user-agent", req -> HttpResponse.ok(req.getHeader("User-Agent") != null ? req.getHeader("User-Agent") : ""));

        System.out.println("Router initialized. Listening on port " + port + "...");
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                    HttpRequest request = HttpParser.parse(reader);
                    if (request == null) continue;
                    HttpResponse response = router.dispatch(request);
                    response.writeTo(clientSocket.getOutputStream());
                }
            }
        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
}
