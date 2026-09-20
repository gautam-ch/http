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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        int port = 4221;
        String filesDirectory = "./files";

        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) port = Integer.parseInt(args[++i]);
            else if ("--directory".equals(args[i]) && i + 1 < args.length) filesDirectory = args[++i];
        }

        final Path directoryPath = Paths.get(filesDirectory).toAbsolutePath().normalize();
        try { Files.createDirectories(directoryPath); } catch (Exception ignored) {}

        Router router = new Router();
        router.get("/", req -> HttpResponse.ok("Core Java HTTP Server is running.\n"));
        router.get("/echo/*", req -> HttpResponse.ok(req.getPath().substring("/echo/".length())));
        router.get("/user-agent", req -> HttpResponse.ok(req.getHeader("User-Agent") != null ? req.getHeader("User-Agent") : ""));

        // Stage 7: Return a file
        router.get("/files/*", req -> {
            String filename = req.getPath().substring("/files/".length());
            Path targetFile = directoryPath.resolve(filename).normalize();
            if (!targetFile.startsWith(directoryPath)) {
                return HttpResponse.badRequest("400 Bad Request: Path traversal forbidden.\n");
            }
            if (!Files.exists(targetFile) || Files.isDirectory(targetFile)) {
                return HttpResponse.notFound("404 Not Found: File not found.\n");
            }
            return HttpResponse.ok(Files.readAllBytes(targetFile), "application/octet-stream");
        });

        // Stage 8: Post a file
        router.post("/files/*", req -> {
            String filename = req.getPath().substring("/files/".length());
            Path targetFile = directoryPath.resolve(filename).normalize();
            if (!targetFile.startsWith(directoryPath)) {
                return HttpResponse.badRequest("400 Bad Request: Path traversal forbidden.\n");
            }
            Files.write(targetFile, req.getBody());
            return HttpResponse.created("File created successfully.\n");
        });

        System.out.println("Server running on port " + port + ", serving files from " + directoryPath);
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
