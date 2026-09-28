package server;

import server.http.HttpMethod;
import server.http.HttpRequest;
import server.http.HttpResponse;
import server.http.HttpStatus;
import server.routing.Router;

import java.util.Collections;

public class RouterTest {
    public static void main(String[] args) {
        Router router = new Router();
        router.get("/test", req -> HttpResponse.ok("test-passed"));
        router.get("/echo/*", req -> HttpResponse.ok(req.getPath().substring("/echo/".length())));

        HttpRequest exactReq = new HttpRequest(HttpMethod.GET, "/test", "HTTP/1.1", Collections.emptyMap(), new byte[0]);
        HttpResponse res1 = router.dispatch(exactReq);
        assert res1.getStatus() == HttpStatus.OK;

        HttpRequest echoReq = new HttpRequest(HttpMethod.GET, "/echo/developer", "HTTP/1.1", Collections.emptyMap(), new byte[0]);
        HttpResponse res2 = router.dispatch(echoReq);
        assert res2.getStatus() == HttpStatus.OK;
        assert "developer".equals(new String(res2.getBody()));

        HttpRequest missingReq = new HttpRequest(HttpMethod.GET, "/unknown", "HTTP/1.1", Collections.emptyMap(), new byte[0]);
        HttpResponse res3 = router.dispatch(missingReq);
        assert res3.getStatus() == HttpStatus.NOT_FOUND;

        System.out.println("-> [RouterTest]: All routing assertions passed successfully.");
    }
}
