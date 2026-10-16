package bloodbank;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/** Holds all data in memory and saves it to simple text files in the "data" folder. */
public class Store {

    public record Donor(int id, String name, int age, String group, String phone, String city, String lastDonation) {}
    public record Request(int id, String patient, String group, int units, String hospital, String status, String date) {}

    public static final String[] GROUPS = {"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"};

    private static final List<Donor> donors = new ArrayList<>();
    private static final List<Request> requests = new ArrayList<>();
    private static final Map<String, Integer> stock = new LinkedHashMap<>();
    private static final Path DIR = Path.of("data");

    // ---------- loading and saving ----------

    public static synchronized void load() throws IOException {
        Files.createDirectories(DIR);
        for (String g : GROUPS) stock.put(g, 0);

        for (String line : read("stock.txt")) {
            String[] p = line.split("\\|", -1);
            stock.put(p[0], Integer.parseInt(p[1]));
        }
        for (String line : read("donors.txt")) {
            String[] p = line.split("\\|", -1);
            donors.add(new Donor(Integer.parseInt(p[0]), p[1], Integer.parseInt(p[2]), p[3], p[4], p[5], p[6]));
        }
        for (String line : read("requests.txt")) {
            String[] p = line.split("\\|", -1);
            requests.add(new Request(Integer.parseInt(p[0]), p[1], p[2], Integer.parseInt(p[3]), p[4], p[5], p[6]));
        }
    }

    private static List<String> read(String file) throws IOException {
        Path p = DIR.resolve(file);
        return Files.exists(p) ? Files.readAllLines(p) : List.of();
    }

    private static void save() {
        try {
            List<String> s = new ArrayList<>(), d = new ArrayList<>(), r = new ArrayList<>();
            stock.forEach((g, u) -> s.add(g + "|" + u));
            for (Donor x : donors)
                d.add(x.id() + "|" + x.name() + "|" + x.age() + "|" + x.group() + "|" + x.phone() + "|" + x.city() + "|" + x.lastDonation());
            for (Request x : requests)
                r.add(x.id() + "|" + x.patient() + "|" + x.group() + "|" + x.units() + "|" + x.hospital() + "|" + x.status() + "|" + x.date());
            Files.write(DIR.resolve("stock.txt"), s);
            Files.write(DIR.resolve("donors.txt"), d);
            Files.write(DIR.resolve("requests.txt"), r);
        } catch (IOException e) {
            System.out.println("Could not save data: " + e.getMessage());
        }
    }

    private static String clean(String s) {   // keeps the text files safe
        return s == null ? "" : s.replace("|", " ").replace("\n", " ").replace("\r", " ").trim();
    }

    // ---------- reading data ----------

    public static synchronized List<Donor> donors(String group) {
        List<Donor> out = new ArrayList<>();
        for (Donor d : donors) if (group == null || group.isEmpty() || d.group().equals(group)) out.add(d);
        return out;
    }

    public static synchronized List<Request> requests() { return new ArrayList<>(requests); }

    public static synchronized Map<String, Integer> stock() { return new LinkedHashMap<>(stock); }

    // ---------- actions (each returns a message to show the user) ----------

    public static synchronized String addDonor(String name, int age, String group, String phone, String city) {
        if (clean(name).isEmpty()) return "Name is required.";
        if (age < 18 || age > 65) return "Donor age must be between 18 and 65.";
        if (!Arrays.asList(GROUPS).contains(group)) return "Invalid blood group.";
        int id = donors.stream().mapToInt(Donor::id).max().orElse(0) + 1;
        donors.add(new Donor(id, clean(name), age, group, clean(phone), clean(city), "-"));
        save();
        return "Donor added.";
    }

    public static synchronized String deleteDonor(int id) {
        donors.removeIf(d -> d.id() == id);
        save();
        return "Donor deleted.";
    }

    public static synchronized String donate(int donorId, int units) {
        if (units < 1 || units > 5) return "Units must be between 1 and 5.";
        for (int i = 0; i < donors.size(); i++) {
            Donor d = donors.get(i);
            if (d.id() == donorId) {
                stock.merge(d.group(), units, Integer::sum);
                donors.set(i, new Donor(d.id(), d.name(), d.age(), d.group(), d.phone(), d.city(), LocalDate.now().toString()));
                save();
                return units + " unit(s) of " + d.group() + " added to stock. Thank you, " + d.name() + "!";
            }
        }
        return "Donor not found.";
    }

    public static synchronized String addRequest(String patient, String group, int units, String hospital) {
        if (clean(patient).isEmpty()) return "Patient name is required.";
        if (units < 1 || units > 10) return "Units must be between 1 and 10.";
        if (!Arrays.asList(GROUPS).contains(group)) return "Invalid blood group.";
        int id = requests.stream().mapToInt(Request::id).max().orElse(0) + 1;
        requests.add(new Request(id, clean(patient), group, units, clean(hospital), "Pending", LocalDate.now().toString()));
        save();
        return fulfill(id);   // try to serve it straight away
    }

    public static synchronized String fulfill(int id) {
        for (int i = 0; i < requests.size(); i++) {
            Request r = requests.get(i);
            if (r.id() == id && r.status().equals("Pending")) {
                if (stock.get(r.group()) >= r.units()) {
                    stock.merge(r.group(), -r.units(), Integer::sum);
                    requests.set(i, new Request(r.id(), r.patient(), r.group(), r.units(), r.hospital(), "Fulfilled", r.date()));
                    save();
                    return "Request #" + id + " fulfilled.";
                }
                save();
                return "Not enough " + r.group() + " blood in stock. Request #" + id + " is pending.";
            }
        }
        return "Request not found or already fulfilled.";
    }
}
