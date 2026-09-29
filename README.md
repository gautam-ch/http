# High-Performance Multithreaded HTTP/1.1 Server in Core Java

[![Java](https://img.shields.io/badge/Java-21%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)
[![Build Status](https://img.shields.io/badge/Build-Passing-success?style=for-the-badge)]()

A production-grade, multithreaded HTTP/1.1 web server engineered strictly from scratch using **pure Core Java primitives** (`java.net.ServerSocket`, `java.net.Socket`, and `java.util.concurrent`), without Spring Boot, Netty, or any external frameworks.

This project implements and expands upon the [CodeCrafters "Build Your Own HTTP Server" Challenge](https://github.com/codecrafters-io/build-your-own-http-server), featuring RFC 7230 protocol conformance, an in-memory routing engine, and a bounded `ThreadPoolExecutor` architecture designed to prevent thread exhaustion under surge traffic.

---

## Architecture Diagram

```
                             +-----------------------------------+
                             |     Concurrent HTTP/1.1 Clients   |
                             +-----------------------------------+
                                               |
                                     (Port 4221 TCP Handshake)
                                               v
                          +-----------------------------------------+
                          |   ServerSocket Acceptor Loop            |
                          |   - setReuseAddress(true)               |
                          |   - Backlog Queue: 128                  |
                          +-----------------------------------------+
                                               |
                                    Dispatches Worker Task
                                               v
       +-------------------------------------------------------------------------------+
       |                      Bounded ThreadPoolExecutor                               |
       |                                                                               |
       |  +-------------------------------------------------------------------------+  |
       |  |  Work Queue: ArrayBlockingQueue<Runnable>(Capacity = 500)               |  |
       |  +-------------------------------------------------------------------------+  |
       |                                       |                                       |
       |             [http-worker-1]    [http-worker-2]    [http-worker-N]             |
       |             Core Pool = CPU    Max Pool = CPU * 4                             |
       +-------------------------------------------------------------------------------+
                                               |
                     +-------------------------+-------------------------+
                     | (Normal Execution)                                | (Queue & Pool Saturated)
                     v                                                   v
      +-----------------------------+                     +-----------------------------+
      |      ConnectionHandler      |                     | 503 Rejection Policy        |
      | - SO_TIMEOUT = 15s (Defense |                     | - Writes 503 Unavailable    |
      |   against Slowloris)        |                     | - Retry-After: 5            |
      | - RFC 7230 Stream Parser    |                     | - Closes socket cleanly     |
      +-----------------------------+                     +-----------------------------+
                     |
                     v
      +-----------------------------+
      |       In-Memory Router      |
      | - O(1) Exact Path Matches   |
      | - Regex Dynamic Routes      |
      | - 405 Method Not Allowed    |
      +-----------------------------+
                     |
                     v
      +-----------------------------+
      |   HttpResponse Serializer   |
      | - Status Line, RFC 1123 Date|
      | - Content-Length, Headers   |
      | - Zero-Copy Byte Flush      |
      +-----------------------------+
```

---

## Concurrency Analysis: ThreadPool vs Naive Threading

| Metric | Naive Server (`new Thread()`) | Our Architecture (`ThreadPoolExecutor`) |
|---|---|---|
| **Thread Model** | Unbounded JVM threads | Fixed Core (`N_CPU`) + Max (`N_CPU * 4`) bounded workers |
| **Surge Behavior** | Throws `OutOfMemoryError: unable to create native thread` | Buffers in `ArrayBlockingQueue(500)`; gracefully degrades |
| **Saturation Policy** | OS Thrashing / Process Crash | Rejection Handler returns `503 Service Unavailable` with `Retry-After: 5` |
| **Slowloris Defense** | None (hung threads leak memory indefinitely) | `Socket.setSoTimeout(15000)` automatically purges idle connections |
| **Memory Footprint** | ~1 MB off-heap stack per thread ($10,000 \text{ connections} \approx 10\text{ GB}$) | Bounded memory envelope ($< 64\text{ MB}$ JVM heap footprint) |

---

## CodeCrafters Stage Progression

| Stage | Milestone | Implementation Details |
|:---:|:---|:---|
| **Stage 1 & 2** | **Bind to Port & 200 OK** | `ServerSocket` listening on port `4221` with `SO_REUSEADDR`, returning raw HTTP `200 OK`. |
| **Stage 3** | **Extract URL Path** | RFC 7230 request line parsing; routes `/` to 200 and unmapped targets to 404. |
| **Stage 4** | **Respond with Body** | Endpoint `GET /echo/{str}` extracting path segment, returning `text/plain` and exact `Content-Length`. |
| **Stage 5** | **Read Header** | RFC 7230 case-insensitive header table; `GET /user-agent` header inspection. |
| **Stage 6** | **Concurrent Connections** | Decoupled `ConnectionHandler` executed across a bounded `ThreadPoolExecutor`. |
| **Stage 7** | **Return a File** | `GET /files/{filename}` serving `application/octet-stream` from `--directory` with path traversal defense. |
| **Stage 8** | **Post a File** | `POST /files/{filename}` streaming exact `Content-Length` payload bytes directly to disk. |

---

## Quickstart Guide

### 1. Compile with `javac`
```bash
# Compile application & test classes
mkdir -p target/classes
javac -d target/classes $(find src/main/java -name "*.java")
```

### 2. Run the Server
```bash
# Start server on default CodeCrafters port (4221)
java -cp target/classes server.Main --port 4221 --directory ./files
```

### 3. Verification Commands (cURL)
```bash
# 1. Health check
curl -i http://localhost:4221/health

# 2. Echo dynamic route
curl -i http://localhost:4221/echo/senior-systems-engineer

# 3. User-Agent header inspection
curl -i -H "User-Agent: SystemsClient/1.0" http://localhost:4221/user-agent

# 4. Upload a file (POST)
curl -i -X POST http://localhost:4221/files/sample.txt -d "Multithreaded HTTP Server in Core Java"

# 5. Retrieve the uploaded file (GET)
curl -i http://localhost:4221/files/sample.txt
```

---

## Automated Test Suite

Run the zero-dependency test suite directly:
```bash
# Compile and run parser assertions
javac -d target/classes src/main/java/server/**/*.java src/test/java/server/*.java
java -cp target/classes server.HttpParserTest
java -cp target/classes server.RouterTest
```

---

## Author
**Gautam Chouhan** ([@gautam-ch](https://github.com/gautam-ch))
