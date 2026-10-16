package bloodbank;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Blood Bank Management System - plain Java web server, no frameworks. */
public class Main {

    static final String USER = "admin", PASS = "admin123";
    static final Set<String> sessions = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) throws IOException {
        Store.load();
        int port = System.getenv("PORT") != null ? Integer.parseInt(System.getenv("PORT")) : 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", Main::handle);
        server.start();
        System.out.println("Blood Bank running at http://localhost:" + port + "  (login: admin / admin123)");
    }

    static void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            boolean post = ex.getRequestMethod().equals("POST");
            Map<String, String> query = parse(ex.getRequestURI().getRawQuery());
            Map<String, String> form = post ? parse(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)) : Map.of();
            String msg = query.get("msg");

            // ---- login / logout ----
            if (path.equals("/login")) {
                if (post && USER.equals(form.get("username")) && PASS.equals(form.get("password"))) {
                    String token = UUID.randomUUID().toString();
                    sessions.add(token);
                    ex.getResponseHeaders().add("Set-Cookie", "token=" + token + "; Path=/; HttpOnly");
                    redirect(ex, "/");
                } else {
                    send(ex, 200, Pages.login(post ? "Wrong username or password." : null));
                }
                return;
            }
            if (path.equals("/logout")) {
                sessions.remove(cookie(ex));
                redirect(ex, "/login");
                return;
            }
            if (!sessions.contains(cookie(ex))) { redirect(ex, "/login"); return; }

            // ---- pages ----
            switch (path) {
                case "/" -> send(ex, 200, Pages.dashboard());
                case "/donors" -> send(ex, 200, Pages.donors(query.get("group"), msg));
                case "/donors/add" -> {
                    if (post) {
                        String m = Store.addDonor(form.get("name"), num(form.get("age")), form.get("group"),
                                form.get("phone"), form.get("city"));
                        redirect(ex, "/donors?msg=" + enc(m));
                    } else send(ex, 200, Pages.addDonorForm(msg));
                }
                case "/donors/delete" -> redirect(ex, "/donors?msg=" + enc(Store.deleteDonor(num(form.get("id")))));
                case "/donate" -> redirect(ex, "/donors?msg=" + enc(Store.donate(num(form.get("id")), num(form.get("units")))));
                case "/requests" -> send(ex, 200, Pages.requests(msg));
                case "/requests/add" -> redirect(ex, "/requests?msg=" + enc(Store.addRequest(
                        form.get("patient"), form.get("group"), num(form.get("units")), form.get("hospital"))));
                case "/requests/fulfill" -> redirect(ex, "/requests?msg=" + enc(Store.fulfill(num(form.get("id")))));
                default -> send(ex, 404, Pages.layout("Not found", null, "<h1>404 - Page not found</h1>"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            send(ex, 500, "Something went wrong: " + Pages.esc(e.getMessage()));
        }
    }

    // ---------- small helpers ----------

    static Map<String, String> parse(String s) {
        Map<String, String> map = new HashMap<>();
        if (s == null || s.isEmpty()) return map;
        for (String pair : s.split("&")) {
            int i = pair.indexOf('=');
            String k = i < 0 ? pair : pair.substring(0, i);
            String v = i < 0 ? "" : pair.substring(i + 1);
            map.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return map;
    }

    static String cookie(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Cookie");
        if (header == null) return "";
        for (String c : header.split(";")) {
            c = c.trim();
            if (c.startsWith("token=")) return c.substring(6);
        }
        return "";
    }

    static int num(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    static void send(HttpExchange ex, int code, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    static void redirect(HttpExchange ex, String to) throws IOException {
        ex.getResponseHeaders().set("Location", to);
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }
}
