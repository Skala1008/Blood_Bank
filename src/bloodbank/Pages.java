package bloodbank;

import java.util.List;
import java.util.Map;

/** Builds every HTML page as a plain Java string. */
public class Pages {

    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String layout(String title, String msg, String body) {
        String message = (msg == null || msg.isEmpty()) ? "" : "<div class='msg'>" + esc(msg) + "</div>";
        return """
            <!DOCTYPE html><html><head><meta charset='UTF-8'>
            <meta name='viewport' content='width=device-width, initial-scale=1'>
            <title>%s - Blood Bank</title>
            <style>
              *{box-sizing:border-box} body{font-family:Arial,sans-serif;margin:0;background:#f5f5f5;color:#222}
              nav{background:#b71c1c;padding:12px 20px;display:flex;gap:18px;align-items:center;flex-wrap:wrap}
              nav b{color:#fff;font-size:18px;margin-right:auto} nav a{color:#fff;text-decoration:none}
              nav a:hover{text-decoration:underline}
              main{max-width:1000px;margin:20px auto;padding:0 15px}
              .cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(110px,1fr));gap:12px;margin:15px 0}
              .card{background:#fff;border-radius:8px;padding:15px;text-align:center;box-shadow:0 1px 3px #0003}
              .card h2{margin:0;color:#b71c1c} .card p{margin:5px 0 0;font-size:22px} .low p{color:#e65100}
              table{width:100%%;border-collapse:collapse;background:#fff;box-shadow:0 1px 3px #0003}
              th,td{padding:8px 10px;border-bottom:1px solid #eee;text-align:left;font-size:14px}
              th{background:#eee} form.box{background:#fff;padding:15px;border-radius:8px;box-shadow:0 1px 3px #0003;margin-bottom:20px}
              input,select{padding:7px;margin:4px 6px 4px 0;border:1px solid #bbb;border-radius:4px}
              button{background:#b71c1c;color:#fff;border:0;padding:8px 14px;border-radius:4px;cursor:pointer}
              button.small{padding:4px 8px} button.grey{background:#666}
              .msg{background:#e8f5e9;border-left:4px solid #2e7d32;padding:10px;margin:10px 0}
              .err{background:#ffebee;border-left-color:#c62828} .tag{padding:2px 8px;border-radius:10px;font-size:12px}
              .Pending{background:#fff3e0;color:#e65100} .Fulfilled{background:#e8f5e9;color:#2e7d32}
              .wrap{overflow-x:auto}
            </style></head><body>
            <nav><b>&#129656; Blood Bank</b>
              <a href='/'>Dashboard</a><a href='/donors'>Donors</a><a href='/donors/add'>Add Donor</a>
              <a href='/requests'>Requests</a><a href='/logout'>Logout</a></nav>
            <main>%s%s</main></body></html>
            """.formatted(esc(title), message, body);
    }

    static String groupOptions(String selected, boolean withAll) {
        StringBuilder sb = new StringBuilder();
        if (withAll) sb.append("<option value=''>All groups</option>");
        for (String g : Store.GROUPS)
            sb.append("<option").append(g.equals(selected) ? " selected" : "").append(">").append(g).append("</option>");
        return sb.toString();
    }

    static String login(String error) {
        String err = error == null ? "" : "<div class='msg err'>" + esc(error) + "</div>";
        return """
            <!DOCTYPE html><html><head><meta charset='UTF-8'><title>Login - Blood Bank</title>
            <meta name='viewport' content='width=device-width, initial-scale=1'>
            <style>body{font-family:Arial;background:#b71c1c;display:flex;justify-content:center;padding-top:80px}
            form{background:#fff;padding:30px;border-radius:8px;width:300px} h2{margin-top:0;color:#b71c1c}
            input{width:100%%;padding:9px;margin:6px 0 12px;box-sizing:border-box}
            button{width:100%%;background:#b71c1c;color:#fff;border:0;padding:10px;cursor:pointer}
            .msg{background:#ffebee;padding:8px;margin-bottom:10px}</style></head><body>
            <form method='post' action='/login'><h2>&#129656; Blood Bank Login</h2>%s
            <label>Username</label><input name='username' required>
            <label>Password</label><input type='password' name='password' required>
            <button>Login</button><p style='font-size:12px;color:#666'>Demo: admin / admin123</p></form></body></html>
            """.formatted(err);
    }

