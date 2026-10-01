package com.cinebook.service;

import com.cinebook.model.Movie;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * In-memory repository for Movie data.
 * Six movies are pre-loaded at startup; no database is required.
 */
@Service
public class MovieService {

    private final List<Movie> movies;

    public MovieService() {
        movies = new ArrayList<>();

        movies.add(new Movie(
                "M1",
                "Interstellar",
                "Sci-Fi / Adventure / Drama",
                169,
                "English",
                8.7,
                "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
                "A team of explorers travel through a wormhole in space in an attempt to ensure humanity's survival."
        ));

        movies.add(new Movie(
                "M2",
                "Inception",
                "Sci-Fi / Thriller",
                148,
                "English",
                8.8,
                "https://image.tmdb.org/t/p/w500/9gk7adHYeDvHkCSEqAvQNLV5Uge.jpg",
                "A thief who steals corporate secrets through dream-sharing technology is given the inverse task of planting an idea into the mind of a C.E.O."
        ));

        movies.add(new Movie(
                "M3",
                "The Dark Knight",
                "Action / Crime",
                152,
                "English",
                9.0,
                "https://image.tmdb.org/t/p/w500/qJ2tW6WMUDux911r6m7haRef0WH.jpg",
                "Batman faces the Joker, a criminal mastermind who wants to plunge Gotham City into anarchy, testing the hero's commitment to justice."
        ));

        movies.add(new Movie(
                "M4",
                "Dune: Part Two",
                "Sci-Fi / Adventure",
                166,
                "English",
                8.5,
                "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
                "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family."
        ));

        movies.add(new Movie(
                "M5",
                "Spider-Man: Across the Spider-Verse",
                "Animation",
                140,
                "English",
                8.6,
                "https://image.tmdb.org/t/p/w500/8Vt6mWEReuy4Of61Lnj5Xj704m8.jpg",
                "Miles Morales catapults across the Multiverse, where he encounters a team of Spider-People charged with protecting its very existence."
        ));

        movies.add(new Movie(
                "M6",
                "The Batman",
                "Action / Mystery",
                176,
                "English",
                7.8,
                "https://image.tmdb.org/t/p/w500/b0PlSFdDwbyK0cf5RxwDpaOJQvQ.jpg",
                "Batman ventures into Gotham City's underworld when a sadistic killer leaves behind a trail of cryptic clues."
        ));
    }

    /**
     * Returns all available movies.
     */
    public List<Movie> getAllMovies() {
        return movies;
    }

    /**
     * Finds a movie by its ID.
     *
     * @param id the movie ID (e.g. "M1")
     * @return an Optional containing the movie, or empty if not found
     */
    public Optional<Movie> getMovieById(String id) {
        return movies.stream()
                .filter(m -> m.getId().equals(id))
                .findFirst();
    }
}
