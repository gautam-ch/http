package server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) {
        int port = 4221;
        System.out.println("Starting HTTP server on port " + port + "...");
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    System.out.println("Accepted TCP connection from " + clientSocket.getRemoteSocketAddress());
                    BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                    String requestLine = reader.readLine();
                    System.out.println("Request: " + requestLine);

                    OutputStream out = clientSocket.getOutputStream();
                    String response = "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n";
                    out.write(response.getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                }
            }
        } catch (Exception e) {
            System.err.println("Server socket exception: " + e.getMessage());
        }
    }
}
