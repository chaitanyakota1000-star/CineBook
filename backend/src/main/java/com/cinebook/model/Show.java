package com.cinebook.model;

public class Show {

    private String showId;
    private String movieId;
    private String theatreName;
    private String date;
    private String time;
    private int pricePerSeat;

    public Show(String showId, String movieId, String theatreName,
                String date, String time, int pricePerSeat) {
        this.showId = showId;
        this.movieId = movieId;
        this.theatreName = theatreName;
        this.date = date;
        this.time = time;
        this.pricePerSeat = pricePerSeat;
    }

    public String getShowId() {
        return showId;
    }

    public void setShowId(String showId) {
        this.showId = showId;
    }

    public String getMovieId() {
        return movieId;
    }

    public void setMovieId(String movieId) {
        this.movieId = movieId;
    }

    public String getTheatreName() {
        return theatreName;
    }

    public void setTheatreName(String theatreName) {
        this.theatreName = theatreName;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public int getPricePerSeat() {
        return pricePerSeat;
    }

    public void setPricePerSeat(int pricePerSeat) {
        this.pricePerSeat = pricePerSeat;
    }
}
