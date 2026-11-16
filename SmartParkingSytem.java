import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;

/** SmartPark - Smart Parking Management System (Swing GUI + automatic text mode). Admin password: admin123 */
public class SmartParkingSystem extends JFrame {
    static final String ADMIN_PASSWORD = "admin123";
    static String cur = "\u20B9";                                  // currency sign ("Rs." in text mode)
    static final DateTimeFormatter IN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"), PRETTY = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"),
            HM = DateTimeFormatter.ofPattern("HH:mm"), DM = DateTimeFormatter.ofPattern("dd/MM HH:mm"), HMS = DateTimeFormatter.ofPattern("HH:mm:ss"),
            CLOCK = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy  HH:mm:ss");
    static final Color NAVY = new Color(22, 42, 74), BG = new Color(236, 242, 251), GREEN = new Color(46, 204, 113), ORANGE = new Color(255, 159, 28),
            RED = new Color(235, 77, 75), BLUE = new Color(52, 120, 210), PURPLE = new Color(142, 68, 173), TEAL = new Color(22, 160, 133);
    static final Color[] CARS = {new Color(231, 76, 60), new Color(52, 152, 219), new Color(241, 196, 15), new Color(155, 89, 182), new Color(26, 188, 156),
            new Color(230, 126, 34), new Color(236, 240, 241), new Color(233, 30, 99), new Color(0, 188, 212), new Color(139, 195, 74)};
    static final Font PLAIN = new Font("SansSerif", Font.PLAIN, 12), MONO = new Font("Monospaced", Font.BOLD, 16);
enum Status { AVAILABLE, RESERVED, OCCUPIED }

static class User {
    final String id, name, vehicle;
    User(String i, String n, String v) { id = i; name = n; vehicle = v; }
}

static class Space {
    final int number;
    boolean entered = false;                 // vehicle physically inside (set by admin)
    Status status = Status.AVAILABLE;
    Space(int n) { number = n; }
}

static class Booking {
    static int nextId = 1000;
    final int id = ++nextId;
    final User user; final Space space; final LocalDateTime start, end; final double rate;
    String state = "ACTIVE";                 // ACTIVE, COMPLETED, CANCELLED
    Booking(User u, Space s, LocalDateTime a, LocalDateTime b, double r) { user = u; space = s; start = a; end = b; rate = r; }
    boolean active() { return state.equals("ACTIVE"); }
    double fee() { return Math.max(1, Duration.between(start, end).toMinutes()) / 60.0 * rate; }
}
static class Lot {
    final List<Space> spaces = new ArrayList<>();
    final List<Booking> bookings = new ArrayList<>();
    final Map<String, User> users = new HashMap<>();
    Lot() { for (int i = 1; i <= 10; i++) spaces.add(new Space(i)); }

    // Earliest unfinished active booking of a slot
    Booking next(Space s, LocalDateTime now) {
        Booking best = null;
        for (Booking b : bookings)
            if (b.space == s && b.active() && b.end.isAfter(now) && (best == null || b.start.isBefore(best.start))) best = b;
        return best;
    }

    // Finish expired bookings (vehicle leaves) and recompute every slot's status
    void tick() {
        LocalDateTime now = LocalDateTime.now();
        for (Booking b : bookings) if (b.active() && !b.end.isAfter(now)) { b.state = "COMPLETED"; b.space.entered = false; }
        for (Space s : spaces) s.status = s.entered ? Status.OCCUPIED : next(s, now) != null ? Status.RESERVED : Status.AVAILABLE;
    }

    double occupancy() {
        int used = 0;
        for (Space s : spaces) if (s.status != Status.AVAILABLE) used++;
        return used * 100.0 / spaces.size();
    }

    // Dynamic pricing: rate rises with occupancy
    double rate() { double p = occupancy(); return p < 50 ? 20 : p < 80 ? 30 : 40; }

