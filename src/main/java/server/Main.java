package server;

import server.http.HttpResponse;
import server.routing.Router;

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
        router.get("/", req -> HttpResponse.ok("Core Java HTTP Server running.\n"));
        router.get("/echo/*", req -> HttpResponse.ok(req.getPath().substring("/echo/".length())));
        router.get("/user-agent", req -> HttpResponse.ok(req.getHeader("User-Agent") != null ? req.getHeader("User-Agent") : ""));

        router.get("/files/*", req -> {
            String filename = req.getPath().substring("/files/".length());
            Path targetFile = directoryPath.resolve(filename).normalize();
            if (!Files.exists(targetFile) || Files.isDirectory(targetFile)) {
                return HttpResponse.notFound("404 Not Found: File " + filename + " does not exist.\n");
            }
            return HttpResponse.ok(Files.readAllBytes(targetFile), "application/octet-stream");
        });

        router.post("/files/*", req -> {
            String filename = req.getPath().substring("/files/".length());
            Path targetFile = directoryPath.resolve(filename).normalize();
            Files.write(targetFile, req.getBody());
            return HttpResponse.created("File saved successfully.\n");
        });

        int cores = Runtime.getRuntime().availableProcessors();
        HttpServer server = new HttpServer(port, router, cores, cores * 4, 500);

        try {
            server.start();
        } catch (Exception e) {
            System.err.println("Fatal: " + e.getMessage());
        }
    }
}
