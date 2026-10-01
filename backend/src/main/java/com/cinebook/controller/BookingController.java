package com.cinebook.controller;

import com.cinebook.model.Booking;
import com.cinebook.service.BookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing booking and waitlist endpoints.
 */
@RestController
@RequestMapping("/api/bookings")
@CrossOrigin(origins = "*")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    // -------------------------------------------------------------------------
    // Booking endpoints
    // -------------------------------------------------------------------------

    /**
     * GET /api/bookings
     * Returns all active bookings.
     */
    @GetMapping
    public ResponseEntity<List<Booking>> getAllBookings() {
        return ResponseEntity.ok(bookingService.getAllBookings());
    }

    /**
     * GET /api/bookings/{bookingId}
     * Returns a booking by ID, or 404 if not found.
     */
    @GetMapping("/{bookingId}")
    public ResponseEntity<Booking> getBookingById(@PathVariable String bookingId) {
        return bookingService.getBookingById(bookingId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/bookings
     * Creates a new booking.
     *
     * Request body (JSON):
     * <pre>
     * {
     *   "customerName": "Alice",
     *   "showId": "S-M1-1",
     *   "seatLabels": ["A1", "A2"]
     * }
     * </pre>
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createBooking(
            @RequestBody Map<String, Object> requestBody) {

        String customerName = (String) requestBody.get("customerName");
        String showId = (String) requestBody.get("showId");

        @SuppressWarnings("unchecked")
        List<String> seatLabels = (List<String>) requestBody.get("seatLabels");

        Map<String, Object> result = bookingService.createBooking(customerName, showId, seatLabels);

        boolean success = Boolean.TRUE.equals(result.get("success"));
        return success
                ? ResponseEntity.ok(result)
                : ResponseEntity.badRequest().body(result);
    }

    /**
     * DELETE /api/bookings/{bookingId}
     * Cancels an existing booking and frees the seats.
     */
    @DeleteMapping("/{bookingId}")
    public ResponseEntity<Map<String, Object>> cancelBooking(@PathVariable String bookingId) {
        Map<String, Object> result = bookingService.cancelBooking(bookingId);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        return success
                ? ResponseEntity.ok(result)
                : ResponseEntity.notFound().build();
    }

    // -------------------------------------------------------------------------
    // Waitlist endpoints
    // -------------------------------------------------------------------------

    /**
     * POST /api/waitlist
     * Adds a customer to the waiting list for a show.
     *
     * Request body (JSON):
     * <pre>
     * {
     *   "customerName": "Bob",
     *   "showId": "S-M1-1",
     *   "seatsRequested": 2
     * }
     * </pre>
     */
    @PostMapping("/waitlist")
    public ResponseEntity<Map<String, Object>> joinWaitlist(
            @RequestBody Map<String, Object> requestBody) {

        String customerName = (String) requestBody.get("customerName");
        String showId = (String) requestBody.get("showId");
        int seatsRequested = ((Number) requestBody.get("seatsRequested")).intValue();

        Map<String, Object> result = bookingService.joinWaitlist(customerName, showId, seatsRequested);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/waitlist/{showId}
     * Returns the current waiting list for a show.
     */
    @GetMapping("/waitlist/{showId}")
    public ResponseEntity<Map<String, Object>> getWaitlistInfo(@PathVariable String showId) {
        return ResponseEntity.ok(bookingService.getWaitlistInfo(showId));
    }
}
