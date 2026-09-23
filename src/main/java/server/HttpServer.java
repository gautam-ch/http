package server;

import server.routing.Router;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpServer {
    private final int port;
    private final Router router;
    private final int corePoolSize;
    private final int maxPoolSize;
    private final int queueCapacity;

    private ServerSocket serverSocket;
    private ThreadPoolExecutor executor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public HttpServer(int port, Router router, int corePoolSize, int maxPoolSize, int queueCapacity) {
        this.port = port;
        this.router = router;
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.queueCapacity = queueCapacity;
    }

    public void start() throws IOException {
        running.set(true);
        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(queueCapacity);
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "http-worker-" + count.getAndIncrement());
            }
        };

        this.executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L,
                TimeUnit.SECONDS,
                workQueue,
                threadFactory
        );

        this.serverSocket = new ServerSocket(port, 128);
        this.serverSocket.setReuseAddress(true);
        System.out.printf("HttpServer started on port %d with bounded ThreadPool [Core: %d, Max: %d, Queue: %d]%n",
                port, corePoolSize, maxPoolSize, queueCapacity);

        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                executor.execute(new ConnectionHandler(clientSocket, router));
            } catch (Exception e) {
                if (!running.get()) break;
            }
        }
    }

    public void stop() {
        running.set(false);
        try {
            if (serverSocket != null) serverSocket.close();
            if (executor != null) executor.shutdown();
        } catch (Exception ignored) {}
    }
}
