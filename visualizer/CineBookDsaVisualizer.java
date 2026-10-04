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
 * A standalone desktop Java Swing application that connects directly to the
 * CineBook Spring Boot backend and mirrors the exact real-time data structures:
 *
 * 1. 2D Seat Array: int[5][6] seats matrix with O(1) random access reads and writes.
 * 2. Booking HashMap: HashMap<String, Booking> bookingMap with O(1) amortized put/get
 *    and bucket collision chaining.
 * 3. Waiting Queue: Custom FIFO LinkedList Queue (WaitingList) with O(1) enqueue at
 *    REAR and O(1) dequeue from FRONT.
 * 4. Occupied HashSet: HashSet<String> occupiedSeatsSet demonstrating O(1) duplicate
 *    prevention and collision detection.
 *
 * Single Source of Truth: Spring Boot Backend (http://localhost:8080).
 */
public class CineBookDsaVisualizer extends JFrame {

    public static final int ROWS = 5;
    public static final int COLS = 6;
    private static final String DEFAULT_BACKEND_URL = "http://localhost:8080";
    public static final int HASHMAP_CAPACITY = 16;
    public static final double LOAD_FACTOR = 0.75;

    // HTTP Client
    private final HttpClient httpClient;
    private String backendUrl = DEFAULT_BACKEND_URL;

    // State Tracking (Mirrors the Backend, NOT an independent source of truth)
    private String selectedShowId = "S-M1-1";

    // 1. Array State
    private int[][] currentSeats = new int[ROWS][COLS];
    private int[][] previousSeats = new int[ROWS][COLS];
    private boolean hasPreviousArrayState = false;
    private final Set<Point> recentlyChangedCells = new HashSet<>();

    // 2. HashMap State
    private final Map<String, BookingDto> currentBookings = new LinkedHashMap<>();
    private final Map<String, BookingDto> previousBookings = new LinkedHashMap<>();
    private boolean hasPreviousHashMapState = false;
    private String highlightedBookingKey = null;

    // 3. Queue State
    private final List<WaitingNodeDto> currentQueue = new ArrayList<>();
    private final List<WaitingNodeDto> previousQueue = new ArrayList<>();
    private boolean hasPreviousQueueState = false;
    private String newlyEnqueuedCustomer = null;
    private String newlyDequeuedCustomer = null;

    // 4. HashSet State
    private final Set<String> currentOccupiedSet = new LinkedHashSet<>();
    private final Set<String> previousOccupiedSet = new LinkedHashSet<>();

    // Top Bar UI Components
    private JTextField backendUrlField;
    private JButton connectBtn;
    private JComboBox<ShowItem> showComboBox;
    private JLabel statusPill;
    private JLabel lastSyncLabel;
    private JCheckBox autoSyncCheckbox;
    private JButton syncNowBtn;

    // Tabs
    private JTabbedPane tabbedPane;

    // Tab 1: 2D Array Grid
    private CellPanel[][] cellPanels = new CellPanel[ROWS][COLS];
    private JLabel gridTitleLabel;

    // Tab 2: HashMap Components
    private JLabel hmSizeLabel;
    private JLabel hmCapacityLabel;
    private JLabel hmLoadFactorLabel;
    private JLabel hmThresholdLabel;
    private JTextField hmSearchField;
    private JButton hmSearchBtn;
    private JLabel hmSearchResultLabel;
    private JPanel hmBucketsPanel;
    private JPanel hmEntriesListPanel;

    // Tab 3: Queue Components
    private JLabel qSizeLabel;
    private JLabel qFrontLabel;
    private JLabel qRearLabel;
    private JLabel qEmptyStatusLabel;
    private JPanel qVisualPipelinePanel;
    private JTextField qCustNameField;
    private JComboBox<Integer> qSeatsCombo;
    private JButton qEnqueueBtn;
    private JLabel qActionFeedbackLabel;

    // Tab 4: HashSet Components
    private JLabel hsSizeLabel;
    private JPanel hsChipsPanel;
    private JTextField hsCheckField;
    private JButton hsCheckBtn;
    private JLabel hsCheckResultLabel;

    // Right Inspector Panel
    private JLabel showIdVal;
    private JLabel movieTitleVal;
    private JLabel availableCountVal;
    private JLabel bookedCountVal;
    private JLabel occupancyRateVal;
    private JLabel waitlistCountVal;
    private JLabel totalBookingsVal;

    // Latest Mutation & Before/After
    private JLabel mutationDsaTypeBadge;
    private JLabel mutationOpLabel;
    private JLabel mutationCoordLabel;
    private JLabel mutationTransitionLabel;
    private JTextArea mutationExplainArea;
    private JLabel beforeBadge;
    private JLabel afterBadge;

    // Real-Time Event Log
    private JTextArea eventLogArea;

    // Polling Timer
    private javax.swing.Timer pollTimer;
    private boolean isPolling = false;

    public CineBookDsaVisualizer() {
        super("CineBook DSA Visualizer — Real-Time Data Structures (Array • HashMap • Queue • Set)");

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
        setSize(1400, 880);
        setMinimumSize(new Dimension(1180, 760));
        setLocationRelativeTo(null);

        // Overall Dark Background
        Color bgMain = new Color(11, 13, 20);
        getContentPane().setBackground(bgMain);
        setLayout(new BorderLayout(10, 10));

        // 1. TOP CONTROL BAR
        add(createTopBar(), BorderLayout.NORTH);

        // 2. MAIN SPLIT: LEFT TABS (Array, HashMap, Queue, Set) + RIGHT INSPECTOR & LOG
        JPanel centerContainer = new JPanel(new BorderLayout(12, 12));
        centerContainer.setBackground(bgMain);
        centerContainer.setBorder(new EmptyBorder(0, 14, 8, 14));

        // Tabs Pane
        tabbedPane = createStyledTabbedPane();
        tabbedPane.addTab("  🪑 2D Seat Array (int[5][6])  ", createMatrixTab());
        tabbedPane.addTab("  🗺️ Booking HashMap  ", createHashMapTab());
        tabbedPane.addTab("  👥 Waiting Queue (FIFO)  ", createQueueTab());
        tabbedPane.addTab("  🏷️ Occupied Seats (HashSet)  ", createHashSetTab());

        centerContainer.add(tabbedPane, BorderLayout.CENTER);

        // Right Inspector Panel
        centerContainer.add(createRightInspectorPanel(), BorderLayout.EAST);

        add(centerContainer, BorderLayout.CENTER);

        // 3. BOTTOM FOOTER BAR
        add(createFooterBar(), BorderLayout.SOUTH);

        // Window Closing Hook
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (pollTimer != null) pollTimer.stop();
            }
        });
    }

    private JTabbedPane createStyledTabbedPane() {
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabs.setBackground(new Color(22, 27, 38));
        tabs.setForeground(new Color(226, 232, 240));
        tabs.setBorder(new LineBorder(new Color(38, 48, 69), 1));
        return tabs;
    }

    // =========================================================================
    // 1. TOP BAR: BRANDING, STATUS, URL & CONTROLS
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
        logo.setFont(new Font("Segoe UI", Font.BOLD, 18));
        logo.setForeground(new Color(168, 85, 247)); // Accent purple

        JLabel tag = new JLabel("DSA MULTI-STRUCTURE VISUALIZER");
        tag.setFont(new Font("Segoe UI", Font.BOLD, 11));
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
                log("SYSTEM", "Auto-sync enabled (1000ms polling interval).");
            } else {
                pollTimer.stop();
                log("SYSTEM", "Auto-sync paused by user.");
            }
        });

        syncNowBtn = createStyledButton("Sync Now", new Color(30, 41, 59));
        syncNowBtn.setForeground(new Color(226, 232, 240));
        syncNowBtn.addActionListener(e -> refreshAllDsaData());

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
        backendUrlField.setBorder(new CompoundBorder(
                new LineBorder(new Color(51, 65, 85), 1),
                new EmptyBorder(4, 6, 4, 6)
        ));

        connectBtn = createStyledButton("Connect", new Color(139, 92, 246));
        connectBtn.addActionListener(e -> {
            backendUrl = backendUrlField.getText().trim();
            log("SYSTEM", "Connecting to backend URL: " + backendUrl);
            fetchShowsAndRefresh();
        });

        JLabel showLabel = new JLabel("Select Show ID:");
        showLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        showLabel.setForeground(new Color(203, 213, 225));

        showComboBox = new JComboBox<>();
        showComboBox.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        showComboBox.setBackground(new Color(15, 23, 42));
        showComboBox.setForeground(Color.WHITE);
        showComboBox.setPreferredSize(new Dimension(290, 28));
        showComboBox.addActionListener(e -> {
            ShowItem item = (ShowItem) showComboBox.getSelectedItem();
            if (item != null && !item.showId.equals(selectedShowId)) {
                selectedShowId = item.showId;
                hasPreviousArrayState = false;
                hasPreviousHashMapState = false;
                hasPreviousQueueState = false;
                recentlyChangedCells.clear();
                log("SYSTEM", "Switched to show: " + item.showId + " (" + item.display + ")");
                refreshAllDsaData();
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
    // TAB 1: 2D ARRAY SEAT MATRIX (int[5][6])
    // =========================================================================
    private JPanel createMatrixTab() {
        JPanel wrapper = new JPanel(new BorderLayout(8, 10));
        wrapper.setBackground(new Color(18, 22, 33));
        wrapper.setBorder(new CompoundBorder(
                new LineBorder(new Color(38, 48, 69), 1),
                new EmptyBorder(12, 16, 12, 16)
        ));

        // Header with title and legend
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        gridTitleLabel = new JLabel("MEMORY VIEW: int[5][6] seats — Show: " + selectedShowId);
        gridTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        gridTitleLabel.setForeground(new Color(241, 245, 249));

        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        legendPanel.setOpaque(false);
        legendPanel.add(createLegendBadge("0 : AVAILABLE", new Color(16, 185, 129)));
        legendPanel.add(createLegendBadge("1 : BOOKED", new Color(239, 68, 68)));
        legendPanel.add(createLegendBadge("★ JUST UPDATED", new Color(168, 85, 247)));

        headerPanel.add(gridTitleLabel, BorderLayout.WEST);
        headerPanel.add(legendPanel, BorderLayout.EAST);
        wrapper.add(headerPanel, BorderLayout.NORTH);

        // Screen Curve Banner
        JPanel screenBanner = new JPanel();
        screenBanner.setBackground(new Color(30, 41, 59));
        screenBanner.setBorder(new CompoundBorder(
                new LineBorder(new Color(168, 85, 247, 180), 1, true),
                new EmptyBorder(4, 20, 4, 20)
        ));
        JLabel screenText = new JLabel("── CINEMA SCREEN / FRONT STAGE ──", SwingConstants.CENTER);
        screenText.setFont(new Font("Segoe UI", Font.BOLD, 11));
        screenText.setForeground(new Color(203, 213, 225));
        screenBanner.add(screenText);

        JPanel centerArea = new JPanel(new BorderLayout(0, 10));
        centerArea.setOpaque(false);
        centerArea.add(screenBanner, BorderLayout.NORTH);

        // Grid Panel with Column & Row Headers
        JPanel gridWithHeaders = new JPanel(new BorderLayout(6, 6));
        gridWithHeaders.setOpaque(false);

        // Column Headers
        JPanel colHeaderPanel = new JPanel(new GridLayout(1, COLS, 8, 0));
        colHeaderPanel.setOpaque(false);
        colHeaderPanel.setBorder(new EmptyBorder(0, 84, 4, 0));
        for (int c = 0; c < COLS; c++) {
            JLabel colLbl = new JLabel("Col " + c + " [Seat " + (c + 1) + "]", SwingConstants.CENTER);
            colLbl.setFont(new Font("Consolas", Font.BOLD, 11));
            colLbl.setForeground(new Color(148, 163, 184));
            colLbl.setBorder(new CompoundBorder(
                    new LineBorder(new Color(51, 65, 85), 1),
                    new EmptyBorder(3, 2, 3, 2)
            ));
            colHeaderPanel.add(colLbl);
        }
        gridWithHeaders.add(colHeaderPanel, BorderLayout.NORTH);

        // Row Headers
        JPanel rowHeaderPanel = new JPanel(new GridLayout(ROWS, 1, 0, 8));
        rowHeaderPanel.setOpaque(false);
        rowHeaderPanel.setPreferredSize(new Dimension(80, 0));
        for (int r = 0; r < ROWS; r++) {
            char rowLetter = (char) ('A' + r);
            JLabel rowLbl = new JLabel("[Row " + r + "] " + rowLetter, SwingConstants.CENTER);
            rowLbl.setFont(new Font("Consolas", Font.BOLD, 11));
            rowLbl.setForeground(new Color(148, 163, 184));
            rowLbl.setBorder(new CompoundBorder(
                    new LineBorder(new Color(51, 65, 85), 1),
                    new EmptyBorder(3, 2, 3, 2)
            ));
            rowHeaderPanel.add(rowLbl);
        }
        gridWithHeaders.add(rowHeaderPanel, BorderLayout.WEST);

        // Matrix Cells Grid
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
        centerArea.add(gridWithHeaders, BorderLayout.CENTER);

        wrapper.add(centerArea, BorderLayout.CENTER);
        return wrapper;
    }

    // =========================================================================
    // TAB 2: BOOKING HASHMAP (HashMap<String, Booking>)
    // =========================================================================
    private JPanel createHashMapTab() {
        JPanel wrapper = new JPanel(new BorderLayout(10, 10));
        wrapper.setBackground(new Color(18, 22, 33));
        wrapper.setBorder(new EmptyBorder(12, 14, 12, 14));

        // 1. Top Specs & Metrics Bar
        JPanel topSpecs = new JPanel(new BorderLayout(8, 8));
        topSpecs.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleRow.setOpaque(false);
        JLabel hmTitle = new JLabel("DATA STRUCTURE: HashMap<String, Booking> bookingMap");
        hmTitle.setFont(new Font("Consolas", Font.BOLD, 14));
        hmTitle.setForeground(new Color(168, 85, 247));
        JLabel hmComplexity = new JLabel("[ O(1) Amortized Average Access & Insertion ]");
        hmComplexity.setFont(new Font("Segoe UI", Font.BOLD, 11));
        hmComplexity.setForeground(new Color(52, 211, 153));
        titleRow.add(hmTitle);
        titleRow.add(hmComplexity);
        topSpecs.add(titleRow, BorderLayout.NORTH);

        // Stats Pills
        JPanel statsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        statsRow.setOpaque(false);

        hmSizeLabel = createPill("Entries (size): 0", new Color(168, 85, 247));
        hmCapacityLabel = createPill("Capacity (buckets): 16", new Color(59, 130, 246));
        hmLoadFactorLabel = createPill("Load Factor: 0.75", new Color(245, 158, 11));
        hmThresholdLabel = createPill("Rehash Threshold: 12", new Color(148, 163, 184));

        statsRow.add(hmSizeLabel);
        statsRow.add(hmCapacityLabel);
        statsRow.add(hmLoadFactorLabel);
        statsRow.add(hmThresholdLabel);
        topSpecs.add(statsRow, BorderLayout.CENTER);

        // Interactive O(1) Search Bar
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        searchBar.setOpaque(false);
        searchBar.setBorder(new CompoundBorder(
                new LineBorder(new Color(51, 65, 85), 1),
                new EmptyBorder(4, 8, 4, 8)
        ));

        JLabel searchPrompt = new JLabel("Key Search (get(key)):");
        searchPrompt.setFont(new Font("Segoe UI", Font.BOLD, 12));
        searchPrompt.setForeground(new Color(226, 232, 240));

        hmSearchField = new JTextField("BK1001", 12);
        hmSearchField.setFont(new Font("Consolas", Font.PLAIN, 12));
        hmSearchField.setBackground(new Color(15, 23, 42));
        hmSearchField.setForeground(Color.WHITE);
        hmSearchField.setCaretColor(Color.WHITE);

        hmSearchBtn = createStyledButton("Search get(k)", new Color(139, 92, 246));
        hmSearchBtn.addActionListener(e -> performHashMapLookup(hmSearchField.getText().trim()));

        hmSearchResultLabel = new JLabel("Enter a Booking ID and test O(1) hash lookup.");
        hmSearchResultLabel.setFont(new Font("Consolas", Font.PLAIN, 11));
        hmSearchResultLabel.setForeground(new Color(148, 163, 184));

        searchBar.add(searchPrompt);
        searchBar.add(hmSearchField);
        searchBar.add(hmSearchBtn);
        searchBar.add(hmSearchResultLabel);

        topSpecs.add(searchBar, BorderLayout.SOUTH);
        wrapper.add(topSpecs, BorderLayout.NORTH);

        // 2. Center: Split into Visual Buckets (16 Hash Buckets) and Bookings Record Cards
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setOpaque(false);
        splitPane.setDividerLocation(180);
        splitPane.setDividerSize(4);
        splitPane.setBorder(null);

        // Upper: Visual Buckets Distribution (Table of 16 Buckets)
        JPanel bucketsWrapper = new JPanel(new BorderLayout(4, 4));
        bucketsWrapper.setOpaque(false);
        JLabel bucketsTitle = new JLabel("HASH TABLE BUCKET MAPPING [Math.abs(key.hashCode()) % 16]:");
        bucketsTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        bucketsTitle.setForeground(new Color(203, 213, 225));
        bucketsWrapper.add(bucketsTitle, BorderLayout.NORTH);

        hmBucketsPanel = new JPanel(new GridLayout(2, 8, 6, 6));
        hmBucketsPanel.setOpaque(false);
        bucketsWrapper.add(new JScrollPane(hmBucketsPanel), BorderLayout.CENTER);
        splitPane.setTopComponent(bucketsWrapper);

        // Lower: Real Booking Records Cards List
        JPanel entriesWrapper = new JPanel(new BorderLayout(4, 4));
        entriesWrapper.setOpaque(false);
        JLabel entriesTitle = new JLabel("LIVE BOOKING OBJECTS (Stored in backend bookingMap):");
        entriesTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        entriesTitle.setForeground(new Color(203, 213, 225));
        entriesWrapper.add(entriesTitle, BorderLayout.NORTH);

        hmEntriesListPanel = new JPanel();
        hmEntriesListPanel.setLayout(new BoxLayout(hmEntriesListPanel, BoxLayout.Y_AXIS));
        hmEntriesListPanel.setBackground(new Color(15, 23, 42));

        JScrollPane scrollEntries = new JScrollPane(hmEntriesListPanel);
        scrollEntries.setBorder(new LineBorder(new Color(51, 65, 85), 1));
        entriesWrapper.add(scrollEntries, BorderLayout.CENTER);
        splitPane.setBottomComponent(entriesWrapper);

        wrapper.add(splitPane, BorderLayout.CENTER);
        return wrapper;
    }

    private void performHashMapLookup(String key) {
        if (key.isEmpty()) {
            hmSearchResultLabel.setText("Please enter a non-empty booking ID.");
            return;
        }

        int hash = key.hashCode();
        int bucketIndex = Math.abs(hash) % HASHMAP_CAPACITY;
        boolean exists = currentBookings.containsKey(key);

        if (exists) {
            BookingDto b = currentBookings.get(key);
            highlightedBookingKey = key;
            hmSearchResultLabel.setText(String.format(
                    "FOUND in O(1)! hash=%d -> Bucket [%d] | Customer: %s, Seats: %s, Status: %s",
                    hash, bucketIndex, b.customerName, b.bookedSeats, b.status
            ));
            hmSearchResultLabel.setForeground(new Color(52, 211, 153));
            log("HASHMAP", String.format("Lookup get(\"%s\") -> FOUND in O(1) at Bucket [%d]. Customer: %s", key, bucketIndex, b.customerName));
        } else {
            highlightedBookingKey = null;
            hmSearchResultLabel.setText(String.format(
                    "NOT FOUND (O(1)): hash=%d -> Bucket [%d] contains null/no match for key '%s'",
                    hash, bucketIndex, key
            ));
            hmSearchResultLabel.setForeground(new Color(248, 113, 113));
            log("HASHMAP", String.format("Lookup get(\"%s\") -> NOT FOUND (Bucket [%d] checked in O(1))", key, bucketIndex));
        }

        renderHashMapBuckets();
        renderHashMapEntries();
    }

    // =========================================================================
    // TAB 3: WAITING QUEUE (Custom FIFO LinkedList Queue)
    // =========================================================================
    private JPanel createQueueTab() {
        JPanel wrapper = new JPanel(new BorderLayout(10, 10));
        wrapper.setBackground(new Color(18, 22, 33));
        wrapper.setBorder(new EmptyBorder(12, 14, 12, 14));

        // 1. Top Specs & FIFO Metrics
        JPanel topSpecs = new JPanel(new BorderLayout(8, 8));
        topSpecs.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleRow.setOpaque(false);
        JLabel qTitle = new JLabel("DATA STRUCTURE: Custom FIFO LinkedList Queue (WaitingList)");
        qTitle.setFont(new Font("Consolas", Font.BOLD, 14));
        qTitle.setForeground(new Color(56, 189, 248)); // Sky Blue
        JLabel qComplexity = new JLabel("[ Enqueue at REAR: O(1) • Dequeue from FRONT: O(1) ]");
        qComplexity.setFont(new Font("Segoe UI", Font.BOLD, 11));
        qComplexity.setForeground(new Color(52, 211, 153));
        titleRow.add(qTitle);
        titleRow.add(qComplexity);
        topSpecs.add(titleRow, BorderLayout.NORTH);

        // Stats Pills
        JPanel statsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        statsRow.setOpaque(false);

        qSizeLabel = createPill("Queue Size: 0", new Color(56, 189, 248));
        qFrontLabel = createPill("FRONT (peek): None", new Color(168, 85, 247));
        qRearLabel = createPill("REAR: None", new Color(236, 72, 153));
        qEmptyStatusLabel = createPill("Status: EMPTY", new Color(148, 163, 184));

        statsRow.add(qSizeLabel);
        statsRow.add(qFrontLabel);
        statsRow.add(qRearLabel);
        statsRow.add(qEmptyStatusLabel);
        topSpecs.add(statsRow, BorderLayout.CENTER);

        // Test Interactive Enqueue Bar
        JPanel enqueueBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        enqueueBar.setOpaque(false);
        enqueueBar.setBorder(new CompoundBorder(
                new LineBorder(new Color(51, 65, 85), 1),
                new EmptyBorder(4, 8, 4, 8)
        ));

        JLabel enqPrompt = new JLabel("Test Enqueue (POST /api/bookings/waitlist):");
        enqPrompt.setFont(new Font("Segoe UI", Font.BOLD, 12));
        enqPrompt.setForeground(new Color(226, 232, 240));

        qCustNameField = new JTextField("Alice Walker", 12);
        qCustNameField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        qCustNameField.setBackground(new Color(15, 23, 42));
        qCustNameField.setForeground(Color.WHITE);

        JLabel seatsPrompt = new JLabel("Seats:");
        seatsPrompt.setForeground(new Color(148, 163, 184));
        qSeatsCombo = new JComboBox<>(new Integer[]{1, 2, 3, 4, 5});
        qSeatsCombo.setBackground(new Color(15, 23, 42));
        qSeatsCombo.setForeground(Color.WHITE);

        qEnqueueBtn = createStyledButton("Enqueue at REAR ➔", new Color(14, 165, 233));
        qEnqueueBtn.addActionListener(e -> testEnqueueCustomer());

        qActionFeedbackLabel = new JLabel("Enter a customer name to enqueue into the real backend queue.");
        qActionFeedbackLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        qActionFeedbackLabel.setForeground(new Color(148, 163, 184));

        enqueueBar.add(enqPrompt);
        enqueueBar.add(qCustNameField);
        enqueueBar.add(seatsPrompt);
        enqueueBar.add(qSeatsCombo);
        enqueueBar.add(qEnqueueBtn);
        enqueueBar.add(qActionFeedbackLabel);

        topSpecs.add(enqueueBar, BorderLayout.SOUTH);
        wrapper.add(topSpecs, BorderLayout.NORTH);

        // 2. Center: Visual FIFO Pipeline (Horizontal Sequence of Nodes)
        JPanel pipelineWrapper = new JPanel(new BorderLayout(8, 8));
        pipelineWrapper.setOpaque(false);

        JLabel pipelineTitle = new JLabel("FIFO ORDER: FRONT (Served First on Seat Cancellation) ──────► REAR (Joined Last)");
        pipelineTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        pipelineTitle.setForeground(new Color(203, 213, 225));
        pipelineWrapper.add(pipelineTitle, BorderLayout.NORTH);

        qVisualPipelinePanel = new JPanel();
        qVisualPipelinePanel.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 12));
        qVisualPipelinePanel.setBackground(new Color(15, 23, 42));

        JScrollPane scrollPipeline = new JScrollPane(qVisualPipelinePanel);
        scrollPipeline.setBorder(new LineBorder(new Color(51, 65, 85), 1));
        pipelineWrapper.add(scrollPipeline, BorderLayout.CENTER);

        // Lower: Queue Principles / Explanations
        JPanel explanationPanel = new JPanel(new GridLayout(1, 2, 8, 8));
        explanationPanel.setOpaque(false);
        explanationPanel.setPreferredSize(new Dimension(0, 100));

        JTextArea exp1 = createStyledTextArea(
                "WHY FIFO QUEUE IS USED FOR TICKETING:\n" +
                "• Strict First-Come First-Served fairness: The earlier a customer joins, the sooner they get a seat.\n" +
                "• In CineBook, when a confirmed booking is cancelled, processWaitlist() peeks FRONT in O(1).\n" +
                "• If available seats >= seatsRequested, the customer is dequeued in O(1) and auto-booked!"
        );
        JTextArea exp2 = createStyledTextArea(
                "COMPLEXITY & POINTER MECHANISM:\n" +
                "• front: Reference to head Node (next customer to be dequeued).\n" +
                "• rear: Reference to tail Node (where newly enqueued requests attach).\n" +
                "• enqueue: rear.next = newNode; rear = newNode (O(1) time).\n" +
                "• dequeue: front = front.next (O(1) time, zero array shifting needed)."
        );
        explanationPanel.add(exp1);
        explanationPanel.add(exp2);
        pipelineWrapper.add(explanationPanel, BorderLayout.SOUTH);

        wrapper.add(pipelineWrapper, BorderLayout.CENTER);
        return wrapper;
    }

    private void testEnqueueCustomer() {
        String name = qCustNameField.getText().trim();
        if (name.isEmpty()) {
            qActionFeedbackLabel.setText("Please enter a customer name.");
            return;
        }
        int seats = (Integer) qSeatsCombo.getSelectedItem();
        qEnqueueBtn.setEnabled(false);
        qActionFeedbackLabel.setText("Enqueuing customer to backend...");

        new Thread(() -> {
            try {
                String url = backendUrl.replaceAll("/+$", "") + "/api/bookings/waitlist";
                String body = String.format("{\"customerName\":\"%s\",\"showId\":\"%s\",\"seatsRequested\":%d}", name, selectedShowId, seats);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                SwingUtilities.invokeLater(() -> {
                    qEnqueueBtn.setEnabled(true);
                    if (resp.statusCode() == 200) {
                        qActionFeedbackLabel.setText("Enqueued " + name + " (" + seats + " seats) at REAR successfully!");
                        qActionFeedbackLabel.setForeground(new Color(52, 211, 153));
                        refreshAllDsaData();
                    } else {
                        qActionFeedbackLabel.setText("Error enqueuing: HTTP " + resp.statusCode());
                        qActionFeedbackLabel.setForeground(new Color(248, 113, 113));
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    qEnqueueBtn.setEnabled(true);
                    qActionFeedbackLabel.setText("Failed: " + ex.getMessage());
                    qActionFeedbackLabel.setForeground(new Color(248, 113, 113));
                });
            }
        }).start();
    }

    // =========================================================================
    // TAB 4: OCCUPIED SEATS HASHSET (HashSet<String>)
    // =========================================================================
    private JPanel createHashSetTab() {
        JPanel wrapper = new JPanel(new BorderLayout(10, 10));
        wrapper.setBackground(new Color(18, 22, 33));
        wrapper.setBorder(new EmptyBorder(12, 14, 12, 14));

        // Top Specs Bar
        JPanel topSpecs = new JPanel(new BorderLayout(8, 8));
        topSpecs.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleRow.setOpaque(false);
        JLabel hsTitle = new JLabel("DATA STRUCTURE: HashSet<String> occupiedSeatsSet");
        hsTitle.setFont(new Font("Consolas", Font.BOLD, 14));
        hsTitle.setForeground(new Color(244, 63, 94)); // Rose
        JLabel hsComplexity = new JLabel("[ O(1) Instant Duplicate Booking Prevention ]");
        hsComplexity.setFont(new Font("Segoe UI", Font.BOLD, 11));
        hsComplexity.setForeground(new Color(52, 211, 153));
        titleRow.add(hsTitle);
        titleRow.add(hsComplexity);
        topSpecs.add(titleRow, BorderLayout.NORTH);

        hsSizeLabel = createPill("Occupied Seats Count: 0", new Color(244, 63, 94));
        topSpecs.add(hsSizeLabel, BorderLayout.CENTER);

        // Interactive set.contains(seat) search bar
        JPanel checkBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        checkBar.setOpaque(false);
        checkBar.setBorder(new CompoundBorder(
                new LineBorder(new Color(51, 65, 85), 1),
                new EmptyBorder(4, 8, 4, 8)
        ));

        JLabel checkPrompt = new JLabel("Test Duplicate Conflict (contains(seat)):");
        checkPrompt.setFont(new Font("Segoe UI", Font.BOLD, 12));
        checkPrompt.setForeground(new Color(226, 232, 240));

        hsCheckField = new JTextField("B2", 6);
        hsCheckField.setFont(new Font("Consolas", Font.PLAIN, 12));
        hsCheckField.setBackground(new Color(15, 23, 42));
        hsCheckField.setForeground(Color.WHITE);

        hsCheckBtn = createStyledButton("Check set.contains()", new Color(225, 29, 72));
        hsCheckBtn.addActionListener(e -> checkSeatSetMembership(hsCheckField.getText().trim().toUpperCase()));

        hsCheckResultLabel = new JLabel("Enter seat code (e.g., 'B2') to test O(1) set membership collision test.");
        hsCheckResultLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        hsCheckResultLabel.setForeground(new Color(148, 163, 184));

        checkBar.add(checkPrompt);
        checkBar.add(hsCheckField);
        checkBar.add(hsCheckBtn);
        checkBar.add(hsCheckResultLabel);

        topSpecs.add(checkBar, BorderLayout.SOUTH);
        wrapper.add(topSpecs, BorderLayout.NORTH);

        // Center: Active Chips Grid
        JPanel centerPanel = new JPanel(new BorderLayout(8, 8));
        centerPanel.setOpaque(false);

        JLabel chipsTitle = new JLabel("CURRENT OCCUPIED SEATS (Active in HashSet):");
        chipsTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        chipsTitle.setForeground(new Color(203, 213, 225));
        centerPanel.add(chipsTitle, BorderLayout.NORTH);

        hsChipsPanel = new JPanel();
        hsChipsPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));
        hsChipsPanel.setBackground(new Color(15, 23, 42));

        JScrollPane scrollChips = new JScrollPane(hsChipsPanel);
        scrollChips.setBorder(new LineBorder(new Color(51, 65, 85), 1));
        centerPanel.add(scrollChips, BorderLayout.CENTER);

        // Lower: Explanation
        JTextArea setExp = createStyledTextArea(
                "WHY HASHSET PREVENTS RACE CONDITIONS & DUPLICATE BOOKINGS:\n" +
                "• When multiple users attempt booking simultaneously on CineBook, the system checks:\n" +
                "  if (occupiedSeatsSet.contains(seatLabel)) -> REJECT IMMEDIATELY with conflict message.\n" +
                "• Checking a HashSet is O(1) time complexity, whereas searching an unindexed list of previous bookings is O(N).\n" +
                "• If valid, the seats are added to the set and marked occupied in the 2D array atomically."
        );
        setExp.setPreferredSize(new Dimension(0, 90));
        centerPanel.add(setExp, BorderLayout.SOUTH);

        wrapper.add(centerPanel, BorderLayout.CENTER);
        return wrapper;
    }

    private void checkSeatSetMembership(String seat) {
        if (seat.isEmpty()) {
            hsCheckResultLabel.setText("Please enter a seat label.");
            return;
        }

        boolean occupied = currentOccupiedSet.contains(seat);
        if (occupied) {
            hsCheckResultLabel.setText(String.format("CONFLICT DETECTED in O(1)! Seat '%s' is ALREADY in occupiedSeatsSet.", seat));
            hsCheckResultLabel.setForeground(new Color(248, 113, 113));
            log("HASHSET", String.format("set.contains(\"%s\") == true (COLLISION PREVENTED in O(1))", seat));
        } else {
            hsCheckResultLabel.setText(String.format("SEAT AVAILABLE! Seat '%s' is NOT in occupiedSeatsSet.", seat));
            hsCheckResultLabel.setForeground(new Color(52, 211, 153));
            log("HASHSET", String.format("set.contains(\"%s\") == false (SEAT AVAILABLE in O(1))", seat));
        }
    }

    // =========================================================================
    // RIGHT INSPECTOR PANEL (GLOBAL DSA METRICS, MUTATION DETECTOR & EVENT LOG)
    // =========================================================================
    private JPanel createRightInspectorPanel() {
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBackground(new Color(22, 27, 38));
        right.setPreferredSize(new Dimension(420, 0));
        right.setBorder(new CompoundBorder(
                new LineBorder(new Color(38, 48, 69), 1),
                new EmptyBorder(12, 12, 12, 12)
        ));

        // 1. SECTION: CURRENT SHOW METRICS
        JLabel sec1 = createSectionTitle("1. REAL-TIME DSA OVERVIEW");
        right.add(sec1);
        right.add(Box.createVerticalStrut(4));

        JPanel metricsCard = new JPanel(new GridLayout(6, 2, 4, 3));
        metricsCard.setOpaque(false);
        metricsCard.setBorder(new EmptyBorder(2, 4, 4, 4));

        metricsCard.add(createPropLabel("Show ID:"));
        showIdVal = createPropVal("S-M1-1");
        metricsCard.add(showIdVal);

        metricsCard.add(createPropLabel("Movie:"));
        movieTitleVal = createPropVal("Interstellar");
        metricsCard.add(movieTitleVal);

        metricsCard.add(createPropLabel("Available (seats[][]=0):"));
        availableCountVal = createPropVal("30 / 30");
        availableCountVal.setForeground(new Color(52, 211, 153));
        metricsCard.add(availableCountVal);

        metricsCard.add(createPropLabel("Booked (seats[][]=1):"));
        bookedCountVal = createPropVal("0 / 30");
        bookedCountVal.setForeground(new Color(248, 113, 113));
        metricsCard.add(bookedCountVal);

        metricsCard.add(createPropLabel("Booking HashMap Size:"));
        totalBookingsVal = createPropVal("0 records");
        totalBookingsVal.setForeground(new Color(168, 85, 247));
        metricsCard.add(totalBookingsVal);

        metricsCard.add(createPropLabel("Waitlist Queue Size:"));
        waitlistCountVal = createPropVal("0 waiting");
        waitlistCountVal.setForeground(new Color(56, 189, 248));
        metricsCard.add(waitlistCountVal);

        right.add(metricsCard);
        right.add(Box.createVerticalStrut(8));
        right.add(new JSeparator(SwingConstants.HORIZONTAL));
        right.add(Box.createVerticalStrut(8));

        // 2. SECTION: LATEST DSA MUTATION & BEFORE/AFTER STATE
        JPanel sec2Header = new JPanel(new BorderLayout());
        sec2Header.setOpaque(false);
        JLabel sec2 = createSectionTitle("2. LATEST DSA OPERATION DETECTED");
        mutationDsaTypeBadge = new JLabel("[ARRAY]");
        mutationDsaTypeBadge.setFont(new Font("Consolas", Font.BOLD, 11));
        mutationDsaTypeBadge.setForeground(new Color(168, 85, 247));
        sec2Header.add(sec2, BorderLayout.WEST);
        sec2Header.add(mutationDsaTypeBadge, BorderLayout.EAST);
        right.add(sec2Header);
        right.add(Box.createVerticalStrut(4));

        JPanel mutPanel = new JPanel();
        mutPanel.setLayout(new BoxLayout(mutPanel, BoxLayout.Y_AXIS));
        mutPanel.setOpaque(false);

        mutationOpLabel = new JLabel("Operation: Initialized [Synchronized with Backend]");
        mutationOpLabel.setFont(new Font("Consolas", Font.BOLD, 12));
        mutationOpLabel.setForeground(new Color(168, 85, 247));

        mutationCoordLabel = new JLabel("Access: Direct Memory Reference O(1)");
        mutationCoordLabel.setFont(new Font("Consolas", Font.PLAIN, 11));
        mutationCoordLabel.setForeground(new Color(226, 232, 240));

        mutationTransitionLabel = new JLabel("Transition: —");
        mutationTransitionLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        mutationTransitionLabel.setForeground(new Color(148, 163, 184));

        mutPanel.add(mutationOpLabel);
        mutPanel.add(Box.createVerticalStrut(2));
        mutPanel.add(mutationCoordLabel);
        mutPanel.add(Box.createVerticalStrut(2));
        mutPanel.add(mutationTransitionLabel);
        mutPanel.add(Box.createVerticalStrut(6));

        // Before & After Visual Badges
        JPanel beforeAfterPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        beforeAfterPanel.setOpaque(false);
        beforeAfterPanel.setBorder(new CompoundBorder(
                new LineBorder(new Color(51, 65, 85), 1),
                new EmptyBorder(4, 8, 4, 8)
        ));

        JPanel rowBefore = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        rowBefore.setOpaque(false);
        JLabel beforeTitle = new JLabel("BEFORE:");
        beforeTitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        beforeTitle.setForeground(new Color(148, 163, 184));
        beforeBadge = new JLabel("Initial State");
        beforeBadge.setFont(new Font("Consolas", Font.BOLD, 11));
        beforeBadge.setForeground(new Color(52, 211, 153));
        rowBefore.add(beforeTitle);
        rowBefore.add(beforeBadge);

        JPanel rowAfter = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        rowAfter.setOpaque(false);
        JLabel afterTitle = new JLabel("AFTER: ");
        afterTitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        afterTitle.setForeground(new Color(148, 163, 184));
        afterBadge = new JLabel("Synchronized");
        afterBadge.setFont(new Font("Consolas", Font.BOLD, 11));
        afterBadge.setForeground(new Color(168, 85, 247));
        rowAfter.add(afterTitle);
        rowAfter.add(afterBadge);

        beforeAfterPanel.add(rowBefore);
        beforeAfterPanel.add(rowAfter);
        mutPanel.add(beforeAfterPanel);
        mutPanel.add(Box.createVerticalStrut(6));

        mutationExplainArea = new JTextArea(4, 28);
        mutationExplainArea.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        mutationExplainArea.setBackground(new Color(15, 23, 42));
        mutationExplainArea.setForeground(new Color(203, 213, 225));
        mutationExplainArea.setLineWrap(true);
        mutationExplainArea.setWrapStyleWord(true);
        mutationExplainArea.setEditable(false);
        mutationExplainArea.setBorder(new EmptyBorder(4, 6, 4, 6));
        mutationExplainArea.setText("Book or cancel a seat on the CineBook website, or test enqueue in the visualizer. Real DSA mutations will be analyzed here in real-time.");
        mutPanel.add(mutationExplainArea);

        right.add(mutPanel);
        right.add(Box.createVerticalStrut(8));
        right.add(new JSeparator(SwingConstants.HORIZONTAL));
        right.add(Box.createVerticalStrut(8));

        // 3. SECTION: LIVE DSA EVENT LOG
        JPanel logHeader = new JPanel(new BorderLayout());
        logHeader.setOpaque(false);
        JLabel sec3 = createSectionTitle("3. REAL-TIME DSA EVENT STREAM");
        JButton clearLogBtn = new JButton("Clear");
        clearLogBtn.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        clearLogBtn.setMargin(new Insets(1, 4, 1, 4));
        clearLogBtn.addActionListener(e -> eventLogArea.setText(""));
        logHeader.add(sec3, BorderLayout.WEST);
        logHeader.add(clearLogBtn, BorderLayout.EAST);
        right.add(logHeader);
        right.add(Box.createVerticalStrut(4));

        eventLogArea = new JTextArea();
        eventLogArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        eventLogArea.setBackground(new Color(11, 15, 25));
        eventLogArea.setForeground(new Color(192, 132, 252)); // Light purple
        eventLogArea.setEditable(false);
        eventLogArea.setMargin(new Insets(4, 6, 4, 6));

        JScrollPane logScroll = new JScrollPane(eventLogArea);
        logScroll.setPreferredSize(new Dimension(380, 200));
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
        l.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        l.setForeground(new Color(148, 163, 184));
        return l;
    }

    private JLabel createPropVal(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Consolas", Font.BOLD, 12));
        l.setForeground(Color.WHITE);
        return l;
    }

    private JPanel createFooterBar() {
        JPanel footer = new JPanel(new BorderLayout(10, 0));
        footer.setBackground(new Color(15, 23, 42));
        footer.setBorder(new CompoundBorder(
                new LineBorder(new Color(38, 48, 69), 1),
                new EmptyBorder(6, 16, 6, 16)
        ));

        JLabel left = new JLabel("DSA STRUCTURES: int[5][6] Array (O(1))  •  HashMap<String, Booking> (O(1))  •  FIFO Queue (O(1))  •  HashSet (O(1))");
        left.setFont(new Font("Consolas", Font.PLAIN, 11));
        left.setForeground(new Color(148, 163, 184));

        JLabel right = new JLabel("Single Source of Truth: Spring Boot Backend  •  CineBook Smart Movie Ticket Booking System");
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
                refreshAllDsaData();
            }
        });
        pollTimer.start();
    }

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
                        log("SYSTEM", "Connected to backend at " + backendUrl + ". Loaded " + shows.size() + " shows.");
                        refreshAllDsaData();
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        updateConnectionStatus(false);
                        log("ERROR", "HTTP Error " + response.statusCode() + " fetching shows.");
                    });
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    updateConnectionStatus(false);
                    log("ERROR", "Connection failed: " + ex.getMessage());
                });
            }
        }).start();
    }

    /**
     * Unified polling call: fetches all DSA structures from /api/bookings/dsa-state/{showId}
     */
    private void refreshAllDsaData() {
        if (isPolling) return;
        isPolling = true;

        new Thread(() -> {
            try {
                String url = backendUrl.replaceAll("/+$", "") + "/api/bookings/dsa-state/" + selectedShowId;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String json = response.body();

                    // 1. Parse Seat Matrix
                    int[][] newSeats = parseSeatMatrixJson(json);
                    int availableCount = parseJsonInt(json, "availableCount", 30);
                    int totalSeats = parseJsonInt(json, "totalSeats", 30);

                    // 2. Parse HashMap Entries
                    Map<String, BookingDto> newBookings = parseBookingsJson(json);

                    // 3. Parse Queue Elements
                    List<WaitingNodeDto> newQueue = parseQueueJson(json);

                    // 4. Parse HashSet Elements
                    Set<String> newOccupiedSet = parseHashSetJson(json);

                    SwingUtilities.invokeLater(() -> {
                        applyUnifiedDsaUpdate(newSeats, availableCount, totalSeats, newBookings, newQueue, newOccupiedSet);
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
     * Synchronizes and diffs all four data structures atomically.
     */
    private void applyUnifiedDsaUpdate(
            int[][] newSeats, int availableCount, int totalSeats,
            Map<String, BookingDto> newBookings,
            List<WaitingNodeDto> newQueue,
            Set<String> newOccupiedSet) {

        // =====================================================================
        // 1. DIFF & UPDATE 2D SEAT ARRAY
        // =====================================================================
        recentlyChangedCells.clear();
        if (hasPreviousArrayState) {
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    int oldVal = previousSeats[r][c];
                    int newVal = newSeats[r][c];
                    if (oldVal != newVal) {
                        recentlyChangedCells.add(new Point(r, c));
                        char rowChar = (char) ('A' + r);
                        String seatLabel = "" + rowChar + (c + 1);

                        if (oldVal == 0 && newVal == 1) {
                            String msg = String.format("seats[%d][%d] = 1 (Seat %s BOOKED)", r, c, seatLabel);
                            log("ARRAY", msg);
                            showArrayMutationDetails(r, c, seatLabel, 0, 1, "BOOKING");
                        } else if (oldVal == 1 && newVal == 0) {
                            String msg = String.format("seats[%d][%d] = 0 (Seat %s CANCELLED & Freed)", r, c, seatLabel);
                            log("ARRAY", msg);
                            showArrayMutationDetails(r, c, seatLabel, 1, 0, "CANCELLATION");
                        }
                    }
                }
            }
        } else {
            hasPreviousArrayState = true;
            log("ARRAY", "2D Seat Array initialized for show " + selectedShowId);
        }

        // Copy new seat matrix
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

        // =====================================================================
        // 2. DIFF & UPDATE BOOKING HASHMAP
        // =====================================================================
        if (hasPreviousHashMapState) {
            // Check for new bookings added (put)
            for (Map.Entry<String, BookingDto> entry : newBookings.entrySet()) {
                String k = entry.getKey();
                BookingDto b = entry.getValue();
                if (!previousBookings.containsKey(k)) {
                    log("HASHMAP", String.format("bookingMap.put(\"%s\", Booking{customer='%s', seats=%s}) in O(1)", k, b.customerName, b.bookedSeats));
                    showHashMapMutationDetails(k, b, "PUT");
                } else {
                    BookingDto prevB = previousBookings.get(k);
                    if (!prevB.status.equals(b.status)) {
                        log("HASHMAP", String.format("bookingMap.get(\"%s\").setStatus(\"%s\") updated in O(1)", k, b.status));
                        showHashMapMutationDetails(k, b, "STATUS_CHANGE");
                    }
                }
            }
        } else {
            hasPreviousHashMapState = true;
            log("HASHMAP", "Booking HashMap synchronized (" + newBookings.size() + " total entries).");
        }

        currentBookings.clear();
        currentBookings.putAll(newBookings);
        previousBookings.clear();
        previousBookings.putAll(newBookings);

        renderHashMapBuckets();
        renderHashMapEntries();

        // =====================================================================
        // 3. DIFF & UPDATE WAITING QUEUE
        // =====================================================================
        if (hasPreviousQueueState) {
            if (newQueue.size() > previousQueue.size()) {
                // Customer enqueued at REAR
                WaitingNodeDto newest = newQueue.get(newQueue.size() - 1);
                newlyEnqueuedCustomer = newest.customerName;
                log("QUEUE", String.format("waitlist.enqueue(\"%s\", %d seats) appended at REAR in O(1) (Size=%d)", newest.customerName, newest.seatsRequested, newQueue.size()));
                showQueueMutationDetails(newest, "ENQUEUE", previousQueue.size(), newQueue.size());
            } else if (newQueue.size() < previousQueue.size()) {
                // Customer dequeued from FRONT
                WaitingNodeDto removed = previousQueue.get(0);
                newlyDequeuedCustomer = removed.customerName;
                log("QUEUE", String.format("waitlist.dequeue() -> Customer \"%s\" served from FRONT in O(1) (Auto-allocated seats)", removed.customerName));
                showQueueMutationDetails(removed, "DEQUEUE", previousQueue.size(), newQueue.size());
            }
        } else {
            hasPreviousQueueState = true;
            log("QUEUE", "Waiting Queue synchronized (" + newQueue.size() + " in waitlist).");
        }

        currentQueue.clear();
        currentQueue.addAll(newQueue);
        previousQueue.clear();
        previousQueue.addAll(newQueue);

        renderQueuePipeline();

        // =====================================================================
        // 4. DIFF & UPDATE OCCUPIED SEATS HASHSET
        // =====================================================================
        currentOccupiedSet.clear();
        currentOccupiedSet.addAll(newOccupiedSet);
        previousOccupiedSet.clear();
        previousOccupiedSet.addAll(newOccupiedSet);

        renderOccupiedHashSet();

        // =====================================================================
        // 5. UPDATE OVERVIEW METRICS & TIMESTAMP
        // =====================================================================
        int bookedCount = totalSeats - availableCount;
        double rate = (totalSeats > 0) ? ((double) bookedCount / totalSeats * 100.0) : 0.0;

        showIdVal.setText(selectedShowId);
        gridTitleLabel.setText("MEMORY VIEW: int[5][6] seats — Show: " + selectedShowId);
        availableCountVal.setText(availableCount + " / " + totalSeats);
        bookedCountVal.setText(bookedCount + " / " + totalSeats);
        occupancyRateVal.setText(String.format("%.1f%%", rate));
        totalBookingsVal.setText(currentBookings.size() + " in bookingMap");
        waitlistCountVal.setText(currentQueue.size() + " in waitlistMap");

        // Sync timestamp
        String now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        lastSyncLabel.setText("Synced: " + now);
    }

    // =========================================================================
    // RENDER HELPERS: HASHMAP TAB
    // =========================================================================
    private void renderHashMapBuckets() {
        hmSizeLabel.setText("Entries (size): " + currentBookings.size());
        hmBucketsPanel.removeAll();

        // Map entries to 16 buckets
        Map<Integer, List<BookingDto>> bucketMap = new HashMap<>();
        for (int i = 0; i < HASHMAP_CAPACITY; i++) {
            bucketMap.put(i, new ArrayList<>());
        }
        for (BookingDto b : currentBookings.values()) {
            int bucket = Math.abs(b.bookingId.hashCode()) % HASHMAP_CAPACITY;
            bucketMap.get(bucket).add(b);
        }

        for (int i = 0; i < HASHMAP_CAPACITY; i++) {
            List<BookingDto> list = bucketMap.get(i);
            JPanel bPanel = new JPanel(new BorderLayout(2, 2));
            bPanel.setBackground(list.isEmpty() ? new Color(24, 30, 43) : new Color(37, 28, 59));
            bPanel.setBorder(new CompoundBorder(
                    new LineBorder(list.isEmpty() ? new Color(51, 65, 85) : new Color(168, 85, 247), 1, true),
                    new EmptyBorder(4, 6, 4, 6)
            ));

            JLabel title = new JLabel("Bucket [" + i + "]", SwingConstants.CENTER);
            title.setFont(new Font("Consolas", Font.BOLD, 10));
            title.setForeground(list.isEmpty() ? new Color(148, 163, 184) : new Color(236, 72, 153));
            bPanel.add(title, BorderLayout.NORTH);

            if (list.isEmpty()) {
                JLabel emptyLbl = new JLabel("null", SwingConstants.CENTER);
                emptyLbl.setFont(new Font("Consolas", Font.PLAIN, 10));
                emptyLbl.setForeground(new Color(100, 116, 139));
                bPanel.add(emptyLbl, BorderLayout.CENTER);
            } else {
                JPanel chainPanel = new JPanel(new GridLayout(list.size(), 1, 0, 1));
                chainPanel.setOpaque(false);
                for (BookingDto b : list) {
                    boolean isMatch = b.bookingId.equals(highlightedBookingKey);
                    JLabel node = new JLabel("➔ " + b.bookingId, SwingConstants.CENTER);
                    node.setFont(new Font("Consolas", Font.BOLD, 10));
                    node.setForeground(isMatch ? new Color(52, 211, 153) : Color.WHITE);
                    chainPanel.add(node);
                }
                bPanel.add(chainPanel, BorderLayout.CENTER);
            }

            hmBucketsPanel.add(bPanel);
        }

        hmBucketsPanel.revalidate();
        hmBucketsPanel.repaint();
    }

    private void renderHashMapEntries() {
        hmEntriesListPanel.removeAll();

        if (currentBookings.isEmpty()) {
            JPanel emptyPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 20));
            emptyPanel.setOpaque(false);
            JLabel emptyLbl = new JLabel("No booking records in backend bookingMap yet. Book tickets on CineBook to populate.");
            emptyLbl.setFont(new Font("Segoe UI", Font.ITALIC, 12));
            emptyLbl.setForeground(new Color(148, 163, 184));
            emptyPanel.add(emptyLbl);
            hmEntriesListPanel.add(emptyPanel);
        } else {
            for (BookingDto b : currentBookings.values()) {
                boolean isMatch = b.bookingId.equals(highlightedBookingKey);
                JPanel card = new JPanel(new BorderLayout(8, 4));
                card.setBackground(isMatch ? new Color(48, 30, 68) : new Color(22, 27, 38));
                card.setBorder(new CompoundBorder(
                        new LineBorder(isMatch ? new Color(168, 85, 247) : new Color(51, 65, 85), isMatch ? 2 : 1, true),
                        new EmptyBorder(8, 12, 8, 12)
                ));

                // Left: Key badge & Bucket
                int bucket = Math.abs(b.bookingId.hashCode()) % HASHMAP_CAPACITY;
                JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
                left.setOpaque(false);

                JLabel keyBadge = new JLabel("KEY: " + b.bookingId);
                keyBadge.setFont(new Font("Consolas", Font.BOLD, 13));
                keyBadge.setForeground(new Color(168, 85, 247));

                JLabel bucketBadge = new JLabel("[Bucket " + bucket + "]");
                bucketBadge.setFont(new Font("Consolas", Font.PLAIN, 11));
                bucketBadge.setForeground(new Color(148, 163, 184));

                left.add(keyBadge);
                left.add(bucketBadge);
                card.add(left, BorderLayout.WEST);

                // Center: Details
                JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
                center.setOpaque(false);

                JLabel custLbl = new JLabel("Customer: " + b.customerName);
                custLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
                custLbl.setForeground(Color.WHITE);

                JLabel showLbl = new JLabel("Show: " + b.showId + " (" + b.showTime + ")");
                showLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                showLbl.setForeground(new Color(203, 213, 225));

                JLabel seatsLbl = new JLabel("Seats: " + b.bookedSeats);
                seatsLbl.setFont(new Font("Consolas", Font.BOLD, 12));
                seatsLbl.setForeground(new Color(56, 189, 248));

                JLabel amountLbl = new JLabel("Total: ₹" + b.totalAmount);
                amountLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
                amountLbl.setForeground(new Color(245, 158, 11));

                center.add(custLbl);
                center.add(showLbl);
                center.add(seatsLbl);
                center.add(amountLbl);
                card.add(center, BorderLayout.CENTER);

                // Right: Status Badge
                boolean confirmed = "CONFIRMED".equalsIgnoreCase(b.status);
                JLabel statusBadge = new JLabel(b.status, SwingConstants.CENTER);
                statusBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
                statusBadge.setOpaque(true);
                statusBadge.setBackground(confirmed ? new Color(6, 78, 59) : new Color(127, 29, 29));
                statusBadge.setForeground(confirmed ? new Color(110, 231, 183) : new Color(254, 202, 202));
                statusBadge.setBorder(new EmptyBorder(3, 8, 3, 8));
                card.add(statusBadge, BorderLayout.EAST);

                hmEntriesListPanel.add(card);
                hmEntriesListPanel.add(Box.createVerticalStrut(4));
            }
        }

        hmEntriesListPanel.revalidate();
        hmEntriesListPanel.repaint();
    }

    // =========================================================================
    // RENDER HELPERS: QUEUE TAB
    // =========================================================================
    private void renderQueuePipeline() {
        qSizeLabel.setText("Queue Size: " + currentQueue.size());
        qEmptyStatusLabel.setText(currentQueue.isEmpty() ? "Status: EMPTY" : "Status: ACTIVE WAITING LIST");
        qEmptyStatusLabel.setForeground(currentQueue.isEmpty() ? new Color(148, 163, 184) : new Color(52, 211, 153));

        if (!currentQueue.isEmpty()) {
            qFrontLabel.setText("FRONT: " + currentQueue.get(0).customerName + " (" + currentQueue.get(0).seatsRequested + " seats)");
            qRearLabel.setText("REAR: " + currentQueue.get(currentQueue.size() - 1).customerName);
        } else {
            qFrontLabel.setText("FRONT (peek): None");
            qRearLabel.setText("REAR: None");
        }

        qVisualPipelinePanel.removeAll();

        if (currentQueue.isEmpty()) {
            JPanel emptyCard = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 24));
            emptyCard.setOpaque(false);
            JLabel emptyLbl = new JLabel("✨ WAITING LIST IS CURRENTLY EMPTY (0 customers waiting for " + selectedShowId + "). All seat requests are fulfilled.");
            emptyLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
            emptyLbl.setForeground(new Color(148, 163, 184));
            emptyCard.add(emptyLbl);
            qVisualPipelinePanel.add(emptyCard);
        } else {
            for (int i = 0; i < currentQueue.size(); i++) {
                WaitingNodeDto node = currentQueue.get(i);
                boolean isFront = (i == 0);
                boolean isRear = (i == currentQueue.size() - 1);

                JPanel nodeCard = new JPanel(new BorderLayout(4, 4));
                nodeCard.setPreferredSize(new Dimension(220, 110));
                nodeCard.setBackground(isFront ? new Color(28, 44, 69) : new Color(22, 27, 38));
                nodeCard.setBorder(new CompoundBorder(
                        new LineBorder(isFront ? new Color(56, 189, 248) : new Color(51, 65, 85), isFront ? 2 : 1, true),
                        new EmptyBorder(8, 10, 8, 10)
                ));

                // Position Tag: FRONT / REAR / Node #i
                JPanel tagPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                tagPanel.setOpaque(false);
                if (isFront) {
                    JLabel fBadge = createMiniBadge("★ FRONT (Head)", new Color(56, 189, 248));
                    tagPanel.add(fBadge);
                }
                if (isRear) {
                    JLabel rBadge = createMiniBadge("REAR (Tail)", new Color(236, 72, 153));
                    tagPanel.add(rBadge);
                }
                if (!isFront && !isRear) {
                    JLabel nBadge = createMiniBadge("Node [" + i + "]", new Color(148, 163, 184));
                    tagPanel.add(nBadge);
                }
                nodeCard.add(tagPanel, BorderLayout.NORTH);

                // Body: Customer & Seats
                JPanel body = new JPanel(new GridLayout(3, 1, 0, 2));
                body.setOpaque(false);

                JLabel nameLbl = new JLabel(node.customerName);
                nameLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
                nameLbl.setForeground(Color.WHITE);

                JLabel seatsLbl = new JLabel("Seats Requested: " + node.seatsRequested);
                seatsLbl.setFont(new Font("Consolas", Font.BOLD, 11));
                seatsLbl.setForeground(new Color(245, 158, 11));

                JLabel timeLbl = new JLabel("Joined: " + node.joinedAt);
                timeLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                timeLbl.setForeground(new Color(148, 163, 184));

                body.add(nameLbl);
                body.add(seatsLbl);
                body.add(timeLbl);
                nodeCard.add(body, BorderLayout.CENTER);

                qVisualPipelinePanel.add(nodeCard);

                // Arrow Connector to next node
                if (i < currentQueue.size() - 1) {
                    JLabel arrow = new JLabel("──────►");
                    arrow.setFont(new Font("Consolas", Font.BOLD, 16));
                    arrow.setForeground(new Color(148, 163, 184));
                    qVisualPipelinePanel.add(arrow);
                }
            }
        }

        qVisualPipelinePanel.revalidate();
        qVisualPipelinePanel.repaint();
    }

    // =========================================================================
    // RENDER HELPERS: HASHSET TAB
    // =========================================================================
    private void renderOccupiedHashSet() {
        hsSizeLabel.setText("Occupied Seats Count: " + currentOccupiedSet.size());
        hsChipsPanel.removeAll();

        if (currentOccupiedSet.isEmpty()) {
            JLabel emptyLbl = new JLabel("Set is currently empty. No seats occupied for this show.");
            emptyLbl.setFont(new Font("Segoe UI", Font.ITALIC, 12));
            emptyLbl.setForeground(new Color(148, 163, 184));
            hsChipsPanel.add(emptyLbl);
        } else {
            for (String seat : currentOccupiedSet) {
                JPanel chip = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
                chip.setBackground(new Color(45, 23, 31));
                chip.setBorder(new CompoundBorder(
                        new LineBorder(new Color(244, 63, 94), 1, true),
                        new EmptyBorder(3, 8, 3, 8)
                ));

                JLabel lbl = new JLabel("Seat: " + seat);
                lbl.setFont(new Font("Consolas", Font.BOLD, 12));
                lbl.setForeground(new Color(254, 202, 202));
                chip.add(lbl);

                hsChipsPanel.add(chip);
            }
        }

        hsChipsPanel.revalidate();
        hsChipsPanel.repaint();
    }

    // =========================================================================
    // MUTATION EXPLANATIONS
    // =========================================================================
    private void showArrayMutationDetails(int row, int col, String label, int oldVal, int newVal, String type) {
        mutationDsaTypeBadge.setText("[ARRAY]");
        mutationDsaTypeBadge.setForeground(new Color(168, 85, 247));

        mutationOpLabel.setText("Operation: seats[" + row + "][" + col + "] = " + newVal + ";");
        mutationCoordLabel.setText("Array Access: Row " + row + " ('" + label.charAt(0) + "'), Col " + col + " (Seat " + (col + 1) + ")");
        mutationTransitionLabel.setText("Transition: " + (oldVal == 0 ? "AVAILABLE (0)" : "BOOKED (1)") + " -> " + (newVal == 0 ? "AVAILABLE (0)" : "BOOKED (1)"));

        beforeBadge.setText("val = " + oldVal + " (" + (oldVal == 0 ? "AVAILABLE" : "BOOKED") + ")");
        beforeBadge.setForeground(oldVal == 0 ? new Color(52, 211, 153) : new Color(248, 113, 113));

        afterBadge.setText("val = " + newVal + " (" + (newVal == 0 ? "AVAILABLE" : "BOOKED") + ")");
        afterBadge.setForeground(newVal == 0 ? new Color(52, 211, 153) : new Color(248, 113, 113));

        if ("BOOKING".equals(type)) {
            mutationExplainArea.setText(String.format(
                    "WEBSITE BOOKING DETECTED:\n" +
                    "1. Customer selected seat '%s' on CineBook website.\n" +
                    "2. Backend computed 2D array offsets: row = '%s' - 'A' = %d; col = %d - 1 = %d.\n" +
                    "3. Backend executed: seats[%d][%d] = 1 in O(1) time.\n" +
                    "4. Memory cell [%d][%d] transitioned from 0 to 1.",
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

    private void showHashMapMutationDetails(String key, BookingDto b, String type) {
        mutationDsaTypeBadge.setText("[HASHMAP]");
        mutationDsaTypeBadge.setForeground(new Color(236, 72, 153));

        int bucket = Math.abs(key.hashCode()) % HASHMAP_CAPACITY;

        if ("PUT".equals(type)) {
            mutationOpLabel.setText("Operation: bookingMap.put(\"" + key + "\", booking);");
            mutationCoordLabel.setText("Bucket Mapping: hash=" + key.hashCode() + " -> Bucket [" + bucket + "]");
            mutationTransitionLabel.setText("Key Insertion: Newly created booking record added.");

            beforeBadge.setText("bookingMap.containsKey(\"" + key + "\") == false");
            beforeBadge.setForeground(new Color(148, 163, 184));

            afterBadge.setText("bookingMap.containsKey(\"" + key + "\") == true");
            afterBadge.setForeground(new Color(52, 211, 153));

            mutationExplainArea.setText(String.format(
                    "HASHMAP INSERTION (put):\n" +
                    "1. Backend generated Booking ID '%s'.\n" +
                    "2. Hash code computed: hashCode() = %d.\n" +
                    "3. Bucket index calculated: Math.abs(%d) %% 16 = %d.\n" +
                    "4. Node inserted into bucket chain in O(1) amortized time.\n" +
                    "5. Customer '%s' booked seats: %s.",
                    key, key.hashCode(), key.hashCode(), bucket, b.customerName, b.bookedSeats
            ));
        } else {
            mutationOpLabel.setText("Operation: bookingMap.get(\"" + key + "\").setStatus(\"" + b.status + "\");");
            mutationCoordLabel.setText("Hash Lookup: get(\"" + key + "\") executed in O(1) time.");
            mutationTransitionLabel.setText("Status Update: Record status updated to " + b.status);

            beforeBadge.setText("status = CONFIRMED");
            beforeBadge.setForeground(new Color(52, 211, 153));

            afterBadge.setText("status = " + b.status);
            afterBadge.setForeground(new Color(248, 113, 113));

            mutationExplainArea.setText(String.format(
                    "HASHMAP RECORD UPDATE (cancel):\n" +
                    "1. Backend retrieved booking in O(1) using key '%s'.\n" +
                    "2. Updated booking status to '%s'.\n" +
                    "3. Associated seats %s freed and returned to 2D array.",
                    key, b.status, b.bookedSeats
            ));
        }
    }

    private void showQueueMutationDetails(WaitingNodeDto node, String type, int prevSize, int newSize) {
        mutationDsaTypeBadge.setText("[QUEUE]");
        mutationDsaTypeBadge.setForeground(new Color(56, 189, 248));

        if ("ENQUEUE".equals(type)) {
            mutationOpLabel.setText("Operation: waitlist.enqueue(\"" + node.customerName + "\", " + node.seatsRequested + ");");
            mutationCoordLabel.setText("Pointer Update: rear.next = newNode; rear = newNode;");
            mutationTransitionLabel.setText("Queue Growth: Size transitioned from " + prevSize + " to " + newSize);

            beforeBadge.setText("rear = previousTail (size=" + prevSize + ")");
            beforeBadge.setForeground(new Color(148, 163, 184));

            afterBadge.setText("rear = Node[\"" + node.customerName + "\"] (size=" + newSize + ")");
            afterBadge.setForeground(new Color(56, 189, 248));

            mutationExplainArea.setText(String.format(
                    "FIFO QUEUE ENQUEUE (At REAR):\n" +
                    "1. Customer '%s' requested %d seats for show '%s'.\n" +
                    "2. New Node appended at tail pointer (rear) in O(1) time.\n" +
                    "3. No elements shifted in memory (unlike ArrayList).\n" +
                    "4. Customer will be served First-In First-Out when seats are freed.",
                    node.customerName, node.seatsRequested, node.showId
            ));
        } else {
            mutationOpLabel.setText("Operation: waitlist.dequeue(); -> auto-allocated seats");
            mutationCoordLabel.setText("Pointer Update: front = front.next; (O(1) removal)");
            mutationTransitionLabel.setText("Queue Shrink: Size transitioned from " + prevSize + " to " + newSize);

            beforeBadge.setText("front = Node[\"" + node.customerName + "\"] (size=" + prevSize + ")");
            beforeBadge.setForeground(new Color(245, 158, 11));

            afterBadge.setText("front = front.next (size=" + newSize + ")");
            afterBadge.setForeground(new Color(52, 211, 153));

            mutationExplainArea.setText(String.format(
                    "FIFO QUEUE DEQUEUE (From FRONT):\n" +
                    "1. A booking was cancelled, freeing available seats.\n" +
                    "2. Backend peeked FRONT customer '%s' who needed %d seats.\n" +
                    "3. Seats available >= %d, so front node was dequeued in O(1) time.\n" +
                    "4. Ticket was automatically booked for '%s'!",
                    node.customerName, node.seatsRequested, node.seatsRequested, node.customerName
            ));
        }
    }

    private void updateConnectionStatus(boolean connected) {
        if (connected) {
            statusPill.setText("● CONNECTED");
            statusPill.setForeground(new Color(52, 211, 153));
            statusPill.setBorder(new CompoundBorder(
                    new LineBorder(new Color(52, 211, 153, 100), 1, true),
                    new EmptyBorder(3, 8, 3, 8)
            ));
        } else {
            statusPill.setText("● DISCONNECTED");
            statusPill.setForeground(new Color(239, 68, 68));
            statusPill.setBorder(new CompoundBorder(
                    new LineBorder(new Color(239, 68, 68, 100), 1, true),
                    new EmptyBorder(3, 8, 3, 8)
            ));
        }
    }

    private void log(String tag, String msg) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        eventLogArea.append(String.format("[%s] [%s] %s\n", timestamp, tag, msg));
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
            seatLabelView.setFont(new Font("Segoe UI", Font.BOLD, 17));
            seatLabelView.setForeground(Color.WHITE);

            valLabel = new JLabel("val = 0", SwingConstants.CENTER);
            valLabel.setFont(new Font("Consolas", Font.BOLD, 11));
            valLabel.setForeground(new Color(52, 211, 153));

            center.add(seatLabelView);
            center.add(valLabel);
            add(center, BorderLayout.CENTER);

            // Bottom: Status Badge
            statusBadge = new JLabel("AVAILABLE", SwingConstants.CENTER);
            statusBadge.setFont(new Font("Segoe UI", Font.BOLD, 9));
            statusBadge.setOpaque(true);
            statusBadge.setBackground(new Color(6, 78, 59));
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
                setBackground(new Color(24, 32, 47));
                valLabel.setText("val = 0");
                valLabel.setForeground(new Color(52, 211, 153));
                statusBadge.setText(isChanged ? "★ RELEASED (0)" : "AVAILABLE (0)");
                statusBadge.setBackground(new Color(6, 78, 59));
                statusBadge.setForeground(new Color(110, 231, 183));
            } else {
                setBackground(new Color(45, 23, 31));
                valLabel.setText("val = 1");
                valLabel.setForeground(new Color(248, 113, 113));
                statusBadge.setText(isChanged ? "★ BOOKED (1)" : "BOOKED (1)");
                statusBadge.setBackground(new Color(127, 29, 29));
                statusBadge.setForeground(new Color(254, 202, 202));
            }

            if (isChanged) {
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
        mutationDsaTypeBadge.setText("[ARRAY]");
        mutationDsaTypeBadge.setForeground(new Color(168, 85, 247));
        mutationOpLabel.setText("Inspecting Cell: seats[" + r + "][" + c + "]");
        mutationCoordLabel.setText("Index Mapping: Row " + r + " ('" + label.charAt(0) + "'), Column " + c + " (Seat " + (c + 1) + ")");
        mutationTransitionLabel.setText("Current Stored Value: " + val + " (" + (val == 0 ? "AVAILABLE" : "BOOKED") + ")");
        mutationExplainArea.setText(String.format(
                "CELL INSPECTOR [SEAT %s]:\n" +
                "• Array Coordinates: row = %d, col = %d\n" +
                "• Stored Value: %d (%s)\n" +
                "• Direct Memory Access: seats[%d][%d] in O(1) time.\n" +
                "• Meaning: %s",
                label, r, c, val, (val == 0 ? "AVAILABLE" : "BOOKED"),
                r, c,
                (val == 0 ? "Free for customers to book on CineBook." : "Occupied by confirmed ticket.")
        ));
    }

    // =========================================================================
    // LIGHTWEIGHT JSON PARSING & DTOs
    // =========================================================================
    public static class BookingDto {
        public String bookingId;
        public String customerName;
        public String showId;
        public String movieTitle;
        public String showTime;
        public List<String> bookedSeats = new ArrayList<>();
        public String bookingTime;
        public int totalAmount;
        public String status;
    }

    public static class WaitingNodeDto {
        public String customerName;
        public String showId;
        public int seatsRequested;
        public String joinedAt;
    }

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
        int seatsStart = json.indexOf("\"seatArray\"");
        if (seatsStart == -1) seatsStart = json.indexOf("\"seats\"");
        if (seatsStart == -1) return matrix;

        int arrayStart = json.indexOf("[[", seatsStart);
        int arrayEnd = json.indexOf("]]", arrayStart);
        if (arrayStart == -1 || arrayEnd == -1) return matrix;

        String arrayContent = json.substring(arrayStart + 1, arrayEnd + 1);
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

    private Map<String, BookingDto> parseBookingsJson(String json) {
        Map<String, BookingDto> map = new LinkedHashMap<>();
        int hmIdx = json.indexOf("\"hashMap\"");
        if (hmIdx == -1) return map;
        int entriesIdx = json.indexOf("\"entries\"", hmIdx);
        if (entriesIdx == -1) return map;

        Pattern p = Pattern.compile("\\{[^\\{\\}]*\"bookingId\"\\s*:\\s*\"([^\"]+)\"[^\\{\\}]*\\}");
        Matcher m = p.matcher(json.substring(entriesIdx));
        while (m.find()) {
            String block = m.group();
            BookingDto b = new BookingDto();
            b.bookingId = extractJsonString(block, "bookingId");
            b.customerName = extractJsonString(block, "customerName");
            b.showId = extractJsonString(block, "showId");
            b.movieTitle = extractJsonString(block, "movieTitle");
            b.showTime = extractJsonString(block, "showTime");
            b.bookingTime = extractJsonString(block, "bookingTime");
            b.status = extractJsonString(block, "status");
            b.totalAmount = parseJsonInt(block, "totalAmount", 0);

            int seatsStart = block.indexOf("\"bookedSeats\"");
            if (seatsStart != -1) {
                int arrStart = block.indexOf("[", seatsStart);
                int arrEnd = block.indexOf("]", arrStart);
                if (arrStart != -1 && arrEnd != -1) {
                    String arrContent = block.substring(arrStart + 1, arrEnd);
                    Matcher sm = Pattern.compile("\"([^\"]+)\"").matcher(arrContent);
                    while (sm.find()) {
                        b.bookedSeats.add(sm.group(1));
                    }
                }
            }

            if (b.bookingId != null) {
                map.put(b.bookingId, b);
            }
        }
        return map;
    }

    private List<WaitingNodeDto> parseQueueJson(String json) {
        List<WaitingNodeDto> list = new ArrayList<>();
        int qIdx = json.indexOf("\"queue\"");
        if (qIdx == -1) return list;
        int elemIdx = json.indexOf("\"elements\"", qIdx);
        if (elemIdx == -1) return list;

        Pattern p = Pattern.compile("\\{[^\\{\\}]*\"customerName\"\\s*:\\s*\"([^\"]+)\"[^\\{\\}]*\"seatsRequested\"\\s*:\\s*(\\d+)[^\\{\\}]*\\}");
        Matcher m = p.matcher(json.substring(elemIdx));
        while (m.find()) {
            String block = m.group();
            WaitingNodeDto w = new WaitingNodeDto();
            w.customerName = extractJsonString(block, "customerName");
            w.showId = extractJsonString(block, "showId");
            w.seatsRequested = parseJsonInt(block, "seatsRequested", 1);
            w.joinedAt = extractJsonString(block, "joinedAt");
            list.add(w);
        }
        return list;
    }

    private Set<String> parseHashSetJson(String json) {
        Set<String> set = new LinkedHashSet<>();
        int hsIdx = json.indexOf("\"hashSet\"");
        if (hsIdx == -1) return set;
        int elemIdx = json.indexOf("\"elements\"", hsIdx);
        if (elemIdx == -1) return set;
        int arrStart = json.indexOf("[", elemIdx);
        int arrEnd = json.indexOf("]", arrStart);
        if (arrStart != -1 && arrEnd != -1) {
            String arr = json.substring(arrStart + 1, arrEnd);
            Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(arr);
            while (m.find()) {
                set.add(m.group(1));
            }
        }
        return set;
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

    // =========================================================================
    // UI UTILITY BUILDERS
    // =========================================================================
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

    private static JLabel createPill(String text, Color accent) {
        JLabel pill = new JLabel(text);
        pill.setFont(new Font("Consolas", Font.BOLD, 11));
        pill.setForeground(accent);
        pill.setBorder(new CompoundBorder(
                new LineBorder(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 120), 1, true),
                new EmptyBorder(3, 8, 3, 8)
        ));
        return pill;
    }

    private static JLabel createMiniBadge(String text, Color accent) {
        JLabel b = new JLabel(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 10));
        b.setForeground(accent);
        b.setBorder(new CompoundBorder(
                new LineBorder(accent, 1, true),
                new EmptyBorder(1, 4, 1, 4)
        ));
        return b;
    }

    private static JPanel createLegendBadge(String text, Color accent) {
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

    private static JTextArea createStyledTextArea(String text) {
        JTextArea area = new JTextArea(text);
        area.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        area.setBackground(new Color(15, 23, 42));
        area.setForeground(new Color(203, 213, 225));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setBorder(new CompoundBorder(
                new LineBorder(new Color(51, 65, 85), 1),
                new EmptyBorder(6, 8, 6, 8)
        ));
        return area;
    }

    // =========================================================================
    // MAIN ENTRY POINT
    // =========================================================================
    public static void main(String[] args) {
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
