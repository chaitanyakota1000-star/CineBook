package com.cinebook.service;

import com.cinebook.ds.Theatre;
import com.cinebook.ds.WaitingList;
import com.cinebook.model.Booking;
import com.cinebook.model.Movie;
import com.cinebook.model.Show;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Core DSA-backed booking service.
 *
 * <ul>
 *   <li>Seat state is stored in a {@link Theatre} (2D int array) per show.</li>
 *   <li>Waiting customers are stored in a {@link WaitingList} (custom linked-list queue) per show.</li>
 *   <li>Booking records are stored in a HashMap keyed by bookingId.</li>
 * </ul>
 */
@Service
public class BookingService {

    /** One Theatre (seat grid) per showId. */
    private final Map<String, Theatre> theatreMap = new HashMap<>();

    /** All booking records, keyed by bookingId. */
    private final Map<String, Booking> bookingMap = new HashMap<>();

    /** One WaitingList per showId. */
    private final Map<String, WaitingList> waitlistMap = new HashMap<>();

    private int idCounter = 1001;

    private final ShowService showService;
    private final MovieService movieService;

    public BookingService(ShowService showService, MovieService movieService) {
        this.showService = showService;
        this.movieService = movieService;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Theatre getOrCreateTheatre(String showId) {
        return theatreMap.computeIfAbsent(showId, Theatre::new);
    }

    private WaitingList getOrCreateWaitlist(String showId) {
        return waitlistMap.computeIfAbsent(showId, id -> new WaitingList());
    }

    private synchronized String generateBookingId() {
        return "BK" + (idCounter++);
    }

    private String nowTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));
    }

    // -------------------------------------------------------------------------
    // Public API methods
    // -------------------------------------------------------------------------

    public Map<String, Object> getSeatMap(String showId) {
        Theatre theatre = getOrCreateTheatre(showId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("showId", showId);
        response.put("seats", theatre.getSeatsCopy());
        response.put("availableCount", theatre.getAvailableCount());
        response.put("totalSeats", Theatre.ROWS * Theatre.COLS);
        response.put("isFull", theatre.isFull());
        return response;
    }

    public Map<String, Object> createBooking(String customerName, String showId,
                                             List<String> seatLabels) {
        Map<String, Object> response = new LinkedHashMap<>();
        Theatre theatre = getOrCreateTheatre(showId);

        // --- Atomic validation phase ---
        List<String> unavailable = new ArrayList<>();
        List<int[]> parsedCoords = new ArrayList<>();

        for (String label : seatLabels) {
            int[] coord = theatre.parseSeatLabel(label);
            if (coord == null) {
                unavailable.add(label + " (invalid)");
            } else if (!theatre.isSeatAvailable(coord[0], coord[1])) {
                unavailable.add(label);
            } else {
                parsedCoords.add(coord);
            }
        }

        if (!unavailable.isEmpty()) {
            response.put("success", false);
            response.put("message", "Some seats are unavailable or invalid.");
            response.put("unavailableSeats", unavailable);
            return response;
        }

        // --- Booking phase (all seats validated) ---
        for (int[] coord : parsedCoords) {
            theatre.bookSeat(coord[0], coord[1]);
        }

        Optional<Show> showOpt = showService.getShowById(showId);
        int pricePerSeat = showOpt.map(Show::getPricePerSeat).orElse(180);
        String showTime = showOpt.map(Show::getTime).orElse("5:00 PM");
        String movieId = showOpt.map(Show::getMovieId).orElse("");

        Optional<Movie> movieOpt = movieService.getMovieById(movieId);
        String movieTitle = movieOpt.map(Movie::getTitle).orElse("Interstellar");
        String posterUrl = movieOpt.map(Movie::getPosterUrl).orElse("");

        String bookingId = generateBookingId();
        Booking booking = new Booking(
                bookingId,
                customerName,
                showId,
                movieTitle,
                posterUrl,
                showTime,
                new ArrayList<>(seatLabels),
                nowTimestamp(),
                pricePerSeat * seatLabels.size(),
                "CONFIRMED"
        );
        bookingMap.put(bookingId, booking);

        response.put("success", true);
        response.put("booking", booking);

        processWaitlist(showId);

        return response;
    }

    public Map<String, Object> cancelBooking(String bookingId) {
        Map<String, Object> response = new LinkedHashMap<>();
        Booking booking = bookingMap.get(bookingId);

        if (booking == null) {
            response.put("success", false);
            response.put("message", "Booking with ID " + bookingId + " not found.");
            return response;
        }

        Theatre theatre = getOrCreateTheatre(booking.getShowId());
        for (String label : booking.getBookedSeats()) {
            int[] coord = theatre.parseSeatLabel(label);
            if (coord != null) {
                theatre.cancelSeat(coord[0], coord[1]);
            }
        }

        booking.setStatus("CANCELLED");

        response.put("success", true);
        response.put("message", "Booking " + bookingId + " cancelled. Seats have been released.");
        response.put("freedSeats", booking.getBookedSeats());

        processWaitlist(booking.getShowId());

        return response;
    }

    public Optional<Booking> getBookingById(String bookingId) {
        return Optional.ofNullable(bookingMap.get(bookingId));
    }

    public List<Booking> getAllBookings() {
        return new ArrayList<>(bookingMap.values());
    }

    private void processWaitlist(String showId) {
        WaitingList waitlist = getOrCreateWaitlist(showId);
        Theatre theatre = getOrCreateTheatre(showId);

        Optional<Show> showOpt = showService.getShowById(showId);
        int pricePerSeat = showOpt.map(Show::getPricePerSeat).orElse(180);
        String showTime = showOpt.map(Show::getTime).orElse("5:00 PM");
        String movieId = showOpt.map(Show::getMovieId).orElse("");

        Optional<Movie> movieOpt = movieService.getMovieById(movieId);
        String movieTitle = movieOpt.map(Movie::getTitle).orElse("Interstellar");
        String posterUrl = movieOpt.map(Movie::getPosterUrl).orElse("");

        while (!waitlist.isEmpty()) {
            WaitingList.Node front = waitlist.peek();

            if (theatre.getAvailableCount() < front.seatsRequested) {
                break;
            }

            WaitingList.Node customer = waitlist.dequeue();
            List<String> seats = theatre.findFirstAvailableSeats(customer.seatsRequested);

            for (String label : seats) {
                int[] coord = theatre.parseSeatLabel(label);
                if (coord != null) {
                    theatre.bookSeat(coord[0], coord[1]);
                }
            }

            String bookingId = generateBookingId();
            Booking booking = new Booking(
                    bookingId,
                    customer.customerName,
                    showId,
                    movieTitle,
                    posterUrl,
                    showTime,
                    seats,
                    nowTimestamp(),
                    pricePerSeat * seats.size(),
                    "CONFIRMED"
            );
            bookingMap.put(bookingId, booking);

            System.out.printf(
                    "[WaitingList] Auto-booked %d seat(s) for '%s' on movie '%s' (%s) -> Booking %s%n",
                    seats.size(), customer.customerName, movieTitle, showId, bookingId
            );
        }
    }

    public Map<String, Object> joinWaitlist(String customerName, String showId, int seatsRequested) {
        Map<String, Object> response = new LinkedHashMap<>();
        WaitingList waitlist = getOrCreateWaitlist(showId);
        waitlist.enqueue(customerName, showId, seatsRequested);

        response.put("success", true);
        response.put("message", "Customer " + customerName + " added to the waiting list.");
        response.put("position", waitlist.getSize());
        response.put("showId", showId);
        response.put("seatsRequested", seatsRequested);
        return response;
    }

    public Map<String, Object> getWaitlistInfo(String showId) {
        WaitingList waitlist = getOrCreateWaitlist(showId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("showId", showId);
        response.put("waitingCount", waitlist.getSize());
        response.put("customers", waitlist.getAllWaiting());
        return response;
    }

    public Map<String, Object> getDsaState(String showId) {
        Map<String, Object> state = new LinkedHashMap<>();

        // 1. Seat Array (2D Matrix int[5][6])
        Theatre theatre = getOrCreateTheatre(showId);
        Map<String, Object> seatArrayInfo = new LinkedHashMap<>();
        seatArrayInfo.put("variableName", "theatreMap.get(\"" + showId + "\").seats");
        seatArrayInfo.put("type", "int[5][6]");
        seatArrayInfo.put("rows", Theatre.ROWS);
        seatArrayInfo.put("cols", Theatre.COLS);
        seatArrayInfo.put("totalSeats", Theatre.ROWS * Theatre.COLS);
        seatArrayInfo.put("availableCount", theatre.getAvailableCount());
        seatArrayInfo.put("bookedCount", (Theatre.ROWS * Theatre.COLS) - theatre.getAvailableCount());
        seatArrayInfo.put("matrix", theatre.getSeatsCopy());
        seatArrayInfo.put("isFull", theatre.isFull());
        state.put("seatArray", seatArrayInfo);

        // 2. Booking HashMap
        Map<String, Object> hashMapInfo = new LinkedHashMap<>();
        hashMapInfo.put("variableName", "bookingMap");
        hashMapInfo.put("type", "HashMap<String, Booking>");
        hashMapInfo.put("size", bookingMap.size());

        Map<String, Object> allEntries = new LinkedHashMap<>();
        List<Booking> showBookings = new ArrayList<>();
        Set<String> occupiedSeatSet = new LinkedHashSet<>();

        for (Map.Entry<String, Booking> entry : bookingMap.entrySet()) {
            Booking b = entry.getValue();
            allEntries.put(entry.getKey(), b);
            if (showId.equals(b.getShowId())) {
                showBookings.add(b);
                if ("CONFIRMED".equalsIgnoreCase(b.getStatus())) {
                    occupiedSeatSet.addAll(b.getBookedSeats());
                }
            }
        }
        hashMapInfo.put("entries", allEntries);
        hashMapInfo.put("showBookings", showBookings);
        state.put("hashMap", hashMapInfo);

        // 3. Waiting Queue (FIFO)
        WaitingList waitlist = getOrCreateWaitlist(showId);
        Map<String, Object> queueInfo = new LinkedHashMap<>();
        queueInfo.put("variableName", "waitlistMap.get(\"" + showId + "\")");
        queueInfo.put("type", "Custom FIFO LinkedList Queue (WaitingList)");
        queueInfo.put("size", waitlist.getSize());
        queueInfo.put("isEmpty", waitlist.isEmpty());
        WaitingList.Node frontNode = waitlist.peek();
        queueInfo.put("frontCustomer", frontNode != null ? frontNode.customerName : null);
        queueInfo.put("frontSeatsRequested", frontNode != null ? frontNode.seatsRequested : 0);
        queueInfo.put("elements", waitlist.getAllWaiting());
        state.put("queue", queueInfo);

        // 4. Occupied Seats HashSet
        Map<String, Object> hashSetInfo = new LinkedHashMap<>();
        hashSetInfo.put("variableName", "occupiedSeatsSet");
        hashSetInfo.put("type", "HashSet<String>");
        hashSetInfo.put("size", occupiedSeatSet.size());
        hashSetInfo.put("elements", occupiedSeatSet);
        state.put("hashSet", hashSetInfo);

        state.put("showId", showId);
        state.put("timestamp", nowTimestamp());
        return state;
    }
}
