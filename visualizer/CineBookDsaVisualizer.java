package visualizer;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CineBook DSA Visualizer
 *
 * A standalone desktop Java Swing visualizer that connects directly to the
 * CineBook Spring Boot backend and mirrors the exact 2D Array (int[5][6])
 * used for movie seat management in real time.
 *
 * DSA Specifications:
 * - Data Structure: 2D Integer Array (int[ROWS][COLS], where ROWS=5, COLS=6)
 * - Row Indexing: 0 to 4 mapped to Rows A through E ('A' + row)
 * - Col Indexing: 0 to 5 mapped to Columns 1 through 6 (col + 1)
 * - Value Semantics: 0 = AVAILABLE (unoccupied memory cell), 1 = BOOKED (occupied)
 * - Time Complexity: O(1) random access for reads and writes
 * - Backend: Spring Boot in-memory HashMap<String, Theatre> (single source of truth)
 */
public class CineBookDsaVisualizer extends JFrame {

    public static final int ROWS = 5;
    public static final int COLS = 6;
    private static final String DEFAULT_BACKEND_URL = "http://localhost:8080";

    // HTTP Client
    private final HttpClient httpClient;
    private String backendUrl = DEFAULT_BACKEND_URL;

    // State Tracking (Mirrors the Backend, NOT an independent source of truth)
    private String selectedShowId = "S-M1-1";
    private int[][] currentSeats = new int[ROWS][COLS];
    private int[][] previousSeats = new int[ROWS][COLS];
    private boolean hasPreviousState = false;
    private final Set<Point> recentlyChangedCells = new HashSet<>();

    // UI Components - Top Bar
    private JTextField backendUrlField;
    private JButton connectBtn;
    private JComboBox<ShowItem> showComboBox;
    private JLabel statusPill;
    private JLabel lastSyncLabel;
    private JCheckBox autoSyncCheckbox;
    private JButton syncNowBtn;

    // UI Components - Center 2D Array Grid
    private CellPanel[][] cellPanels = new CellPanel[ROWS][COLS];
    private JLabel gridTitleLabel;

    // UI Components - Right Inspector Panel
    private JLabel showIdVal;
    private JLabel movieTitleVal;
    private JLabel availableCountVal;
    private JLabel bookedCountVal;
    private JLabel occupancyRateVal;

    // Latest Mutation & Before/After
    private JLabel mutationOpLabel;
    private JLabel mutationCoordLabel;
    private JLabel mutationTransitionLabel;
    private JTextArea mutationExplainArea;
    private JPanel beforeAfterPanel;
    private JLabel beforeCellBadge;
    private JLabel afterCellBadge;

    // Live Event Log
    private JTextArea eventLogArea;

    // Polling Timer
    private javax.swing.Timer pollTimer;
    private boolean isPolling = false;

    public CineBookDsaVisualizer() {
        super("CineBook DSA Visualizer — Real-Time 2D Array Seat Matrix");

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        initComponents();
        initPolling();

        // Initial Data Load
        SwingUtilities.invokeLater(this::fetchShowsAndRefresh);
    }