    // Booked windows of a slot, earliest first
    List<LocalDateTime[]> booked(Space s, LocalDateTime now) {
        List<LocalDateTime[]> list = new ArrayList<>();
        for (Booking b : bookings) if (b.space == s && b.active() && b.end.isAfter(now)) list.add(new LocalDateTime[]{b.start, b.end});
        list.sort((x, y) -> x[0].compareTo(y[0]));
        return list;
    }

    // Free gaps between booked windows; the last gap's end is null (open-ended)
    static List<LocalDateTime[]> free(List<LocalDateTime[]> booked, LocalDateTime now) {
        List<LocalDateTime[]> list = new ArrayList<>();
        LocalDateTime cursor = now.withSecond(0).withNano(0);
        for (LocalDateTime[] w : booked) {
            if (Duration.between(cursor, w[0]).toMinutes() >= 1) list.add(new LocalDateTime[]{cursor, w[0]});
            if (w[1].isAfter(cursor)) cursor = w[1];
        }
        list.add(new LocalDateTime[]{cursor, null});
        return list;
    }

    // Returns an error message, or null if the booking is allowed (overlap check)
    String check(Space sp, LocalDateTime s, LocalDateTime e) {
        if (!e.isAfter(s)) return "End time must be after start time.";
        if (s.isBefore(LocalDateTime.now().minusMinutes(1))) return "Start time cannot be in the past.";
        for (Booking b : bookings)
            if (b.active() && b.space == sp && s.isBefore(b.end) && e.isAfter(b.start))
                return "Slot " + sp.number + " is already booked during that time. Choose another time or slot.";
        return null;
    }

    Booking book(User u, Space sp, LocalDateTime s, LocalDateTime e) {
        Booking b = new Booking(u, sp, s, e, rate());
        bookings.add(b);
        return b;
    }

    List<Booking> of(User u, boolean activeOnly) {
        List<Booking> list = new ArrayList<>();
        for (Booking b : bookings) if (b.user == u && (!activeOnly || b.active())) list.add(b);
        return list;
    }
}
// Countdown text: "Starts ..." before the booking, "... left" during it
static String timerText(Space s, Booking b, LocalDateTime now, String starts) {
    if (b == null) return s.entered ? "--:--:--" : "FREE";
    return now.isBefore(b.start) ? starts + hms(Duration.between(now, b.start)) : hms(Duration.between(now, b.end)) + " left";
}

// Booking progress from 0.0 to 1.0, used for the progress bar
static double progress(Booking b, LocalDateTime now) {
    if (b == null || now.isBefore(b.start)) return 0;
    return Math.min(1.0, Duration.between(b.start, now).toMillis() / (double) Math.max(1, Duration.between(b.start, b.end).toMillis()));
}
private void bookSlot() {
    if (!needLogin()) return;
    // ... dialog setup omitted ...
    LocalDateTime s, e;
    try {
        s = LocalDateTime.parse(st.getText().trim(), IN);
        e = LocalDateTime.parse(en.getText().trim(), IN);
    } catch (DateTimeParseException ex) {
        error("Invalid date/time. Use the format yyyy-MM-dd HH:mm (e.g. 2026-11-03 18:30).");
        return;
    }
    Space space = lot.spaces.get(slot.getSelectedIndex());
    String err = lot.check(space, s, e);
    if (err != null) { error(err); return; }
    Booking b = lot.book(me, space, s, e);
    refresh();
    info("Booking confirmed!\n\n" + receipt(b));
}
private void move(Space s, boolean enter, Runnable reload) {
    if (s.entered == enter) {
        error("Slot " + s.number + (enter ? " is already occupied." : " has no vehicle inside."));
        return;
    }
    s.entered = enter;
    reload.run();
}
public static void main(String[] args) {
    if (GraphicsEnvironment.isHeadless() || (args.length > 0 && args[0].equalsIgnoreCase("console"))) {
        ConsoleApp.run();
        return;
    }
    try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) { }
    SwingUtilities.invokeLater(SmartParkingSystem::new);
}


