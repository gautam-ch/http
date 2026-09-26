package server;

import server.http.HttpResponse;
import server.routing.Router;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Main {
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        configureLogging();

        int port = 4221;
        String filesDirectory = "./files";

        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if ("--directory".equals(args[i]) && i + 1 < args.length) {
                filesDirectory = args[++i];
            }
        }

        final Path directoryPath = Paths.get(filesDirectory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(directoryPath);
            logger.info("Serving files from: " + directoryPath);
        } catch (IOException e) {
            logger.warning("Could not initialize files directory: " + e.getMessage());
        }

        Router router = new Router();

        // Stage 1 & 2: Root 200 OK
        router.get("/", req -> HttpResponse.ok("Core Java HTTP/1.1 Server is running.\n"));

        // Health monitoring
        router.get("/health", req -> HttpResponse.json("{\"status\":\"UP\",\"port\":" + 4221 + "}\n"));

        // Stage 5: User-Agent header inspection
        router.get("/user-agent", req -> {
            String ua = req.getHeader("User-Agent");
            return HttpResponse.ok(ua != null ? ua : "");
        });

        // Stage 4: Dynamic Echo Route (/echo/{message})
        router.get("/echo/*", req -> {
            String path = req.getPath();
            String echoStr = path.length() > "/echo/".length() ? path.substring("/echo/".length()) : "";
            return HttpResponse.ok(echoStr);
        });

        // Stage 7: Return a File (/files/{filename})
        router.get("/files/*", req -> {
            String filename = req.getPath().substring("/files/".length());
            Path target = directoryPath.resolve(filename).normalize();
            if (!target.startsWith(directoryPath)) {
                return HttpResponse.badRequest("400 Bad Request: Path traversal forbidden.\n");
            }
            if (!Files.exists(target) || Files.isDirectory(target)) {
                return HttpResponse.notFound("404 Not Found: File '" + filename + "' does not exist.\n");
            }
            return HttpResponse.ok(Files.readAllBytes(target), "application/octet-stream");
        });

        // Stage 8: Post a File (/files/{filename})
        router.post("/files/*", req -> {
            String filename = req.getPath().substring("/files/".length());
            Path target = directoryPath.resolve(filename).normalize();
            if (!target.startsWith(directoryPath)) {
                return HttpResponse.badRequest("400 Bad Request: Path traversal forbidden.\n");
            }
            Files.write(target, req.getBody());
            logger.info("Stored file: " + target + " (" + req.getBody().length + " bytes)");
            return HttpResponse.created("File saved successfully.\n");
        });

        int cores = Runtime.getRuntime().availableProcessors();
        HttpServer server = new HttpServer(port, router, cores, cores * 4, 500);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received.");
            server.stop();
        }, "shutdown-hook"));

        try {
            server.start();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Fatal server failure", e);
            System.exit(1);
        }
    }

    private static void configureLogging() {
        Logger root = Logger.getLogger("");
        for (var h : root.getHandlers()) root.removeHandler(h);
        ConsoleHandler ch = new ConsoleHandler();
        ch.setFormatter(new SimpleFormatter() {
            private static final String FMT = "[%1$tF %1$tT] [%2$-7s] %3$s %n";
            @Override
            public synchronized String format(java.util.logging.LogRecord lr) {
                return String.format(FMT, new java.util.Date(lr.getMillis()), lr.getLevel().getLocalizedName(), lr.getMessage());
            }
        });
        root.addHandler(ch);
        root.setLevel(Level.INFO);
    }
}
