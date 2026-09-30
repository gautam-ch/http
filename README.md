# Core Java Multithreaded HTTP/1.1 Server

A lightweight, multithreaded HTTP/1.1 web server built from first principles using pure Core Java (`java.net` and `java.util.concurrent`), with zero third-party dependencies or external web frameworks.

Built as an exploration of low-level network programming, RFC 7230 protocol internals, and bounded concurrency models. Originally inspired by the [CodeCrafters "Build Your Own HTTP Server" Challenge](https://github.com/codecrafters-io/build-your-own-http-server).

---

## Motivation

Modern frameworks like Spring Boot or Netty make it easy to expose REST endpoints with a few annotations, but they completely abstract away what actually happens when raw bytes hit the network interface card (NIC). 

This project explores what lies beneath those abstractions:
- How does the operating system handle the TCP three-way handshake and queue incoming connections?
- How do you parse an incoming text/binary byte stream into HTTP request tokens without reading beyond the payload?
- Why do naive multi-threaded socket servers crash under surge load, and how do you design backpressure and bounded thread pools to protect the JVM?

---

## Architecture Overview

```
                        Concurrent HTTP Clients
                                   │
                                   ▼  [TCP Port 4221]
                 ┌───────────────────────────────────┐
                 │    ServerSocket Acceptor Loop     │
                 │   - SO_REUSEADDR = true           │
                 │   - Backlog Queue: 128            │
                 └─────────────────┬─────────────────┘
                                   │ Accepts Socket
                                   ▼
          ┌─────────────────────────────────────────────────┐
          │            ThreadPoolExecutor Pipeline          │
          │                                                 │
          │   ┌─────────────────────────────────────────┐   │
          │   │ Bounded Queue: ArrayBlockingQueue(500)  │   │
          │   └────────────────────┬────────────────────┘   │
          │                        │                        │
          │      [http-worker-1]   [http-worker-2] ...      │
          │      Core Pool = CPU   Max Pool = CPU * 4       │
          └────────────────────────┬────────────────────────┘
                                   │
                ┌──────────────────┴──────────────────┐
                │ (Available Worker)                  │ (Queue Saturated)
                ▼                                     ▼
  ┌───────────────────────────┐         ┌───────────────────────────┐
  │     ConnectionHandler     │         │   503 Rejection Policy    │
  │ - SO_TIMEOUT = 15s        │         │ - HTTP/1.1 503 Service    │
  │ - Custom RFC 7230 Parser  │         │   Unavailable             │
  │ - Socket Lifecycle        │         │ - Retry-After: 5          │
  └─────────────┬─────────────┘         │ - Clean socket closure    │
                │                       └───────────────────────────┘
                ▼
  ┌───────────────────────────┐
  │      In-Memory Router     │
  │ - Exact Path Matches      │
  │ - Dynamic / Regex Paths   │
  │ - 405 Method Validation   │
  └─────────────┬─────────────┘
                │
                ▼
  ┌───────────────────────────┐
  │  HttpResponse Serializer  │
  │ - RFC 1123 Date Header    │
  │ - Content-Length Framing  │
  │ - Direct Output Stream    │
  └───────────────────────────┘
```

---

## Key Design Decisions & Systems Trade-offs

### 1. Concurrency: Bounded ThreadPool vs. Naive Threads
A common anti-pattern in tutorial socket programming is spawning an unmanaged thread per connection:
```java
// Anti-pattern: Unbounded thread allocation
new Thread(new ConnectionHandler(clientSocket)).start();
```
In production, this leads directly to fatal issues:
- **Stack Memory Exhaustion**: Each JVM OS-level platform thread consumes 512 KB to 1 MB of stack memory. A sudden surge of 5,000 connections demands ~5 GB of off-heap memory purely for thread stacks, triggering `java.lang.OutOfMemoryError: unable to create native thread`.
- **CPU Kernel Thrashing**: When hundreds of threads contend for CPU cores, the OS kernel spends more cycles switching thread register states than running application logic.

**Our Solution**:
- We configure a bounded `ThreadPoolExecutor`:
  - **Core Pool**: Dynamically sized to `Runtime.getRuntime().availableProcessors()` to ensure continuous CPU utilization.
  - **Max Pool**: Scaled to `availableProcessors * 4` to handle blocking socket I/O.
  - **Bounded Work Queue**: Built with `ArrayBlockingQueue(500)`. Unlike `Executors.newFixedThreadPool()` which defaults to an unbounded `LinkedBlockingQueue` (hiding heap growth), our queue has a strict physical capacity.
  - **503 Saturation Handling**: When both the queue and pool are fully saturated, a custom `RejectedExecutionHandler` immediately writes `HTTP/1.1 503 Service Unavailable` with a `Retry-After: 5` header and cleanly closes the socket.

### 2. Stream Parsing & RFC 7230 Conformance
- **Line Endings**: Per RFC 7230 §3, headers are delimited by strict CRLF (`\r\n`).
- **Case-Insensitive Headers**: RFC 7230 §3.2 specifies that HTTP header field names are case-insensitive. We normalize headers into an unmodifiable `TreeMap` keyed with `String.CASE_INSENSITIVE_ORDER`, allowing seamless lookup for `User-Agent`, `user-agent`, or `Content-Length`.
- **Safe Body Reads**: For requests containing a payload (such as `POST /files/*`), the parser reads exactly `Content-Length` bytes from the stream, avoiding hangs caused by trying to read to EOF on open TCP connections.

### 3. Defensive Socket Security & Timeouts
- **Slowloris Attack Defense**: Without read timeouts, an attacker can open hundreds of connections and transmit 1 byte every few minutes, holding threads hostage indefinitely. We set `clientSocket.setSoTimeout(15000)` (15 seconds) on every accepted connection.
- **Directory Traversal Protection**: On file serving endpoints (`/files/{filename}`), we resolve paths against the base directory and verify that `targetPath.normalize().startsWith(baseDirectory)`. Any attempt to request `../../etc/passwd` or windows system files is rejected immediately with a `400 Bad Request`.

---

## Supported Endpoints

| Method | Endpoint | Description | Status Code |
|---|---|---|:---:|
| `GET` | `/` | Server status root endpoint | `200 OK` |
| `GET` | `/health` | JSON health & monitoring status | `200 OK` |
| `GET` | `/echo/{message}` | Dynamic route extracting path variable and returning it in body | `200 OK` |
| `GET` | `/user-agent` | Inspects and returns the client's `User-Agent` request header | `200 OK` |
| `GET` | `/files/{filename}` | Streams binary/text file from server disk (`application/octet-stream`) | `200 OK` / `404 Not Found` |
| `POST` | `/files/{filename}` | Ingests `Content-Length` payload bytes and writes file to disk | `201 Created` |

---

## Project Structure

```
d:/projects/http/
├── pom.xml                               # Maven configuration (Java 21 target)
├── README.md                             # Architecture documentation
├── src/
│   ├── main/
│   │   └── java/
│   │       └── server/
│   │           ├── Main.java              # CLI entry point, routing setup, shutdown hooks
│   │           ├── HttpServer.java        # ServerSocket lifecycle and ThreadPoolExecutor
│   │           ├── ConnectionHandler.java # Socket worker with SO_TIMEOUT and 503 handling
│   │           ├── http/
│   │           │   ├── HttpMethod.java    # Supported HTTP verbs
│   │           │   ├── HttpStatus.java    # Status codes and reason phrases
│   │           │   ├── HttpRequest.java   # Immutable request model with case-insensitive headers
│   │           │   ├── HttpResponse.java  # Response builder and binary stream serializer
│   │           │   ├── HttpParser.java    # RFC 7230 byte stream parser
│   │           │   └── HttpParseException.java
│   │           └── routing/
│   │               ├── Router.java        # In-memory router (exact & regex patterns)
│   │               └── RequestHandler.java# Functional interface for route callbacks
│   └── test/
│       └── java/
│           └── server/
│               ├── HttpParserTest.java    # Unit tests for request lines, headers, and payloads
│               └── RouterTest.java        # Unit tests for route resolution and 404s
```

---

## Quickstart Guide

### 1. Compile

**Windows PowerShell:**
```powershell
New-Item -ItemType Directory -Force target/classes
javac -d target/classes src/main/java/server/*.java src/main/java/server/http/*.java src/main/java/server/routing/*.java src/test/java/server/*.java
```

**Linux / macOS:**
```bash
mkdir -p target/classes
javac -d target/classes $(find src/main/java -name "*.java") $(find src/test/java -name "*.java")
```

*(Or use Maven: `mvn clean compile`)*

### 2. Run the Automated Tests
```bash
java -cp target/classes server.HttpParserTest
java -cp target/classes server.RouterTest
```
Expected output:
```text
-> [HttpParserTest]: All test assertions passed successfully.
-> [RouterTest]: All routing assertions passed successfully.
```

### 3. Start the Server
Default port is **4221** (the standard CodeCrafters test port). You can pass `--port` and `--directory` as arguments:

```bash
java -cp target/classes server.Main --port 4221 --directory ./files
```

---

## Manual Verification (cURL Examples)

In another terminal, test the running server:

```bash
# 1. Health endpoint
curl -i http://localhost:4221/health

# 2. Dynamic echo endpoint
curl -i http://localhost:4221/echo/hello-systems-engineer

# 3. User-Agent header reflection
curl -i -H "User-Agent: TerminalClient/1.0" http://localhost:4221/user-agent

# 4. Upload a file (POST)
curl -i -X POST http://localhost:4221/files/sample.txt -d "Multithreaded HTTP Server in Core Java"

# 5. Retrieve the uploaded file (GET)
curl -i http://localhost:4221/files/sample.txt

# 6. Test 404 behavior
curl -i http://localhost:4221/non-existent-route
```

---

## Limitations & Future Work

While this server correctly implements the core requirements of HTTP/1.1 request-response cycles, real-world web servers implement additional protocol layers:
- **Connection Keep-Alive (Persistent Connections)**: Currently, every request is closed after writing (`Connection: close`). Reusing TCP connections across multiple requests requires a connection state machine.
- **Chunked Transfer Encoding**: Currently supports fixed-length payloads via `Content-Length`. Supporting `Transfer-Encoding: chunked` would enable streaming data of unknown length.
- **Non-blocking I/O (Java NIO / Epoll)**: The current blocking-I/O model with a bounded thread pool is simple and predictable. Migrating to `java.nio.channels.Selector` or Java 21 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`) would allow handling tens of thousands of idle connections concurrently with minimal OS memory overhead.

---

## License
MIT
