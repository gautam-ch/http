package server;

import server.http.HttpParseException;
import server.http.HttpParser;
import server.http.HttpRequest;
import server.http.HttpResponse;
import server.routing.Router;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectionHandler implements Runnable {
    private static final Logger logger = Logger.getLogger(ConnectionHandler.class.getName());
    private static final int SOCKET_TIMEOUT_MS = 15000;

    private final Socket clientSocket;
    private final Router router;

    public ConnectionHandler(Socket clientSocket, Router router) {
        this.clientSocket = clientSocket;
        this.router = router;
    }

    @Override
    public void run() {
        try (clientSocket) {
            clientSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            HttpRequest request;
            try {
                request = HttpParser.parse(reader);
            } catch (HttpParseException e) {
                HttpResponse.badRequest("400 Bad Request: " + e.getMessage() + "\n")
                        .writeTo(clientSocket.getOutputStream());
                return;
            }

            if (request == null) return;

            HttpResponse response = router.dispatch(request);
            OutputStream out = clientSocket.getOutputStream();
            response.writeTo(out);

        } catch (SocketTimeoutException e) {
            logger.warning("Socket read timeout from " + clientSocket.getRemoteSocketAddress());
        } catch (IOException e) {
            logger.log(Level.FINE, "Client I/O closed: " + e.getMessage());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error: " + e.getMessage(), e);
        }
    }

    public void rejectWithServiceUnavailable() {
        try (clientSocket) {
            HttpResponse.serviceUnavailable("Server saturated: Connection rejected by bounded worker pool.\n")
                    .writeTo(clientSocket.getOutputStream());
        } catch (IOException ignored) {}
    }
}
