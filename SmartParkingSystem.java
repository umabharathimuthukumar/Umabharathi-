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

    // ===================== model =====================
    enum Status { AVAILABLE, RESERVED, OCCUPIED }
    static class User { final String id, name, vehicle; User(String i, String n, String v) { id = i; name = n; vehicle = v; } }
    static class Space { final int number; boolean entered = false; Status status = Status.AVAILABLE; Space(int n) { number = n; } }  // entered = vehicle inside (admin)

    static class Booking {
        static int nextId = 1000; final int id = ++nextId;
        final User user; final Space space; final LocalDateTime start, end; final double rate;
        String state = "ACTIVE";                                   // ACTIVE, COMPLETED, CANCELLED
        Booking(User u, Space s, LocalDateTime a, LocalDateTime b, double r) { user = u; space = s; start = a; end = b; rate = r; }
        boolean active() { return state.equals("ACTIVE"); }
        double fee() { return Math.max(1, Duration.between(start, end).toMinutes()) / 60.0 * rate; }
        String[] row() { return new String[]{"" + id, user.name, user.vehicle, "" + space.number, start.format(PRETTY), end.format(PRETTY), rs(rate, 0), rs(fee(), 2), state}; }
    }

    /** All parking data and rules - shared by the window and the text mode. */
    static class Lot {
        final List<Space> spaces = new ArrayList<>(); final List<Booking> bookings = new ArrayList<>();
        final Map<String, User> users = new HashMap<>();
        Lot() { for (int i = 1; i <= 10; i++) spaces.add(new Space(i)); }
        Booking next(Space s, LocalDateTime now) {                 // earliest unfinished active booking of a slot
            Booking best = null;
            for (Booking b : bookings)
                if (b.space == s && b.active() && b.end.isAfter(now) && (best == null || b.start.isBefore(best.start))) best = b;
            return best;
        }
        void tick() {                                              // finish old bookings (vehicle leaves), update statuses
            LocalDateTime now = LocalDateTime.now();
            for (Booking b : bookings) if (b.active() && !b.end.isAfter(now)) { b.state = "COMPLETED"; b.space.entered = false; }
            for (Space s : spaces) s.status = s.entered ? Status.OCCUPIED : next(s, now) != null ? Status.RESERVED : Status.AVAILABLE;
        }
        double occupancy() { int used = 0; for (Space s : spaces) if (s.status != Status.AVAILABLE) used++; return used * 100.0 / spaces.size(); }
        double rate() { double p = occupancy(); return p < 50 ? 20 : p < 80 ? 30 : 40; }   // dynamic pricing
        List<LocalDateTime[]> booked(Space s, LocalDateTime now) { // booked windows {start, end}, earliest first
            List<LocalDateTime[]> list = new ArrayList<>();
            for (Booking b : bookings) if (b.space == s && b.active() && b.end.isAfter(now)) list.add(new LocalDateTime[]{b.start, b.end});
            list.sort((x, y) -> x[0].compareTo(y[0]));
            return list;
        }
        static List<LocalDateTime[]> free(List<LocalDateTime[]> booked, LocalDateTime now) {   // gaps; the last "to" is null
            List<LocalDateTime[]> list = new ArrayList<>(); LocalDateTime cursor = now.withSecond(0).withNano(0);
            for (LocalDateTime[] w : booked) {
                if (Duration.between(cursor, w[0]).toMinutes() >= 1) list.add(new LocalDateTime[]{cursor, w[0]});
                if (w[1].isAfter(cursor)) cursor = w[1];
            }
            list.add(new LocalDateTime[]{cursor, null});
            return list;
        }
        String check(Space sp, LocalDateTime s, LocalDateTime e) { // error message, or null if the booking is allowed
            if (!e.isAfter(s)) return "End time must be after start time.";
            if (s.isBefore(LocalDateTime.now().minusMinutes(1))) return "Start time cannot be in the past.";
            for (Booking b : bookings)
                if (b.active() && b.space == sp && s.isBefore(b.end) && e.isAfter(b.start))
                    return "Slot " + sp.number + " is already booked during that time. Choose another time or slot.";
            return null;
        }
        Booking book(User u, Space sp, LocalDateTime s, LocalDateTime e) { Booking b = new Booking(u, sp, s, e, rate()); bookings.add(b); return b; }
        List<Booking> of(User u, boolean activeOnly) {
            List<Booking> list = new ArrayList<>();
            for (Booking b : bookings) if (b.user == u && (!activeOnly || b.active())) list.add(b);
            return list;
        }
    }
    // ---------- shared text helpers ----------
    static String rs(double v, int dec) { return cur + String.format("%." + dec + "f", v); }
    static String hms(Duration d) { long s = Math.max(0, d.getSeconds()); return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60); }
    static String timeOf(LocalDateTime t, LocalDateTime now) { return t.format(t.toLocalDate().equals(now.toLocalDate()) ? HM : DM); }
    static String span(LocalDateTime[] w, LocalDateTime now) {     // "14:00-15:30", "05/11 22:00-06/11 01:00", "15:30 onwards"
        if (w[1] == null) return timeOf(w[0], now) + " onwards";
        return timeOf(w[0], now) + "-" + (w[1].toLocalDate().equals(w[0].toLocalDate()) ? w[1].format(HM) : timeOf(w[1], now));
    }
    static String joinSpans(List<LocalDateTime[]> list, LocalDateTime now, String sep) {
        if (list.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        for (LocalDateTime[] w : list) sb.append(sb.length() > 0 ? sep : "").append(span(w, now));
        return sb.toString();
    }
    static String timerText(Space s, Booking b, LocalDateTime now, String starts) {
        if (b == null) return s.entered ? "--:--:--" : "FREE";
        return now.isBefore(b.start) ? starts + hms(Duration.between(now, b.start)) : hms(Duration.between(now, b.end)) + " left";
    }
    static double progress(Booking b, LocalDateTime now) {
        if (b == null || now.isBefore(b.start)) return 0;
        return Math.min(1.0, Duration.between(b.start, now).toMillis() / (double) Math.max(1, Duration.between(b.start, b.end).toMillis()));
    }
    static String receipt(Booking b) {
        return "Booking ID : " + b.id + "\nSlot       : " + b.space.number + "\nFrom       : " + b.start.format(PRETTY) + "\nTo         : " + b.end.format(PRETTY)
                + "\nRate       : " + rs(b.rate, 0) + " / hour\nTotal fee  : " + rs(b.fee(), 2);
    }

    // ===================== window state =====================
    private final Lot lot = new Lot(); private final List<SlotCard> cards = new ArrayList<>();
    private final JPanel grid = new JPanel(new GridLayout(2, 5, 14, 14));
    private final JLabel available = new JLabel(), reserved = new JLabel(), occupied = new JLabel(), occupancy = new JLabel(), price = new JLabel(),
            clock = new JLabel(), userLabel = new JLabel();
    private final Art gauge = new Art(null, new Dimension(210, 96), this::paintGauge);
    private double gTarget = 0, gShown = 0, gRate = 20;            // gauge: target %, animated %, hourly rate
    private Art header; private User me = null;
    private int phase = 0;                                         // animation frame counter

    public SmartParkingSystem() {
        super("SmartPark - Smart Parking Management System"); buildUI(); refresh();
        new javax.swing.Timer(1000, e -> refresh()).start();       // logic: once per second
        new javax.swing.Timer(40, e -> {                           // animation: ~25 fps
            phase++; gShown += (gTarget - gShown) * 0.12;
            if (Math.abs(gTarget - gShown) < 0.05) gShown = gTarget;
            gauge.repaint(); header.repaint();
            for (SlotCard c : cards) c.repaint();
        }).start();
        setDefaultCloseOperation(EXIT_ON_CLOSE); setSize(1200, 940); setMinimumSize(new Dimension(1050, 860));
        setLocationRelativeTo(null); setVisible(true);
    }

    // ===================== UI helpers =====================
    static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a))); }
    static Color statusColor(Status s) { return s == Status.AVAILABLE ? GREEN : s == Status.RESERVED ? ORANGE : RED; }
    static Font bold(int n) { return new Font("SansSerif", Font.BOLD, n); }
    static JPanel clear(LayoutManager lm) { JPanel p = new JPanel(lm); p.setOpaque(false); return p; }
    static JPanel column() { JPanel p = clear(null); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); return p; }
    static JLabel label(String t, Color c, Font f) { JLabel l = new JLabel(t); l.setForeground(c); l.setFont(f); return l; }
    static JPanel form(Object... kv) {                             // label text, component, label text, component ...
        JPanel p = new JPanel(new GridLayout(0, 2, 8, 8));
        for (int i = 0; i < kv.length; i += 2) { p.add(new JLabel((String) kv[i])); p.add((Component) kv[i + 1]); }
        return p;
    }
    static JButton button(String text, Color c, ActionListener a) {
        JButton b = new JButton(text); b.setBackground(c); b.setForeground(Color.WHITE); b.setFont(bold(14)); b.setOpaque(true);
        b.setBorderPainted(false); b.setFocusPainted(false); b.setPreferredSize(new Dimension(150, 42));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); b.addActionListener(a);
        return b;
    }
    private JPanel stat(String title, JLabel value, Color c) {
        JPanel p = new JPanel(new BorderLayout(0, 4)); p.setBackground(Color.WHITE);
        p.setBorder(new CompoundBorder(new MatteBorder(0, 6, 0, 0, c), new EmptyBorder(12, 16, 12, 16))); value.setFont(bold(34));
        value.setForeground(c); p.add(label(title, Color.GRAY, bold(12)), BorderLayout.NORTH); p.add(value, BorderLayout.CENTER);
        return p;
    }

    /** A panel drawn by a painter function (header, asphalt, legend, gauge, icons). */
    static class Art extends JPanel {
        final BiConsumer<Graphics2D, Dimension> painter;
        Art(LayoutManager lm, Dimension pref, BiConsumer<Graphics2D, Dimension> p) { super(lm); painter = p; setOpaque(false); if (pref != null) setPreferredSize(pref); }
        @Override protected void paintComponent(Graphics g0) { Graphics2D g = (Graphics2D) g0.create(); aa(g); painter.accept(g, getSize()); g.dispose(); }
    }
    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(16, 16)); root.setBackground(BG); root.setBorder(new EmptyBorder(18, 22, 18, 22));
        setContentPane(root);
        // header: gradient + road + driving car; brand on the left, clock and user on the right
        header = new Art(new BorderLayout(), null, this::paintHeader); header.setBorder(new EmptyBorder(14, 22, 44, 22));
        JPanel left = clear(new FlowLayout(FlowLayout.LEFT, 14, 0)), brand = column(), right = column();
        left.add(new Art(null, new Dimension(92, 52), (g, d) -> drawCar(g, 4, 2, 84, 48, new Color(255, 214, 10))));
        brand.add(label("SMARTPARK", Color.WHITE, bold(28))); brand.add(Box.createVerticalStrut(4));
        brand.add(label("SMART PARKING MANAGEMENT SYSTEM", new Color(200, 218, 240), PLAIN)); left.add(brand);
        clock.setForeground(Color.WHITE); clock.setFont(new Font("Monospaced", Font.BOLD, 15));
        userLabel.setForeground(new Color(255, 220, 120)); userLabel.setFont(bold(13));
        clock.setAlignmentX(Component.RIGHT_ALIGNMENT); userLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        right.add(clock); right.add(Box.createVerticalStrut(6)); right.add(userLabel); header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST); root.add(header, BorderLayout.NORTH);
        // stat tiles + gauge
        JPanel stats = clear(new GridLayout(1, 5, 12, 0)), gaugePanel = new JPanel(new BorderLayout());
        stats.add(stat("TOTAL SLOTS", new JLabel("10"), BLUE)); stats.add(stat("AVAILABLE", available, GREEN));
        stats.add(stat("RESERVED", reserved, ORANGE)); stats.add(stat("OCCUPIED", occupied, RED));
        gaugePanel.setBackground(Color.WHITE);
        gaugePanel.setBorder(new CompoundBorder(new MatteBorder(0, 6, 0, 0, PURPLE), new EmptyBorder(4, 8, 4, 8)));
        gaugePanel.add(gauge, BorderLayout.CENTER); stats.add(gaugePanel);
        // asphalt parking lot: heading with legend + the slot cards
        Art parking = new Art(new BorderLayout(10, 12), null, SmartParkingSystem::paintAsphalt);
        parking.setBorder(new EmptyBorder(17, 18, 18, 18));
        JPanel heading = clear(new BorderLayout()), legendWrap = clear(new FlowLayout(FlowLayout.CENTER, 0, 0));
        legendWrap.add(new Art(null, new Dimension(330, 26), SmartParkingSystem::paintLegend));
        heading.add(label("PARKING AREA", Color.WHITE, bold(19)), BorderLayout.WEST); heading.add(legendWrap, BorderLayout.CENTER);
        heading.add(label("Click a parking card to see booking details", new Color(150, 162, 185), PLAIN), BorderLayout.EAST);
        grid.setOpaque(false);
        for (Space s : lot.spaces) { SlotCard c = new SlotCard(s); cards.add(c); grid.add(c); }
        parking.add(heading, BorderLayout.NORTH); parking.add(grid, BorderLayout.CENTER);
        // bottom: occupancy / rate line + buttons
        occupancy.setFont(bold(14)); occupancy.setForeground(NAVY); price.setFont(bold(14)); price.setForeground(BLUE);
        JPanel pricing = clear(new BorderLayout()), buttons = clear(new FlowLayout(FlowLayout.CENTER, 10, 4)), south = column();
        pricing.add(occupancy, BorderLayout.WEST); pricing.add(price, BorderLayout.EAST);
        buttons.add(button("Register", TEAL, e -> registerUser())); buttons.add(button("Login", BLUE, e -> loginUser()));
        buttons.add(button("Book Slot", GREEN.darker(), e -> bookSlot()));
        buttons.add(button("Cancel Booking", ORANGE.darker(), e -> cancelBooking()));
        buttons.add(button("My Bookings", PURPLE, e -> myBookings())); buttons.add(button("Admin", RED.darker(), e -> adminPanel()));
        south.add(pricing); south.add(Box.createVerticalStrut(8)); south.add(buttons); JPanel middle = clear(new BorderLayout(12, 14));
        middle.add(stats, BorderLayout.NORTH); middle.add(parking, BorderLayout.CENTER); middle.add(south, BorderLayout.SOUTH);
        root.add(middle, BorderLayout.CENTER);
    }

    // ===================== custom drawing =====================
    static void aa(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
    static RoundRectangle2D rr(double x, double y, double w, double h, double r) { return new RoundRectangle2D.Double(x, y, w, h, r, r); }
    static void fill(Graphics2D g, Color c, Shape s) { g.setColor(c); g.fill(s); }
    static void text(Graphics2D g, Font f, Color c, String s, int x, int y) { g.setFont(f); g.setColor(c); g.drawString(s, x, y); }
    static void ctext(Graphics2D g, Font f, Color c, String s, int w, int y) { g.setFont(f); g.setColor(c); g.drawString(s, (w - g.getFontMetrics().stringWidth(s)) / 2, y); }
    static void drawCar(Graphics2D g, int x, int y, int w, int h, Color body) {
        fill(g, body, rr(x + w * 0.22, y, w * 0.56, h * 0.55, h * 0.40));                         // cabin
        g.fill(rr(x, y + h * 0.35, w, h * 0.42, h * 0.30));                                        // body
        fill(g, new Color(120, 190, 235), rr(x + w * 0.29, y + h * 0.09, w * 0.19, h * 0.28, 6));  // windows
        g.fill(rr(x + w * 0.52, y + h * 0.09, w * 0.19, h * 0.28, 6)); double d = h * 0.34, wy = y + h * 0.60;
        for (double fx : new double[]{0.14, 0.62}) {                                               // wheels
            fill(g, new Color(30, 40, 60), new Ellipse2D.Double(x + w * fx, wy, d, d));
            fill(g, Color.LIGHT_GRAY, new Ellipse2D.Double(x + w * fx + d / 4, wy + d / 4, d / 2, d / 2));
        }
        fill(g, new Color(255, 214, 10), new Ellipse2D.Double(x + w * 0.90, y + h * 0.45, w * 0.08, h * 0.14));   // headlight
        fill(g, new Color(255, 90, 90), new Ellipse2D.Double(x + w * 0.02, y + h * 0.45, w * 0.06, h * 0.14));    // tail light
    }
    private void paintHeader(Graphics2D g, Dimension d) {          // gradient, road strip, car that keeps driving
        g.setPaint(new GradientPaint(0, 0, NAVY, d.width, 0, BLUE)); g.fillRect(0, 0, d.width, d.height);
        fill(g, new Color(30, 34, 44), new Rectangle(0, d.height - 32, d.width, 32)); g.setColor(new Color(255, 214, 10));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{22f, 16f}, 0f));
        g.drawLine(0, d.height - 16, d.width, d.height - 16);
        drawCar(g, (phase * 3) % (d.width + 200) - 100, d.height - 38, 66, 33, new Color(255, 120, 60));
    }
    static void paintAsphalt(Graphics2D g, Dimension d) {
        g.setPaint(new GradientPaint(0, 0, new Color(34, 39, 52), 0, d.height, new Color(20, 23, 32)));
        g.fill(rr(0, 0, d.width - 1, d.height - 1, 24));
    }
    static void paintLegend(Graphics2D g, Dimension d) {
        String[] names = {"Available", "Reserved", "Occupied"}; Color[] cols = {GREEN, ORANGE, RED}; g.setFont(bold(12));
        for (int i = 0, x = 4; i < 3; i++) {
            fill(g, alpha(cols[i], 90), new Ellipse2D.Double(x - 3, 4, 18, 18)); fill(g, cols[i], new Ellipse2D.Double(x, 7, 12, 12));
            text(g, bold(12), new Color(210, 220, 236), names[i], x + 20, 18); x += 20 + g.getFontMetrics().stringWidth(names[i]) + 22;
        }
    }
    private void paintGauge(Graphics2D g, Dimension d) {           // animated semicircle occupancy gauge
        int w = d.width, h = d.height, r = Math.min(w / 2 - 14, h - 34), cx = w / 2, cy = h - 20;
        g.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.setColor(new Color(225, 232, 242));
        g.draw(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 180, -180, Arc2D.OPEN));
        g.setColor(gShown < 50 ? GREEN : gShown < 80 ? ORANGE : RED);
        if (gShown > 0.3) g.draw(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 180, -180 * gShown / 100.0, Arc2D.OPEN));
        ctext(g, bold(22), NAVY, String.format("%.0f%%", gShown), w, cy - 4);
        ctext(g, bold(11), Color.GRAY, "OCCUPANCY  |  " + rs(gRate, 0) + "/hr", w, h - 3);
    }

    /** One slot card: glowing border, coloured car, live timer, progress bar, BOOKED / FREE timings. */
    class SlotCard extends JPanel {
        final Space s; Booking b; String timer = "";
        double progress = 0; boolean warn = false, hover = false;
        List<LocalDateTime[]> booked = new ArrayList<>(), free = new ArrayList<>(); LocalDateTime now = LocalDateTime.now();
        SlotCard(Space s) {
            this.s = s; setOpaque(false); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(180, 242));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { showDetails(SlotCard.this.s); }
                @Override public void mouseEntered(MouseEvent e) { hover = true; }
                @Override public void mouseExited(MouseEvent e) { hover = false; }
            });
        }
        void update(LocalDateTime now) {                           // recompute live data (once per second)
            this.now = now;
            b = lot.next(s, now); timer = timerText(s, b, now, "Starts "); progress = SmartParkingSystem.progress(b, now);
            warn = b != null && !now.isBefore(b.start) && Duration.between(now, b.end).getSeconds() <= 300;
            booked = lot.booked(s, now); free = Lot.free(booked, now);
        }
        private int drawWindows(Graphics2D g, String label, Color col, List<LocalDateTime[]> list, int x, int y) {   // returns next y
            text(g, bold(11), col, label, x, y); int ix = x + g.getFontMetrics().stringWidth(label + " ");
            g.setColor(new Color(225, 232, 245));
            if (list.isEmpty()) { g.drawString("none", ix, y); return y + 14; }
            for (int i = 0; i < Math.min(2, list.size()); i++, y += 14)
                g.drawString(i == 1 && list.size() > 2 ? "+" + (list.size() - 1) + " more (click card)" : span(list.get(i), now), ix, y);
            return y;
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); aa(g); int w = getWidth(), h = getHeight();
            Color c = statusColor(s.status); RoundRectangle2D card = rr(5, 5, w - 11, h - 11, 26);
            fill(g, new Color(0, 0, 0, 80), rr(7, 9, w - 11, h - 11, 26));                                  // shadow
            g.setPaint(new GradientPaint(0, 0, new Color(64, 72, 90), 0, h, new Color(40, 45, 58))); g.fill(card);   // dark base
            g.setPaint(new GradientPaint(0, 0, alpha(c, 95), 0, h, alpha(c, 18))); g.fill(card);                      // status tint
            if (hover) fill(g, new Color(255, 255, 255, 30), card);
            double pulse = warn ? 0.5 + 0.5 * Math.sin(phase * 0.28) : 1.0;                                 // glowing border (pulses when time is almost over)
            g.setStroke(new BasicStroke(9f)); g.setColor(alpha(c, (int) (60 * pulse))); g.draw(card);
            g.setStroke(new BasicStroke(3f)); g.setColor(alpha(c, (int) (110 + 145 * pulse))); g.draw(card);
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));                // bay lines
            g.setColor(new Color(255, 255, 255, 120)); g.drawLine(18, 46, 18, 112); g.drawLine(w - 18, 46, w - 18, 112);
            g.setStroke(new BasicStroke(1f));
            fill(g, c, rr(14, 12, 86, 22, 22));                                                              // slot pill
            text(g, bold(12), Color.WHITE, "SLOT " + String.format("%02d", s.number), 28, 28);
            if (s.status == Status.AVAILABLE) {                                                              // "P" sign
                fill(g, new Color(0, 0, 0, 60), rr(w / 2.0 - 26, 52, 56, 56, 16));
                fill(g, new Color(33, 110, 220), rr(w / 2.0 - 28, 48, 56, 56, 16));
                g.setColor(Color.WHITE); g.setStroke(new BasicStroke(2f)); g.draw(rr(w / 2.0 - 24, 52, 48, 48, 12));
                ctext(g, bold(38), Color.WHITE, "P", w, 90);
            } else {                                                                                         // coloured car (bobs when occupied)
                fill(g, new Color(0, 0, 0, 90), new Ellipse2D.Double(w / 2.0 - 44, 98, 88, 11));
                drawCar(g, w / 2 - 46, (int) (46 + (s.status == Status.OCCUPIED ? Math.sin(phase * 0.3) * 1.3 : 0)), 92, 54, CARS[(s.number - 1) % CARS.length]);
            }
            ctext(g, bold(14), c.brighter(), s.status.name(), w, 124);                                       // status + timer + progress bar
            ctext(g, MONO, warn ? new Color(255, 190, 190) : Color.WHITE, timer, w, 144);
            fill(g, new Color(255, 255, 255, 45), rr(22, 152, w - 44, 7, 7));
            if (b != null && progress > 0) fill(g, warn ? RED.brighter() : c.brighter(), rr(22, 152, Math.max(7, (w - 44) * progress), 7, 7));
            g.setColor(new Color(255, 255, 255, 50)); g.drawLine(16, 168, w - 16, 168);                      // BOOKED / FREE timings
            drawWindows(g, "FREE", GREEN, free, 16, drawWindows(g, "BOOKED", ORANGE, booked, 16, 184) + 6); g.dispose();
        }
    }

    // ===================== refresh =====================
    private void refresh() {
        lot.tick(); LocalDateTime now = LocalDateTime.now();
        int[] n = new int[3];                                      // AVAILABLE, RESERVED, OCCUPIED
        for (SlotCard c : cards) { c.update(now); n[c.s.status.ordinal()]++; }
        available.setText("" + n[0]); reserved.setText("" + n[1]); occupied.setText("" + n[2]);
        gTarget = lot.occupancy(); gRate = lot.rate(); occupancy.setText(String.format("Occupancy: %.0f%%", gTarget));
        price.setText("Current rate: " + rs(gRate, 0) + " / hour"); clock.setText(now.format(CLOCK));
        userLabel.setText(me == null ? "Guest - please register / login" : "Logged in: " + me.name + " (" + me.vehicle + ")");
    }

    // ===================== user actions =====================
    private boolean confirm(JComponent c, String title) {
        return JOptionPane.showConfirmDialog(this, c, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }
    private boolean needLogin() { if (me == null) error("Please register or login first."); return me != null; }
    private void info(String m) { JOptionPane.showMessageDialog(this, m, "SmartPark", JOptionPane.INFORMATION_MESSAGE); }
    private void error(String m) { JOptionPane.showMessageDialog(this, m, "SmartPark", JOptionPane.ERROR_MESSAGE); }
    private void registerUser() {
        JTextField id = new JTextField(), name = new JTextField(), veh = new JTextField();
        if (!confirm(form("User ID:", id, "Name:", name, "Vehicle number:", veh), "Register")) return;
        String i = id.getText().trim(), n = name.getText().trim(), v = veh.getText().trim();
        if (i.isEmpty() || n.isEmpty() || v.isEmpty()) { error("All fields are required."); return; }
        if (lot.users.containsKey(i)) { error("User ID already exists. Please login instead."); return; }
        lot.users.put(i, me = new User(i, n, v.toUpperCase())); refresh(); info("Registration successful. Welcome, " + n + "!");
    }
    private void loginUser() {
        String i = JOptionPane.showInputDialog(this, "Enter your User ID:", "Login", JOptionPane.PLAIN_MESSAGE);
        if (i == null) return;
        User u = lot.users.get(i.trim());
        if (u == null) { error("User not found. Please register first."); return; }
        me = u; refresh(); info("Login successful. Welcome back, " + u.name + "!");
    }
    private void bookSlot() {
        if (!needLogin()) return;
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0); JComboBox<String> slot = new JComboBox<>();
        for (Space s : lot.spaces) slot.addItem("Slot " + s.number);
        JTextField st = new JTextField(now.format(IN)), en = new JTextField(now.plusHours(1).format(IN));
        JPanel p = form("Parking slot:", slot, "Start (yyyy-MM-dd HH:mm):", st, "End (yyyy-MM-dd HH:mm):", en, "Rate right now:", new JLabel(rs(lot.rate(), 0) + " / hour"));
        while (confirm(p, "Book a Parking Slot")) {
            LocalDateTime s, e;
            try { s = LocalDateTime.parse(st.getText().trim(), IN); e = LocalDateTime.parse(en.getText().trim(), IN); }
            catch (DateTimeParseException ex) { error("Invalid date/time. Use the format yyyy-MM-dd HH:mm (e.g. 2026-11-03 18:30)."); continue; }
            Space space = lot.spaces.get(slot.getSelectedIndex()); String err = lot.check(space, s, e);
            if (err != null) { error(err); continue; }
            Booking b = lot.book(me, space, s, e); refresh(); info("Booking confirmed!\n\n" + receipt(b));
            return;
        }
    }
    private void cancelBooking() {
        if (!needLogin()) return;
        List<Booking> mine = lot.of(me, true);
        if (mine.isEmpty()) { info("You have no active bookings to cancel."); return; }
        JComboBox<String> box = new JComboBox<>();
        for (Booking b : mine) box.addItem("ID " + b.id + " | Slot " + b.space.number + " | " + b.start.format(PRETTY) + " > " + b.end.format(PRETTY));
        if (!confirm(box, "Cancel Booking")) return;
        Booking b = mine.get(box.getSelectedIndex()); b.state = "CANCELLED"; refresh();
        info("Booking " + b.id + " has been cancelled.");
    }
    private void myBookings() {
        if (!needLogin()) return;
        JScrollPane sp = new JScrollPane(newTable(lot.of(me, false))); sp.setPreferredSize(new Dimension(820, 300));
        JOptionPane.showMessageDialog(this, sp, "My Bookings", JOptionPane.PLAIN_MESSAGE);
    }
    private void showDetails(Space s) {
        LocalDateTime now = LocalDateTime.now(); Booking b = lot.next(s, now);
        StringBuilder sb = new StringBuilder("Slot " + s.number + "  -  " + s.status + "\n\n");
        if (b == null) sb.append(s.entered ? "A vehicle is parked here (no active booking).\n" : "This slot is free.\nCurrent rate: " + rs(lot.rate(), 0) + " / hour");
        else sb.append("Booking ID: ").append(b.id).append("\nUser: ").append(b.user.name).append("\nVehicle: ").append(b.user.vehicle)
               .append("\nStart: ").append(b.start.format(PRETTY)).append("\nEnd: ").append(b.end.format(PRETTY))
               .append("\nRate: ").append(rs(b.rate, 0)).append(" / hour\nFee: ").append(rs(b.fee(), 2))
               .append(now.isBefore(b.start) ? "\n\nStarts in: " + hms(Duration.between(now, b.start)) : "\n\nTime left: " + hms(Duration.between(now, b.end)));
        List<LocalDateTime[]> booked = lot.booked(s, now);                                      // all booked / free timings of the slot
        sb.append("\n\n--- Timings ---\nBooked:").append(booked.isEmpty() ? " none" : "\n   " + joinSpans(booked, now, "\n   "))
          .append("\nFree:\n   ").append(joinSpans(Lot.free(booked, now), now, "\n   "));
        JOptionPane.showMessageDialog(this, sb.toString(), "Slot " + s.number + " Details", JOptionPane.INFORMATION_MESSAGE);
    }

    // ===================== admin =====================
    private void adminPanel() {
        JPasswordField pw = new JPasswordField();
        if (!confirm(pw, "Admin password")) return;
        if (!new String(pw.getPassword()).equals(ADMIN_PASSWORD)) { error("Wrong password."); return; }
        JDialog d = new JDialog(this, "Administrator Panel", true); JTable table = newTable(lot.bookings);
        JComboBox<String> slot = new JComboBox<>();
        for (Space s : lot.spaces) slot.addItem("Slot " + s.number);
        Runnable reload = () -> { table.setModel(newTable(lot.bookings).getModel()); refresh(); };
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10)); bottom.add(new JLabel("Select slot:"));
        bottom.add(slot);
        bottom.add(button("Vehicle Entry", GREEN.darker(), e -> move(lot.spaces.get(slot.getSelectedIndex()), true, reload)));
        bottom.add(button("Vehicle Exit", ORANGE.darker(), e -> move(lot.spaces.get(slot.getSelectedIndex()), false, reload)));
        bottom.add(button("Close", NAVY, e -> d.dispose()));
        JLabel title = label("  All Reservations  |  Total slots: " + lot.spaces.size(), Color.BLACK, bold(16));
        title.setBorder(new EmptyBorder(10, 10, 6, 10)); d.setLayout(new BorderLayout()); d.add(title, BorderLayout.NORTH);
        d.add(new JScrollPane(table), BorderLayout.CENTER); d.add(bottom, BorderLayout.SOUTH); d.setSize(950, 520);
        d.setLocationRelativeTo(this); d.setVisible(true);
    }
    private void move(Space s, boolean enter, Runnable reload) {   // admin: vehicle enters / leaves a slot
        if (s.entered == enter) { error("Slot " + s.number + (enter ? " is already occupied." : " has no vehicle inside.")); return; }
        s.entered = enter; reload.run();
    }
    private JTable newTable(List<Booking> list) {
        DefaultTableModel m = new DefaultTableModel(new String[]{"Booking ID", "User", "Vehicle", "Slot", "Start", "End", "Rate", "Fee", "Status"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (Booking b : list) m.addRow(b.row());
        JTable t = new JTable(m); t.setRowHeight(26);
        t.getTableHeader().setBackground(NAVY); t.getTableHeader().setForeground(Color.WHITE); t.getTableHeader().setFont(bold(13));
        return t;
    }

    public static void main(String[] args) {
        // No screen (online compiler, server, SSH)?  Use the text version instead of crashing.
        if (GraphicsEnvironment.isHeadless() || (args.length > 0 && args[0].equalsIgnoreCase("console"))) { ConsoleApp.run(); return; }
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) { }
        SwingUtilities.invokeLater(SmartParkingSystem::new);
    }

    // ===================== CONSOLE MODE (auto-starts when no display is available) =====================
    static class ConsoleApp {
        static final int COLS = 5;                                 // cards per row (use 2 for narrow screens)
        static final boolean COLOR = false;                        // true = ANSI colours (real terminals only)
        static final String ROW = "%-6s %-10s %-12s %-5s %-17s %-17s %-7s %-10s %s";
        final Lot lot = new Lot(); Scanner in; User current = null;
        boolean eof = false;
        static void run() { cur = "Rs."; new ConsoleApp().start(); }
        static void say(String s) { System.out.println(s); }
        String rep(char c, int n) { return new String(new char[Math.max(0, n)]).replace('\0', c); }
        String center(String s, int w) { if (s.length() >= w) return s.substring(0, w); int l = (w - s.length()) / 2; return rep(' ', l) + s + rep(' ', w - s.length() - l); }
        String bar(Booking b, LocalDateTime now, int len) { int f = (int) Math.round(progress(b, now) * len); return b == null ? rep(' ', len + 2) : "[" + rep('#', f) + rep('-', len - f) + "]"; }
        String ask(String prompt) {
            System.out.print(prompt); System.out.flush();
            if (!in.hasNextLine()) { eof = true; return ""; }
            return in.nextLine().trim();
        }
        String[] card(Space s, LocalDateTime now) {                // 11 text lines per slot card
            Booking b = lot.next(s, now); int w = 14; boolean free = s.status == Status.AVAILABLE;
            String tag = "[" + s.status.name() + "]", code = free ? "32" : s.status == Status.RESERVED ? "33" : "31";
            String times = free ? "Ready to book" : b != null ? b.start.format(HM) + " > " + b.end.format(HM) : "Vehicle parked";
            List<LocalDateTime[]> bk = lot.booked(s, now), fr = Lot.free(bk, now);                      // first booked / free window, "+" = more
            String bookedLine = bk.isEmpty() ? "Booked: none" : "B " + bk.get(0)[0].format(HM) + ">" + bk.get(0)[1].format(HM) + (bk.size() > 1 ? "+" : "");
            LocalDateTime[] f0 = fr.get(0);
            String freeLine = f0[1] == null ? "F " + f0[0].format(HM) + " >" : "F " + f0[0].format(HM) + ">" + f0[1].format(HM) + (fr.size() > 1 ? "+" : "");
            String[] mid = {String.format("%-" + w + "s", " SLOT " + String.format("%02d", s.number)), center(free ? "+-----+" : "  __/==\\__  ", w),
                    center(free ? "|  P  |" : " (_o----o_) ", w), COLOR ? "\u001B[" + code + "m" + center(tag, w) + "\u001B[0m" : center(tag, w),
                    center(timerText(s, b, now, "in "), w), center(times, w), center(bar(b, now, 10), w), center(bookedLine, w), center(freeLine, w)};
            String[] c = new String[11]; c[0] = c[10] = "+" + rep('-', w) + "+";
            for (int i = 0; i < mid.length; i++) c[i + 1] = "|" + mid[i] + "|";
            return c;
        }
        void printLot() {
            lot.tick(); LocalDateTime now = LocalDateTime.now(); int[] n = new int[3];
            for (Space s : lot.spaces) n[s.status.ordinal()]++;
            int pct = (int) Math.round(lot.occupancy());
            say("\n=============== SMARTPARK - PARKING AREA ===============\nTime: " + now.format(CLOCK));
            say("Total: " + lot.spaces.size() + "   Available: " + n[0] + "   Reserved: " + n[1] + "   Occupied: " + n[2]);
            say("Occupancy: [" + rep('#', pct / 10) + rep('-', 10 - pct / 10) + "] " + pct + "%    Current rate: " + rs(lot.rate(), 0) + " / hour");
            say("User: " + (current == null ? "Guest (register / login first)" : current.name + " (" + current.vehicle + ")") + "\n");
            for (int start = 0; start < lot.spaces.size(); start += COLS) {
                List<String[]> row = new ArrayList<>();
                for (int i = start; i < Math.min(start + COLS, lot.spaces.size()); i++) row.add(card(lot.spaces.get(i), now));
                for (int line = 0; line < 11; line++) {
                    StringBuilder sb = new StringBuilder();
                    for (String[] cd : row) sb.append(cd[line]).append(' ');
                    say(sb.toString());
                }
            }
            say("\n---------- SLOT TIMINGS (booked / free) ----------");
            for (Space s : lot.spaces) {
                List<LocalDateTime[]> bk = lot.booked(s, now);
                say(String.format("Slot %02d  BOOKED: %s", s.number, joinSpans(bk, now, ";  ")) + "\n         FREE  : " + joinSpans(Lot.free(bk, now), now, ";  "));
            }
        }
        void live() {
            say("\nLive timers for 10 seconds...");
            for (int i = 0; i < 10; i++) {
                lot.tick(); LocalDateTime now = LocalDateTime.now(); say("---- " + now.format(HMS) + " ----");
                for (Space s : lot.spaces) {
                    Booking b = lot.next(s, now);
                    say(String.format("Slot %02d  %-9s  %-14s %s", s.number, s.status, timerText(s, b, now, "in "), bar(b, now, 10)));
                }
                try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
        }
        void printBookings(List<Booking> list) {
            if (list.isEmpty()) { say("No bookings."); return; }
            say(String.format(ROW, "ID", "User", "Vehicle", "Slot", "Start", "End", "Rate", "Fee", "Status"));
            for (Booking b : list) say(String.format(ROW, (Object[]) b.row()));
        }
        boolean needLogin() { if (current == null) say("Please register or login first (options 1 / 2)."); return current != null; }
        int askSlot(String prompt) {                               // returns 1..10, or -1 if invalid
            try { int n = Integer.parseInt(ask(prompt)); if (n >= 1 && n <= lot.spaces.size()) return n; } catch (NumberFormatException ignored) { }
            say("Invalid slot number (1-" + lot.spaces.size() + ").");
            return -1;
        }
        void register() {
            String id = ask("User ID: "), name = ask("Name: "), veh = ask("Vehicle number: ");
            if (id.isEmpty() || name.isEmpty() || veh.isEmpty()) { say("All fields are required."); return; }
            if (lot.users.containsKey(id)) { say("User ID already exists. Please login."); return; }
            lot.users.put(id, current = new User(id, name, veh.toUpperCase())); say("Registration successful. Welcome, " + name + "!");
        }
        void login() {
            User u = lot.users.get(ask("User ID: "));
            if (u == null) { say("User not found. Please register first."); return; }
            current = u; say("Login successful. Welcome back, " + u.name + "!");
        }
        void book() {
            if (!needLogin()) return;
            lot.tick(); int n = askSlot("Slot number (1-" + lot.spaces.size() + "): ");
            if (n < 0) return;
            LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0), start, end;
            try {
                String st = ask("Start (yyyy-MM-dd HH:mm, ENTER = now): "); start = st.isEmpty() ? now : LocalDateTime.parse(st, IN);
                String en = ask("End (yyyy-MM-dd HH:mm) OR duration in minutes: ");
                end = en.contains(":") ? LocalDateTime.parse(en, IN) : start.plusMinutes(Long.parseLong(en));
            } catch (DateTimeParseException | NumberFormatException e) { say("Invalid date/time or number. Use yyyy-MM-dd HH:mm (e.g. 2026-11-03 18:30)."); return; }
            Space space = lot.spaces.get(n - 1); String err = lot.check(space, start, end);
            if (err != null) { say(err); return; }
            Booking b = lot.book(current, space, start, end); lot.tick(); say("\nBooking confirmed!\n" + receipt(b));
        }
        void cancel() {
            if (!needLogin()) return;
            List<Booking> mine = lot.of(current, true);
            if (mine.isEmpty()) { say("You have no active bookings to cancel."); return; }
            printBookings(mine);
            try {
                int id = Integer.parseInt(ask("Booking ID to cancel: "));
                for (Booking b : mine) if (b.id == id) { b.state = "CANCELLED"; lot.tick(); say("Booking " + id + " cancelled."); return; }
                say("No active booking with that ID.");
            } catch (NumberFormatException e) { say("Invalid booking ID."); }
        }
        void admin() {
            if (!ask("Admin password: ").equals(ADMIN_PASSWORD)) { say("Wrong password."); return; }
            while (!eof) {
                say("\n--- ADMIN ---  1 All bookings   2 Vehicle entry   3 Vehicle exit   0 Back"); String c = ask("Admin option: ");
                if (eof || c.equals("0")) return;
                if (c.equals("1")) { printBookings(lot.bookings); continue; }
                if (!c.equals("2") && !c.equals("3")) { say("Invalid option."); continue; }
                int n = askSlot("Slot number: ");
                if (n < 0) continue;
                Space s = lot.spaces.get(n - 1); boolean enter = c.equals("2");
                if (s.entered == enter) say("Slot " + n + (enter ? " is already occupied." : " has no vehicle inside."));
                else { s.entered = enter; say((enter ? "Vehicle entered slot " : "Vehicle left slot ") + n + "."); }
                lot.tick();
            }
        }
        void seedDemo() {                                          // demo data so the lot is not empty
            User u1 = new User("U1", "Arun", "KA01AB1234"), u2 = new User("U2", "Meena", "TN09CD5678"); lot.users.put(u1.id, u1);
            lot.users.put(u2.id, u2); LocalDateTime now = LocalDateTime.now(); List<Space> sp = lot.spaces;
            lot.bookings.add(new Booking(u1, sp.get(1), now.minusMinutes(10), now.plusMinutes(50), 20));
            lot.bookings.add(new Booking(u2, sp.get(4), now, now.plusHours(2), 20));
            lot.bookings.add(new Booking(u1, sp.get(6), now.plusMinutes(30), now.plusMinutes(90), 20));
            lot.bookings.add(new Booking(u2, sp.get(8), now.minusMinutes(5), now.plusMinutes(25), 20));
            lot.bookings.add(new Booking(u2, sp.get(1), now.plusHours(3), now.plusHours(4), 20)); sp.get(8).entered = true;
        }
        void start() {
            in = new Scanner(System.in); seedDemo(); say("SMARTPARK - SMART PARKING MANAGEMENT SYSTEM  (text mode: no display found)");
            say("Demo data loaded. Demo users: U1, U2.  Admin password: " + ADMIN_PASSWORD); printLot();
            while (true) {
                say("\n================ MENU ================\n 1 Register      2 Login         3 Book slot     4 Cancel booking");
                say(" 5 My bookings   6 Show parking  7 Live timers   8 Admin      0 Exit"); String c = ask("Choose option: ");
                if (eof) { say("\n(No more input - demo finished. Run on a computer with a screen for the full colourful window.)"); return; }
                switch (c) {
                    case "1": register(); break;
                    case "2": login(); break;
                    case "3": book(); break;
                    case "4": cancel(); break;
                    case "5": if (needLogin()) printBookings(lot.of(current, false)); break;
                    case "6": printLot(); break;
                    case "7": live(); break;
                    case "8": admin(); break;
                    case "0": say("Goodbye!"); return;
                    default:  say("Invalid option.");
                }
            }
        }
    }
}