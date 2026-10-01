package com.cinebook.model;

import java.util.List;

public class Booking {

    private String bookingId;
    private String customerName;
    private String showId;
    private String movieTitle;
    private String moviePosterUrl;
    private String showTime;
    private List<String> bookedSeats;
    private String bookingTime;
    private int totalAmount;
    private String status; // "CONFIRMED" or "CANCELLED"

    public Booking() {}

    public Booking(String bookingId, String customerName, String showId,
                   List<String> bookedSeats, String bookingTime, int totalAmount, String status) {
        this(bookingId, customerName, showId, "Movie", "", "", bookedSeats, bookingTime, totalAmount, status);
    }

    public Booking(String bookingId, String customerName, String showId,
                   String movieTitle, String moviePosterUrl, String showTime,
                   List<String> bookedSeats, String bookingTime, int totalAmount, String status) {
        this.bookingId = bookingId;
        this.customerName = customerName;
        this.showId = showId;
        this.movieTitle = movieTitle;
        this.moviePosterUrl = moviePosterUrl;
        this.showTime = showTime;
        this.bookedSeats = bookedSeats;
        this.bookingTime = bookingTime;
        this.totalAmount = totalAmount;
        this.status = status;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getShowId() {
        return showId;
    }

    public void setShowId(String showId) {
        this.showId = showId;
    }

    public String getMovieTitle() {
        return movieTitle;
    }

    public void setMovieTitle(String movieTitle) {
        this.movieTitle = movieTitle;
    }

    public String getMoviePosterUrl() {
        return moviePosterUrl;
    }

    public void setMoviePosterUrl(String moviePosterUrl) {
        this.moviePosterUrl = moviePosterUrl;
    }

    public String getShowTime() {
        return showTime;
    }

    public void setShowTime(String showTime) {
        this.showTime = showTime;
    }

    public List<String> getBookedSeats() {
        return bookedSeats;
    }

    public void setBookedSeats(List<String> bookedSeats) {
        this.bookedSeats = bookedSeats;
    }

    public String getBookingTime() {
        return bookingTime;
    }

    public void setBookingTime(String bookingTime) {
        this.bookingTime = bookingTime;
    }

    public int getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(int totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
