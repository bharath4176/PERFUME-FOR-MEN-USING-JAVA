
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

public class PerfumeMartServer {

    static final String DB_URL = "jdbc:mysql://localhost:3306/perfumemart";
    static final String DB_USER = "root";
    static final String DB_PASS = "123456789";
    static final int PORT = 8080;

    static final Path BASE_DIR = Paths.get("C:/perfume/PerfumeMart")
            .toAbsolutePath().normalize();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/api/products", PerfumeMartServer::products);
        server.createContext("/api/orders", PerfumeMartServer::orders);
        server.createContext("/", PerfumeMartServer::staticFiles);

        server.start();
        System.out.println("PerfumeMart running at http://localhost:" + PORT);
        System.out.println("Website folder: " + BASE_DIR);
    }

    static Connection db() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    static void send(HttpExchange ex, int code, String type, byte[] body)
            throws IOException {
        ex.getResponseHeaders().set(
                "Content-Type", type + "; charset=utf-8");
        ex.sendResponseHeaders(code, body.length);

        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    static void json(HttpExchange ex, int code, String value)
            throws IOException {
        send(ex, code, "application/json",
                value.getBytes(StandardCharsets.UTF_8));
    }

    static String esc(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    // GET /api/products
    static void products(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) {
            json(ex, 405, "{\"error\":\"GET only\"}");
            return;
        }

        try (Connection c = db();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT id,name,brand,price,size,stock FROM products ORDER BY id")) {

            StringBuilder sb = new StringBuilder("[");
            boolean first = true;

            while (rs.next()) {
                if (!first)
                    sb.append(",");
                first = false;

                sb.append(String.format(
                        Locale.US,
                        "{\"id\":%d,\"name\":\"%s\",\"brand\":\"%s\",\"price\":%s,\"size\":\"%s\",\"stock\":%d}",
                        rs.getInt("id"),
                        esc(rs.getString("name")),
                        esc(rs.getString("brand")),
                        rs.getBigDecimal("price").toPlainString(),
                        esc(rs.getString("size")),
                        rs.getInt("stock")));
            }

            json(ex, 200, sb.append("]").toString());

        } catch (SQLException e) {
            json(ex, 500, "{\"error\":\"Database connection failed\"}");
            e.printStackTrace();
        }
    }

    // POST /api/orders
    static void orders(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) {
            json(ex, 405, "{\"error\":\"POST only\"}");
            return;
        }

        String body = new String(
                ex.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);

        String name = field(body, "name");
        String phone = field(body, "phone");
        String address = field(body, "address");

        List<int[]> items = new ArrayList<>();

        Matcher m = Pattern.compile(
                "\\{\\s*\"id\"\\s*:\\s*(\\d+)\\s*,\\s*\"qty\"\\s*:\\s*(\\d+)\\s*\\}").matcher(body);

        while (m.find()) {
            int id = Integer.parseInt(m.group(1));
            int qty = Integer.parseInt(m.group(2));

            if (id <= 0 || qty <= 0) {
                json(ex, 400, "{\"error\":\"Invalid product or quantity\"}");
                return;
            }

            items.add(new int[] { id, qty });
        }

        if (name.isBlank() || phone.isBlank()
                || address.isBlank() || items.isEmpty()) {
            json(ex, 400,
                    "{\"error\":\"Enter name, phone, address and cart items\"}");
            return;
        }

        try (Connection c = db()) {
            c.setAutoCommit(false);

            try {
                double total = 0;

                for (int[] item : items) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT price,stock FROM products WHERE id=? FOR UPDATE")) {

                        ps.setInt(1, item[0]);

                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new SQLException("Product not found");
                            }

                            if (rs.getInt("stock") < item[1]) {
                                throw new SQLException("Not enough stock");
                            }

                            total += rs.getBigDecimal("price").doubleValue()
                                    * item[1];
                        }
                    }
                }

                int orderId;

                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO orders(customer_name,phone,address,total) VALUES(?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {

                    ps.setString(1, name);
                    ps.setString(2, phone);
                    ps.setString(3, address);
                    ps.setDouble(4, total);
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Order ID not generated");
                        }
                        orderId = keys.getInt(1);
                    }
                }

                for (int[] item : items) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO order_items(order_id,product_id,quantity) VALUES(?,?,?)")) {

                        ps.setInt(1, orderId);
                        ps.setInt(2, item[0]);
                        ps.setInt(3, item[1]);
                        ps.executeUpdate();
                    }

                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE products SET stock=stock-? WHERE id=?")) {

                        ps.setInt(1, item[1]);
                        ps.setInt(2, item[0]);
                        ps.executeUpdate();
                    }
                }

                c.commit();
                json(ex, 200,
                        "{\"orderId\":" + orderId + ",\"total\":" + total + "}");

            } catch (SQLException e) {
                c.rollback();
                json(ex, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            }

        } catch (SQLException e) {
            json(ex, 500, "{\"error\":\"Database connection failed\"}");
            e.printStackTrace();
        }
    }

    static String field(String body, String key) {
        Matcher m = Pattern.compile(
                "\"" + Pattern.quote(key)
                        + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(body);

        return m.find()
                ? m.group(1).replace("\\\"", "\"").replace("\\\\", "\\")
                : "";
    }

    // Serve HTML, CSS, JavaScript and images from the project folder
    static void staticFiles(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();

        if (path.equals("/")) {
            path = "/index.html";
        }

        String fileName = path.substring(1);

        Path file = BASE_DIR.resolve(fileName).normalize();

        if (!file.startsWith(BASE_DIR) || !Files.isRegularFile(file)) {
            byte[] message = ("File not found: " + fileName)
                    .getBytes(StandardCharsets.UTF_8);

            send(ex, 404, "text/plain", message);
            return;
        }

        String lower = fileName.toLowerCase(Locale.ROOT);
        String type;

        if (lower.endsWith(".html")) {
            type = "text/html";
        } else if (lower.endsWith(".css")) {
            type = "text/css";
        } else if (lower.endsWith(".js")) {
            type = "application/javascript";
        } else if (lower.endsWith(".png")) {
            type = "image/png";
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            type = "image/jpeg";
        } else if (lower.endsWith(".svg")) {
            type = "image/svg+xml";
        } else {
            type = "application/octet-stream";
        }

        send(ex, 200, type, Files.readAllBytes(file));
    }
}
