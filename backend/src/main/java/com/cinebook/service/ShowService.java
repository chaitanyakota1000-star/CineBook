package com.cinebook.service;

import com.cinebook.model.Show;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * In-memory repository for Show data.
 * Four shows per movie (24 shows total) are pre-loaded at startup.
 */
@Service
public class ShowService {

    private final List<Show> shows;

    private static final String THEATRE_NAME = "CineBook Premiere: PVR Cinemas";
    private static final String DATE = "2026-10-05";
    private static final int PRICE_PER_SEAT = 180;
    private static final String[] TIMES = {"10:00 AM", "1:30 PM", "5:00 PM", "8:30 PM"};
    private static final String[] MOVIE_IDS = {"M1", "M2", "M3", "M4", "M5", "M6"};

    public ShowService() {
        shows = new ArrayList<>();
        for (String movieId : MOVIE_IDS) {
            for (int i = 0; i < TIMES.length; i++) {
                String showId = "S-" + movieId + "-" + (i + 1);
                shows.add(new Show(showId, movieId, THEATRE_NAME, DATE, TIMES[i], PRICE_PER_SEAT));
            }
        }
    }

    /**
     * Returns all shows across all movies.
     */
    public List<Show> getAllShows() {
        return shows;
    }

    /**
     * Returns all shows for a specific movie.
     *
     * @param movieId the movie ID to filter by (e.g. "M1")
     */
    public List<Show> getShowsByMovieId(String movieId) {
        return shows.stream()
                .filter(s -> s.getMovieId().equals(movieId))
                .collect(Collectors.toList());
    }

    /**
     * Finds a single show by its ID.
     *
     * @param showId the show ID (e.g. "S-M1-1")
     * @return an Optional containing the show, or empty if not found
     */
    public Optional<Show> getShowById(String showId) {
        return shows.stream()
                .filter(s -> s.getShowId().equals(showId))
                .findFirst();
    }
}
