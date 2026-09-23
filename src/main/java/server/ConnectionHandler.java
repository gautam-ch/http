package server;

import server.http.HttpParser;
import server.http.HttpRequest;
import server.http.HttpResponse;
import server.routing.Router;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ConnectionHandler implements Runnable {
    private final Socket clientSocket;
    private final Router router;

    public ConnectionHandler(Socket clientSocket, Router router) {
        this.clientSocket = clientSocket;
        this.router = router;
    }

    @Override
    public void run() {
        try (clientSocket) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            HttpRequest request = HttpParser.parse(reader);
            if (request == null) return;

            HttpResponse response = router.dispatch(request);
            OutputStream out = clientSocket.getOutputStream();
            response.writeTo(out);
        } catch (Exception e) {
            System.err.println("Error handling client connection: " + e.getMessage());
        }
    }
}
