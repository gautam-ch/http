package server;

import server.routing.Router;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpServer {
    private static final Logger logger = Logger.getLogger(HttpServer.class.getName());

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
        if (!running.compareAndSet(false, true)) return;

        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(queueCapacity);
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "http-worker-" + count.getAndIncrement());
            }
        };

        RejectedExecutionHandler rejectionHandler = (task, exec) -> {
            logger.warning("ThreadPool and bounded queue saturated. Dropping connection with 503.");
            if (task instanceof ConnectionHandler ch) {
                ch.rejectWithServiceUnavailable();
            }
        };

        this.executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L,
                TimeUnit.SECONDS,
                workQueue,
                threadFactory,
                rejectionHandler
        );
        this.executor.allowCoreThreadTimeOut(true);

        this.serverSocket = new ServerSocket(port, 128);
        this.serverSocket.setReuseAddress(true);
        logger.info(String.format("HttpServer started on port %d [Core: %d, Max: %d, Queue: %d]",
                port, corePoolSize, maxPoolSize, queueCapacity));

        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                executor.execute(new ConnectionHandler(clientSocket, router));
            } catch (SocketException e) {
                if (!running.get()) break;
                logger.log(Level.SEVERE, "ServerSocket error", e);
            } catch (Exception e) {
                if (running.get()) logger.log(Level.SEVERE, "Accept error", e);
            }
        }
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        logger.info("Stopping HttpServer on port " + port + "...");
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
            if (executor != null) {
                executor.shutdown();
                if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during server shutdown", e);
        }
        logger.info("HttpServer stopped successfully.");
    }
}