    private void initComponents() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1320, 840);
        setMinimumSize(new Dimension(1120, 740));
        setLocationRelativeTo(null);

        // Overall Dark Background
        Color bgMain = new Color(13, 17, 26);
        getContentPane().setBackground(bgMain);
        setLayout(new BorderLayout(12, 12));

        // 1. TOP CONTROL BAR
        add(createTopBar(), BorderLayout.NORTH);

        // 2. MAIN CENTER + RIGHT CONTAINER
        JPanel centerContainer = new JPanel(new BorderLayout(14, 14));
        centerContainer.setBackground(bgMain);
        centerContainer.setBorder(new EmptyBorder(0, 16, 8, 16));

        // Left/Center: 2D Array Matrix Grid
        centerContainer.add(createMatrixPanel(), BorderLayout.CENTER);

        // Right: Inspector & Live DSA Log
        centerContainer.add(createRightInspectorPanel(), BorderLayout.EAST);

        add(centerContainer, BorderLayout.CENTER);

        // 3. BOTTOM STATUS & MEMORY FOOTER
        add(createFooterBar(), BorderLayout.SOUTH);

        // Clean Window Close Hook
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (pollTimer != null) pollTimer.stop();
            }
        });
    }

    // =========================================================================
    // TOP BAR: BACKEND URL, SHOW SELECTOR & SYNC CONTROLS
    // =========================================================================
    private JPanel createTopBar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setBackground(new Color(22, 27, 38));
        bar.setBorder(new CompoundBorder(
                new LineBorder(new Color(38, 48, 69), 1),
                new EmptyBorder(8, 16, 8, 16)
        ));

        // Tier 1: Branding, Status Pill, and Sync Controls
        JPanel tier1 = new JPanel(new BorderLayout(10, 0));
        tier1.setOpaque(false);

        JPanel leftTier1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftTier1.setOpaque(false);

        JLabel logo = new JLabel("CINEBOOK");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 17));
        logo.setForeground(new Color(168, 85, 247)); // Accent purple

        JLabel tag = new JLabel("DSA VISUALIZER");
        tag.setFont(new Font("Segoe UI", Font.BOLD, 12));
        tag.setForeground(new Color(236, 72, 153)); // Accent pink
        tag.setBorder(new CompoundBorder(
                new LineBorder(new Color(236, 72, 153, 140), 1, true),
                new EmptyBorder(2, 6, 2, 6)
        ));

        statusPill = new JLabel("● CONNECTING...");
        statusPill.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusPill.setForeground(new Color(245, 158, 11)); // Amber
        statusPill.setBorder(new CompoundBorder(
                new LineBorder(new Color(245, 158, 11, 100), 1, true),
                new EmptyBorder(3, 8, 3, 8)
        ));

        lastSyncLabel = new JLabel("Sync: Waiting...");
        lastSyncLabel.setFont(new Font("Consolas", Font.PLAIN, 12));
        lastSyncLabel.setForeground(new Color(148, 163, 184));

        leftTier1.add(logo);
        leftTier1.add(tag);
        leftTier1.add(Box.createHorizontalStrut(10));
        leftTier1.add(statusPill);
        leftTier1.add(lastSyncLabel);

        JPanel rightTier1 = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightTier1.setOpaque(false);

        autoSyncCheckbox = new JCheckBox("Live Sync (1s)", true);
        autoSyncCheckbox.setFont(new Font("Segoe UI", Font.BOLD, 12));
        autoSyncCheckbox.setForeground(new Color(52, 211, 153));
        autoSyncCheckbox.setOpaque(false);
        autoSyncCheckbox.addActionListener(e -> {
            if (autoSyncCheckbox.isSelected()) {
                if (!pollTimer.isRunning()) pollTimer.start();
                log("Auto-sync enabled (1000ms polling interval).");
            } else {
                pollTimer.stop();
                log("Auto-sync paused by user.");
            }
        });

        syncNowBtn = createStyledButton("Sync Now", new Color(30, 41, 59));
        syncNowBtn.setForeground(new Color(226, 232, 240));
        syncNowBtn.addActionListener(e -> refreshSeatData());

        rightTier1.add(autoSyncCheckbox);
        rightTier1.add(syncNowBtn);

        tier1.add(leftTier1, BorderLayout.WEST);
        tier1.add(rightTier1, BorderLayout.EAST);

        // Tier 2: Backend URL Connection & Show Selection
        JPanel tier2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        tier2.setOpaque(false);

        JLabel urlLabel = new JLabel("Backend URL:");
        urlLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        urlLabel.setForeground(new Color(203, 213, 225));

        backendUrlField = new JTextField(DEFAULT_BACKEND_URL, 18);
        backendUrlField.setFont(new Font("Consolas", Font.PLAIN, 12));
        backendUrlField.setBackground(new Color(15, 23, 42));
        backendUrlField.setForeground(Color.WHITE);
        backendUrlField.setCaretColor(Color.WHITE);
        backendUrlField.setCaretPosition(0);
        backendUrlField.setBorder(new CompoundBorder(
                new LineBorder(new Color(51, 65, 85), 1),
                new EmptyBorder(4, 6, 4, 6)
        ));

        connectBtn = createStyledButton("Connect", new Color(139, 92, 246));
        connectBtn.addActionListener(e -> {
            backendUrl = backendUrlField.getText().trim();
            log("Connecting to backend URL: " + backendUrl);
            fetchShowsAndRefresh();
        });

        JLabel showLabel = new JLabel("Select Show ID:");
        showLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        showLabel.setForeground(new Color(203, 213, 225));

        showComboBox = new JComboBox<>();
        showComboBox.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        showComboBox.setBackground(new Color(15, 23, 42));
        showComboBox.setForeground(Color.WHITE);
        showComboBox.setPreferredSize(new Dimension(280, 28));
        showComboBox.addActionListener(e -> {
            ShowItem item = (ShowItem) showComboBox.getSelectedItem();
            if (item != null && !item.showId.equals(selectedShowId)) {
                selectedShowId = item.showId;
                hasPreviousState = false; // Reset diff on show switch
                recentlyChangedCells.clear();
                log("Switched to show: " + item.showId + " (" + item.display + ")");
                refreshSeatData();
            }
        });

        tier2.add(urlLabel);
        tier2.add(backendUrlField);
        tier2.add(connectBtn);
        tier2.add(Box.createHorizontalStrut(16));
        tier2.add(showLabel);
        tier2.add(showComboBox);

        bar.add(tier1);
        bar.add(Box.createVerticalStrut(4));
        bar.add(tier2);
        return bar;
    }

    // =========================================================================
    // CENTER: 2D ARRAY SEAT MATRIX GRID
    // =========================================================================
    private JPanel createMatrixPanel() {
        JPanel wrapper = new JPanel(new BorderLayout(8, 10));
        wrapper.setBackground(new Color(22, 27, 38));
        wrapper.setBorder(new CompoundBorder(
                new LineBorder(new Color(38, 48, 69), 1),
                new EmptyBorder(14, 18, 14, 18)
        ));

        // Header with title and legend
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        gridTitleLabel = new JLabel("MEMORY VIEW: int[5][6] seats — Show: " + selectedShowId);
        gridTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        gridTitleLabel.setForeground(new Color(241, 245, 249));

        // Legend
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        legendPanel.setOpaque(false);
        legendPanel.add(createLegendBadge("0 : AVAILABLE", new Color(16, 185, 129)));
        legendPanel.add(createLegendBadge("1 : BOOKED", new Color(239, 68, 68)));
        legendPanel.add(createLegendBadge("★ JUST UPDATED", new Color(168, 85, 247)));

        headerPanel.add(gridTitleLabel, BorderLayout.WEST);
        headerPanel.add(legendPanel, BorderLayout.EAST);
        wrapper.add(headerPanel, BorderLayout.NORTH);

        // Grid Panel with Column & Row Headers
        JPanel gridWithHeaders = new JPanel(new BorderLayout(6, 6));
        gridWithHeaders.setOpaque(false);

        // 1. Column Headers Panel (Top)
        JPanel colHeaderPanel = new JPanel(new GridLayout(1, COLS, 8, 0));
        colHeaderPanel.setOpaque(false);
        colHeaderPanel.setBorder(new EmptyBorder(0, 90, 4, 0)); // Align with row headers
        for (int c = 0; c < COLS; c++) {
            JLabel colLbl = new JLabel("Col " + c + " [Seat " + (c + 1) + "]", SwingConstants.CENTER);
            colLbl.setFont(new Font("Consolas", Font.BOLD, 12));
            colLbl.setForeground(new Color(148, 163, 184));
            colLbl.setBorder(new CompoundBorder(
                    new LineBorder(new Color(51, 65, 85), 1),
                    new EmptyBorder(4, 4, 4, 4)
            ));
            colHeaderPanel.add(colLbl);
        }
        gridWithHeaders.add(colHeaderPanel, BorderLayout.NORTH);

        // 2. Row Headers Panel (West)
        JPanel rowHeaderPanel = new JPanel(new GridLayout(ROWS, 1, 0, 8));
        rowHeaderPanel.setOpaque(false);
        rowHeaderPanel.setPreferredSize(new Dimension(84, 0));
        for (int r = 0; r < ROWS; r++) {
            char rowLetter = (char) ('A' + r);
            JLabel rowLbl = new JLabel("[Row " + r + "] " + rowLetter, SwingConstants.CENTER);
            rowLbl.setFont(new Font("Consolas", Font.BOLD, 12));
            rowLbl.setForeground(new Color(148, 163, 184));
            rowLbl.setBorder(new CompoundBorder(
                    new LineBorder(new Color(51, 65, 85), 1),
                    new EmptyBorder(4, 4, 4, 4)
            ));
            rowHeaderPanel.add(rowLbl);
        }
        gridWithHeaders.add(rowHeaderPanel, BorderLayout.WEST);

        // 3. Central 5x6 Matrix Cells
        JPanel matrixGrid = new JPanel(new GridLayout(ROWS, COLS, 8, 8));
        matrixGrid.setOpaque(false);

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                CellPanel cp = new CellPanel(r, c);
                cellPanels[r][c] = cp;
                matrixGrid.add(cp);
            }
        }
        gridWithHeaders.add(matrixGrid, BorderLayout.CENTER);

        wrapper.add(gridWithHeaders, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel createLegendBadge(String text, Color accent) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        p.setOpaque(false);
        JLabel dot = new JLabel("■");
        dot.setFont(new Font("Segoe UI", Font.BOLD, 14));
        dot.setForeground(accent);
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lbl.setForeground(new Color(203, 213, 225));
        p.add(dot);
        p.add(lbl);
        return p;
    }

    // =========================================================================
    // RIGHT: INSPECTOR PANEL (DSA METRICS, LATEST MUTATION, EVENT LOG)
    // =========================================================================
    private JPanel createRightInspectorPanel() {
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBackground(new Color(22, 27, 38));
        right.setPreferredSize(new Dimension(420, 0));
        right.setBorder(new CompoundBorder(
                new LineBorder(new Color(38, 48, 69), 1),
                new EmptyBorder(14, 14, 14, 14)
        ));

        // 1. SECTION: CURRENT SHOW METRICS
        JLabel sec1 = createSectionTitle("1. CURRENT SHOW METRICS");
        right.add(sec1);
        right.add(Box.createVerticalStrut(6));

        JPanel metricsCard = new JPanel(new GridLayout(5, 2, 6, 4));
        metricsCard.setOpaque(false);
        metricsCard.setBorder(new EmptyBorder(4, 6, 8, 6));

        metricsCard.add(createPropLabel("Show ID:"));
        showIdVal = createPropVal("S-M1-1");
        metricsCard.add(showIdVal);

        metricsCard.add(createPropLabel("Movie:"));
        movieTitleVal = createPropVal("Interstellar");
        metricsCard.add(movieTitleVal);

        metricsCard.add(createPropLabel("Available Seats (val=0):"));
        availableCountVal = createPropVal("30 / 30");
        availableCountVal.setForeground(new Color(52, 211, 153));
        metricsCard.add(availableCountVal);

        metricsCard.add(createPropLabel("Booked Seats (val=1):"));
        bookedCountVal = createPropVal("0 / 30");
        bookedCountVal.setForeground(new Color(248, 113, 113));
        metricsCard.add(bookedCountVal);

        metricsCard.add(createPropLabel("Occupancy Rate:"));
        occupancyRateVal = createPropVal("0.0%");
        metricsCard.add(occupancyRateVal);

        right.add(metricsCard);
        right.add(Box.createVerticalStrut(10));
        right.add(new JSeparator(SwingConstants.HORIZONTAL));
        right.add(Box.createVerticalStrut(10));

        // 2. SECTION: LAST DSA MUTATION & BEFORE/AFTER STATE
        JLabel sec2 = createSectionTitle("2. LATEST DSA ARRAY OPERATION");
        right.add(sec2);
        right.add(Box.createVerticalStrut(6));

        JPanel mutPanel = new JPanel();
        mutPanel.setLayout(new BoxLayout(mutPanel, BoxLayout.Y_AXIS));
        mutPanel.setOpaque(false);

        mutationOpLabel = new JLabel("Operation: Initialized [No changes yet]");
        mutationOpLabel.setFont(new Font("Consolas", Font.BOLD, 12));
        mutationOpLabel.setForeground(new Color(168, 85, 247)); // Purple

        mutationCoordLabel = new JLabel("Array Access: seats[row][col]");
        mutationCoordLabel.setFont(new Font("Consolas", Font.PLAIN, 12));
        mutationCoordLabel.setForeground(new Color(226, 232, 240));

        mutationTransitionLabel = new JLabel("Transition: —");
        mutationTransitionLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        mutationTransitionLabel.setForeground(new Color(148, 163, 184));

        mutPanel.add(mutationOpLabel);
        mutPanel.add(Box.createVerticalStrut(4));
        mutPanel.add(mutationCoordLabel);
        mutPanel.add(Box.createVerticalStrut(4));
        mutPanel.add(mutationTransitionLabel);
        mutPanel.add(Box.createVerticalStrut(8));

        // Before & After Visual Badges
        beforeAfterPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        beforeAfterPanel.setOpaque(false);
        beforeAfterPanel.setBorder(new CompoundBorder(
                new LineBorder(new Color(51, 65, 85), 1),
                new EmptyBorder(6, 10, 6, 10)
        ));

        JPanel rowBefore = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        rowBefore.setOpaque(false);
        JLabel beforeTitle = new JLabel("BEFORE:");
        beforeTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        beforeTitle.setForeground(new Color(148, 163, 184));
        beforeCellBadge = new JLabel("val = 0 (AVAILABLE)");
        beforeCellBadge.setFont(new Font("Consolas", Font.BOLD, 12));
        beforeCellBadge.setForeground(new Color(52, 211, 153));
        rowBefore.add(beforeTitle);
        rowBefore.add(beforeCellBadge);

        JPanel rowAfter = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        rowAfter.setOpaque(false);
        JLabel afterTitle = new JLabel("AFTER: ");
        afterTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        afterTitle.setForeground(new Color(148, 163, 184));
        afterCellBadge = new JLabel("val = 1 (BOOKED)");
        afterCellBadge.setFont(new Font("Consolas", Font.BOLD, 12));
        afterCellBadge.setForeground(new Color(248, 113, 113));
        rowAfter.add(afterTitle);
        rowAfter.add(afterCellBadge);

        beforeAfterPanel.add(rowBefore);
        beforeAfterPanel.add(rowAfter);
        mutPanel.add(beforeAfterPanel);

        mutPanel.add(Box.createVerticalStrut(8));

        mutationExplainArea = new JTextArea(4, 28);
        mutationExplainArea.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        mutationExplainArea.setBackground(new Color(15, 23, 42));
        mutationExplainArea.setForeground(new Color(203, 213, 225));
        mutationExplainArea.setLineWrap(true);
        mutationExplainArea.setWrapStyleWord(true);
        mutationExplainArea.setEditable(false);
        mutationExplainArea.setBorder(new EmptyBorder(6, 6, 6, 6));
        mutationExplainArea.setText("Select a cell or book/cancel a seat on the CineBook website. The visualizer will show index calculations and exact array mutation.");

        mutPanel.add(mutationExplainArea);
        right.add(mutPanel);

        right.add(Box.createVerticalStrut(10));
        right.add(new JSeparator(SwingConstants.HORIZONTAL));
        right.add(Box.createVerticalStrut(10));

        // 3. SECTION: LIVE DSA EVENT LOG
        JPanel logHeader = new JPanel(new BorderLayout());
        logHeader.setOpaque(false);
        JLabel sec3 = createSectionTitle("3. REAL-TIME DSA EVENT LOG");
        JButton clearLogBtn = new JButton("Clear");
        clearLogBtn.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        clearLogBtn.setMargin(new Insets(1, 4, 1, 4));
        clearLogBtn.addActionListener(e -> eventLogArea.setText(""));
        logHeader.add(sec3, BorderLayout.WEST);
        logHeader.add(clearLogBtn, BorderLayout.EAST);
        right.add(logHeader);
        right.add(Box.createVerticalStrut(6));

        eventLogArea = new JTextArea();
        eventLogArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        eventLogArea.setBackground(new Color(11, 15, 25));
        eventLogArea.setForeground(new Color(167, 139, 250)); // Soft purple
        eventLogArea.setEditable(false);
        eventLogArea.setMargin(new Insets(6, 6, 6, 6));

        JScrollPane logScroll = new JScrollPane(eventLogArea);
        logScroll.setPreferredSize(new Dimension(340, 160));
        logScroll.setBorder(new LineBorder(new Color(51, 65, 85), 1));
        right.add(logScroll);

        return right;
    }

    private JLabel createSectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setForeground(new Color(226, 232, 240));
        return l;
    }

    private JLabel createPropLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        l.setForeground(new Color(148, 163, 184));
        return l;
    }

    private JLabel createPropVal(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Consolas", Font.BOLD, 12));
        l.setForeground(Color.WHITE);
        return l;
    }

    // =========================================================================
    // BOTTOM: FOOTER BAR (DSA ARCHITECTURE & COMPLEXITY)
    // =========================================================================
    private JPanel createFooterBar() {
        JPanel footer = new JPanel(new BorderLayout(10, 0));
        footer.setBackground(new Color(15, 23, 42));
        footer.setBorder(new CompoundBorder(
                new LineBorder(new Color(38, 48, 69), 1),
                new EmptyBorder(6, 16, 6, 16)
        ));

        JLabel left = new JLabel("DATA STRUCTURE: int[5][6] seats  •  Access: O(1)  •  Storage: In-Memory Java HashMap (resets on Spring Boot restart)");
        left.setFont(new Font("Consolas", Font.PLAIN, 11));
        left.setForeground(new Color(148, 163, 184));

        JLabel right = new JLabel("Single Source of Truth: Spring Boot Theatre.java  •  CineBook DSA Capstone");
        right.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        right.setForeground(new Color(100, 116, 139));

        footer.add(left, BorderLayout.WEST);
        footer.add(right, BorderLayout.EAST);
        return footer;
    }

    // =========================================================================
    // BACKGROUND REAL-TIME POLLING & SYNC ENGINE
    // =========================================================================
    private void initPolling() {
        pollTimer = new javax.swing.Timer(1000, e -> {
            if (autoSyncCheckbox.isSelected()) {
                refreshSeatData();
            }
        });
        pollTimer.start();
    }

    /**
     * Fetches all available shows from /api/shows and populates the show selector dropdown.
     */
    private void fetchShowsAndRefresh() {
        new Thread(() -> {
            try {
                String showsUrl = backendUrl.replaceAll("/+$", "") + "/api/shows";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(showsUrl))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    List<ShowItem> shows = parseShowsJson(response.body());
                    SwingUtilities.invokeLater(() -> {
                        showComboBox.removeAllItems();
                        for (ShowItem s : shows) {
                            showComboBox.addItem(s);
                        }
                        if (!shows.isEmpty()) {
                            showComboBox.setSelectedIndex(0);
                            selectedShowId = shows.get(0).showId;
                        }
                        updateConnectionStatus(true);
                        log("Connected to backend at " + backendUrl + ". Loaded " + shows.size() + " shows.");
                        refreshSeatData();
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        updateConnectionStatus(false);
                        log("HTTP Error " + response.statusCode() + " fetching shows.");
                    });
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    updateConnectionStatus(false);
                    log("Connection failed: " + ex.getMessage());
                });
            }
        }).start();
    }

    /**
     * Queries GET /api/shows/{showId}/seats and updates the 2D array representation.
     */
    private void refreshSeatData() {
        if (isPolling) return;
        isPolling = true;

        new Thread(() -> {
            try {
                String url = backendUrl.replaceAll("/+$", "") + "/api/shows/" + selectedShowId + "/seats";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    int[][] newSeats = parseSeatMatrixJson(response.body());
                    int availableCount = parseJsonInt(response.body(), "availableCount", 30);
                    int totalSeats = parseJsonInt(response.body(), "totalSeats", 30);

                    SwingUtilities.invokeLater(() -> {
                        applySeatMatrixUpdate(newSeats, availableCount, totalSeats);
                        updateConnectionStatus(true);
                    });
                } else {
                    SwingUtilities.invokeLater(() -> updateConnectionStatus(false));
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> updateConnectionStatus(false));
            } finally {
                isPolling = false;
            }
        }).start();
    }

    /**
     * Compares previous 2D array state with incoming state and triggers UI updates + mutation analysis.
     */
    private void applySeatMatrixUpdate(int[][] newSeats, int availableCount, int totalSeats) {
        recentlyChangedCells.clear();
        List<String> mutationMessages = new ArrayList<>();

        if (hasPreviousState) {
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    int oldVal = previousSeats[r][c];
                    int newVal = newSeats[r][c];
                    if (oldVal != newVal) {
                        recentlyChangedCells.add(new Point(r, c));
                        char rowChar = (char) ('A' + r);
                        String seatLabel = "" + rowChar + (c + 1);

                        if (oldVal == 0 && newVal == 1) {
                            // Booking Operation
                            String msg = String.format("MUTATION: seats[%d][%d] = 1 (Seat %s BOOKED via Website)", r, c, seatLabel);
                            mutationMessages.add(msg);
                            log(msg);
                            showMutationDetails(r, c, seatLabel, 0, 1, "BOOKING");
                        } else if (oldVal == 1 && newVal == 0) {
                            // Cancellation Operation
                            String msg = String.format("MUTATION: seats[%d][%d] = 0 (Seat %s CANCELLED & Released)", r, c, seatLabel);
                            mutationMessages.add(msg);
                            log(msg);
                            showMutationDetails(r, c, seatLabel, 1, 0, "CANCELLATION");
                        }
                    }
                }
            }
        } else {
            hasPreviousState = true;
            log("Initial matrix synchronization for show " + selectedShowId + " complete.");
        }

        // Copy new state into current and previous arrays
        for (int r = 0; r < ROWS; r++) {
            System.arraycopy(newSeats[r], 0, currentSeats[r], 0, COLS);
            System.arraycopy(newSeats[r], 0, previousSeats[r], 0, COLS);
        }

        // Repaint all 30 cells in the matrix
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                boolean changed = recentlyChangedCells.contains(new Point(r, c));
                cellPanels[r][c].updateCell(currentSeats[r][c], changed);
            }
        }

        // Update Right Inspector Metrics
        int bookedCount = totalSeats - availableCount;
        double rate = (totalSeats > 0) ? ((double) bookedCount / totalSeats * 100.0) : 0.0;

        showIdVal.setText(selectedShowId);
        gridTitleLabel.setText("MEMORY VIEW: int[5][6] seats — Show: " + selectedShowId);
        availableCountVal.setText(availableCount + " / " + totalSeats);
        bookedCountVal.setText(bookedCount + " / " + totalSeats);
        occupancyRateVal.setText(String.format("%.1f%%", rate));

        // Update Sync Timestamp
        String now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        lastSyncLabel.setText("Synced: " + now);
    }

    private void showMutationDetails(int row, int col, String label, int oldVal, int newVal, String type) {
        mutationOpLabel.setText("Operation: seats[" + row + "][" + col + "] = " + newVal + ";");
        mutationCoordLabel.setText("Array Access: Row " + row + " ('" + label.charAt(0) + "'), Col " + col + " (Seat " + (col + 1) + ")");
        mutationTransitionLabel.setText("Transition: " + (oldVal == 0 ? "AVAILABLE (0)" : "BOOKED (1)") + " -> " + (newVal == 0 ? "AVAILABLE (0)" : "BOOKED (1)"));

        beforeCellBadge.setText("val = " + oldVal + " (" + (oldVal == 0 ? "AVAILABLE" : "BOOKED") + ")");
        beforeCellBadge.setForeground(oldVal == 0 ? new Color(52, 211, 153) : new Color(248, 113, 113));

        afterCellBadge.setText("val = " + newVal + " (" + (newVal == 0 ? "AVAILABLE" : "BOOKED") + ")");
        afterCellBadge.setForeground(newVal == 0 ? new Color(52, 211, 153) : new Color(248, 113, 113));

        if ("BOOKING".equals(type)) {
            mutationExplainArea.setText(String.format(
                    "WEBSITE BOOKING DETECTED:\n" +
                    "1. Customer selected seat '%s' on the website.\n" +
                    "2. Backend calculated indices: row = '%s' - 'A' = %d; col = %d - 1 = %d.\n" +
                    "3. Backend executed: seats[%d][%d] = 1 in O(1) time.\n" +
                    "4. Memory cell [%d][%d] updated from 0 to 1.",
                    label, label.charAt(0), row, col + 1, col, row, col, row, col
            ));
        } else {
            mutationExplainArea.setText(String.format(
                    "WEBSITE CANCELLATION DETECTED:\n" +
                    "1. Customer cancelled booking containing seat '%s'.\n" +
                    "2. Backend calculated indices: row = %d, col = %d.\n" +
                    "3. Backend executed: seats[%d][%d] = 0 in O(1) time.\n" +
                    "4. Memory cell [%d][%d] reset from 1 to 0 (seat now available).",
                    label, row, col, row, col, row, col
            ));
        }
    }

    private void updateConnectionStatus(boolean connected) {
        if (connected) {
            statusPill.setText("● CONNECTED");
            statusPill.setForeground(new Color(52, 211, 153)); // Green
            statusPill.setBorder(new CompoundBorder(
                    new LineBorder(new Color(52, 211, 153, 100), 1, true),
                    new EmptyBorder(3, 8, 3, 8)
            ));
        } else {
            statusPill.setText("● DISCONNECTED");
            statusPill.setForeground(new Color(239, 68, 68)); // Red
            statusPill.setBorder(new CompoundBorder(
                    new LineBorder(new Color(239, 68, 68, 100), 1, true),
                    new EmptyBorder(3, 8, 3, 8)
            ));
        }
    }

    private void log(String msg) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        eventLogArea.append("[" + timestamp + "] " + msg + "\n");
        eventLogArea.setCaretPosition(eventLogArea.getDocument().getLength());
    }

    // =========================================================================
    // CUSTOM 2D ARRAY CELL COMPONENT
    // =========================================================================
    private class CellPanel extends JPanel {
        private final int row;
        private final int col;
        private final String seatLabel;
        private int value = 0;
        private boolean isChanged = false;

        private final JLabel indexLabel;
        private final JLabel seatLabelView;
        private final JLabel valLabel;
        private final JLabel statusBadge;

        public CellPanel(int r, int c) {
            this.row = r;
            this.col = c;
            this.seatLabel = "" + (char) ('A' + r) + (c + 1);

            setLayout(new BorderLayout(2, 2));
            setBorder(new LineBorder(new Color(51, 65, 85), 1, true));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            // Top Row: Index
            indexLabel = new JLabel("[" + r + "][" + c + "]", SwingConstants.CENTER);
            indexLabel.setFont(new Font("Consolas", Font.BOLD, 11));
            indexLabel.setForeground(new Color(148, 163, 184));
            add(indexLabel, BorderLayout.NORTH);

            // Center: Big Seat Code + Stored Val
            JPanel center = new JPanel(new GridLayout(2, 1, 0, 0));
            center.setOpaque(false);

            seatLabelView = new JLabel(seatLabel, SwingConstants.CENTER);
            seatLabelView.setFont(new Font("Segoe UI", Font.BOLD, 18));
            seatLabelView.setForeground(Color.WHITE);

            valLabel = new JLabel("val = 0", SwingConstants.CENTER);
            valLabel.setFont(new Font("Consolas", Font.BOLD, 12));
            valLabel.setForeground(new Color(52, 211, 153));

            center.add(seatLabelView);
            center.add(valLabel);
            add(center, BorderLayout.CENTER);

            // Bottom: Status Badge
            statusBadge = new JLabel("AVAILABLE", SwingConstants.CENTER);
            statusBadge.setFont(new Font("Segoe UI", Font.BOLD, 9));
            statusBadge.setOpaque(true);
            statusBadge.setBackground(new Color(6, 78, 59)); // Dark green
            statusBadge.setForeground(new Color(110, 231, 183));
            statusBadge.setBorder(new EmptyBorder(2, 2, 2, 2));
            add(statusBadge, BorderLayout.SOUTH);

            // Interactive Click to Inspect
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    inspectCell(row, col, seatLabel, value);
                }
            });

            updateVisuals();
        }

        public void updateCell(int newVal, boolean changed) {
            this.value = newVal;
            this.isChanged = changed;
            updateVisuals();
        }

        private void updateVisuals() {
            if (value == 0) {
                // Available
                setBackground(new Color(24, 32, 47));
                valLabel.setText("val = 0");
                valLabel.setForeground(new Color(52, 211, 153));
                statusBadge.setText(isChanged ? "★ RELEASED (0)" : "AVAILABLE (0)");
                statusBadge.setBackground(new Color(6, 78, 59));
                statusBadge.setForeground(new Color(110, 231, 183));
            } else {
                // Booked
                setBackground(new Color(45, 23, 31));
                valLabel.setText("val = 1");
                valLabel.setForeground(new Color(248, 113, 113));
                statusBadge.setText(isChanged ? "★ BOOKED (1)" : "BOOKED (1)");
                statusBadge.setBackground(new Color(127, 29, 29));
                statusBadge.setForeground(new Color(254, 202, 202));
            }

            if (isChanged) {
                // Flashing accent border for recently changed cell
                setBorder(new CompoundBorder(
                        new LineBorder(new Color(168, 85, 247), 2, true),
                        new EmptyBorder(1, 1, 1, 1)
                ));
            } else {
                setBorder(new LineBorder(new Color(51, 65, 85), 1, true));
            }
            revalidate();
            repaint();
        }
    }

    private void inspectCell(int r, int c, String label, int val) {
        mutationOpLabel.setText("Inspecting Cell: seats[" + r + "][" + c + "]");
        mutationCoordLabel.setText("Index Mapping: Row " + r + " ('" + label.charAt(0) + "'), Column " + c + " (Seat " + (c + 1) + ")");
        mutationTransitionLabel.setText("Current Stored Value: " + val + " (" + (val == 0 ? "AVAILABLE" : "BOOKED") + ")");
        mutationExplainArea.setText(String.format(
                "CELL INSPECTOR [SEAT %s]:\n" +
                "• Array Coordinates: row = %d, col = %d\n" +
                "• Stored Value: %d (%s)\n" +
                "• Row Calculation: '%s' - 'A' = %d\n" +
                "• Col Calculation: %d - 1 = %d\n" +
                "• Direct Memory Access: seats[%d][%d] in O(1) time.\n" +
                "• Meaning: %s",
                label, r, c, val, (val == 0 ? "AVAILABLE" : "BOOKED"),
                label.charAt(0), r, c + 1, c, r, c,
                (val == 0 ? "Free for customers to book on CineBook." : "Occupied by confirmed ticket.")
        ));
    }

    // =========================================================================
    // LIGHTWEIGHT JSON PARSING (ZERO EXTERNAL DEPENDENCIES)
    // =========================================================================
    private static class ShowItem {
        final String showId;
        final String display;

        ShowItem(String showId, String display) {
            this.showId = showId;
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    private List<ShowItem> parseShowsJson(String json) {
        List<ShowItem> list = new ArrayList<>();
        Pattern p = Pattern.compile("\\{[^\\}]*\\\"showId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"[^\\}]*\\}");
        Matcher m = p.matcher(json);
        while (m.find()) {
            String block = m.group();
            String id = extractJsonString(block, "showId");
            String time = extractJsonString(block, "time");
            String movieId = extractJsonString(block, "movieId");
            String display = id + " • " + (time != null ? time : "") + " (" + movieId + ")";
            list.add(new ShowItem(id, display));
        }
        if (list.isEmpty()) {
            list.add(new ShowItem("S-M1-1", "S-M1-1 • 10:00 AM (M1)"));
            list.add(new ShowItem("S-M2-1", "S-M2-1 • 10:00 AM (M2)"));
        }
        return list;
    }

    private int[][] parseSeatMatrixJson(String json) {
        int[][] matrix = new int[ROWS][COLS];
        int seatsStart = json.indexOf("\"seats\"");
        if (seatsStart == -1) return matrix;

        int arrayStart = json.indexOf("[[", seatsStart);
        int arrayEnd = json.indexOf("]]", arrayStart);
        if (arrayStart == -1 || arrayEnd == -1) return matrix;

        String arrayContent = json.substring(arrayStart + 1, arrayEnd + 1); // e.g. [0,0,0,0,0,0],[0,0,...]
        Pattern rowPattern = Pattern.compile("\\[([^\\]]+)\\]");
        Matcher rowMatcher = rowPattern.matcher(arrayContent);

        int r = 0;
        while (rowMatcher.find() && r < ROWS) {
            String rowValues = rowMatcher.group(1);
            String[] tokens = rowValues.split(",");
            for (int c = 0; c < Math.min(tokens.length, COLS); c++) {
                try {
                    matrix[r][c] = Integer.parseInt(tokens[c].trim());
                } catch (NumberFormatException ignored) {}
            }
            r++;
        }
        return matrix;
    }

    private static String extractJsonString(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static int parseJsonInt(String json, String key, int def) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private static JButton createStyledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(new CompoundBorder(
                new LineBorder(bg.brighter(), 1, true),
                new EmptyBorder(5, 12, 5, 12)
        ));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // =========================================================================
    // MAIN ENTRY POINT
    // =========================================================================
    public static void main(String[] args) {
        // Enable Antialiasing
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            CineBookDsaVisualizer app = new CineBookDsaVisualizer();
            app.setVisible(true);
        });
    }
}
