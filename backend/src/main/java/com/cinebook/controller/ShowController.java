package com.cinebook.controller;

import com.cinebook.model.Show;
import com.cinebook.service.BookingService;
import com.cinebook.service.ShowService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing show-related endpoints, including the seat map for a show.
 */
@RestController
@RequestMapping("/api/shows")
@CrossOrigin(origins = "*")
public class ShowController {

    private final ShowService showService;
    private final BookingService bookingService;

    public ShowController(ShowService showService, BookingService bookingService) {
        this.showService = showService;
        this.bookingService = bookingService;
    }

    /**
     * GET /api/shows
     * Returns all shows.
     */
    @GetMapping
    public ResponseEntity<List<Show>> getAllShows() {
        return ResponseEntity.ok(showService.getAllShows());
    }

    /**
     * GET /api/shows/movie/{movieId}
     * Returns all shows for a specific movie.
     */
    @GetMapping("/movie/{movieId}")
    public ResponseEntity<List<Show>> getShowsByMovieId(@PathVariable String movieId) {
        return ResponseEntity.ok(showService.getShowsByMovieId(movieId));
    }

    /**
     * GET /api/shows/{showId}
     * Returns a single show by ID, or 404 if not found.
     */
    @GetMapping("/{showId}")
    public ResponseEntity<Show> getShowById(@PathVariable String showId) {
        return showService.getShowById(showId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/shows/{showId}/seats
     * Returns the current seat map (2D array + availability info) for a show.
     */
    @GetMapping("/{showId}/seats")
    public ResponseEntity<Map<String, Object>> getSeatMap(@PathVariable String showId) {
        return ResponseEntity.ok(bookingService.getSeatMap(showId));
    }
}