    static String dashboard() {
        StringBuilder cards = new StringBuilder("<div class='cards'>");
        int total = 0;
        for (Map.Entry<String, Integer> e : Store.stock().entrySet()) {
            total += e.getValue();
            cards.append("<div class='card").append(e.getValue() < 5 ? " low" : "").append("'><h2>")
                 .append(e.getKey()).append("</h2><p>").append(e.getValue()).append("</p><small>units</small></div>");
        }
        cards.append("</div>");
        long pending = Store.requests().stream().filter(r -> r.status().equals("Pending")).count();
        String body = "<h1>Dashboard</h1><p>Total donors: <b>" + Store.donors(null).size()
                + "</b> &nbsp; Total units in stock: <b>" + total + "</b> &nbsp; Pending requests: <b>" + pending + "</b></p>"
                + "<h3>Blood Stock</h3>" + cards + "<p><small>Groups with fewer than 5 units are shown in orange.</small></p>";
        return layout("Dashboard", null, body);
    }

    static String addDonorForm(String msg) {
        String body = """
            <h1>Add Donor</h1>
            <form class='box' method='post' action='/donors/add'>
              <input name='name' placeholder='Full name' required>
              <input name='age' type='number' placeholder='Age' min='18' max='65' required>
              <select name='group'>%s</select>
              <input name='phone' placeholder='Phone'>
              <input name='city' placeholder='City'>
              <button>Save Donor</button>
            </form>
            """.formatted(groupOptions(null, false));
        return layout("Add Donor", msg, body);
    }

    static String donors(String filter, String msg) {
        StringBuilder rows = new StringBuilder();
        List<Store.Donor> list = Store.donors(filter);
        for (Store.Donor d : list) {
            rows.append("<tr><td>").append(d.id()).append("</td><td>").append(esc(d.name())).append("</td><td>")
                .append(d.age()).append("</td><td><b>").append(d.group()).append("</b></td><td>")
                .append(esc(d.phone())).append("</td><td>").append(esc(d.city())).append("</td><td>")
                .append(d.lastDonation()).append("</td><td>")
                .append("<form method='post' action='/donate' style='display:inline'>")
                .append("<input type='hidden' name='id' value='").append(d.id()).append("'>")
                .append("<input name='units' type='number' value='1' min='1' max='5' style='width:55px'>")
                .append("<button class='small'>Donate</button></form> ")
                .append("<form method='post' action='/donors/delete' style='display:inline' onsubmit=\"return confirm('Delete this donor?')\">")
                .append("<input type='hidden' name='id' value='").append(d.id()).append("'>")
                .append("<button class='small grey'>Delete</button></form></td></tr>");
        }
        if (list.isEmpty()) rows.append("<tr><td colspan='8'>No donors found.</td></tr>");
        String body = """
            <h1>Donors</h1>
            <form class='box' method='get' action='/donors'>Search by blood group:
              <select name='group'>%s</select><button>Search</button></form>
            <div class='wrap'><table><tr><th>ID</th><th>Name</th><th>Age</th><th>Group</th><th>Phone</th>
            <th>City</th><th>Last Donation</th><th>Actions</th></tr>%s</table></div>
            """.formatted(groupOptions(filter, true), rows);
        return layout("Donors", msg, body);
    }

    static String requests(String msg) {
        StringBuilder rows = new StringBuilder();
        List<Store.Request> list = Store.requests();
        for (int i = list.size() - 1; i >= 0; i--) {
            Store.Request r = list.get(i);
            rows.append("<tr><td>").append(r.id()).append("</td><td>").append(esc(r.patient())).append("</td><td><b>")
                .append(r.group()).append("</b></td><td>").append(r.units()).append("</td><td>")
                .append(esc(r.hospital())).append("</td><td>").append(r.date())
                .append("</td><td><span class='tag ").append(r.status()).append("'>").append(r.status()).append("</span></td><td>");
            if (r.status().equals("Pending"))
                rows.append("<form method='post' action='/requests/fulfill'><input type='hidden' name='id' value='")
                    .append(r.id()).append("'><button class='small'>Try to fulfill</button></form>");
            rows.append("</td></tr>");
        }
        if (list.isEmpty()) rows.append("<tr><td colspan='8'>No requests yet.</td></tr>");
        String body = """
            <h1>Blood Requests</h1>
            <form class='box' method='post' action='/requests/add'>
              <input name='patient' placeholder='Patient name' required>
              <select name='group'>%s</select>
              <input name='units' type='number' min='1' max='10' value='1' style='width:70px' required>
              <input name='hospital' placeholder='Hospital'>
              <button>Request Blood</button>
            </form>
            <div class='wrap'><table><tr><th>ID</th><th>Patient</th><th>Group</th><th>Units</th><th>Hospital</th>
            <th>Date</th><th>Status</th><th></th></tr>%s</table></div>
            """.formatted(groupOptions(null, false), rows);
        return layout("Requests", msg, body);
    }
}
