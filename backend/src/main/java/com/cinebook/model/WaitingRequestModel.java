package com.cinebook.model;

public class WaitingRequestModel {

    private String customerName;
    private String showId;
    private int seatsRequested;
    private String joinedAt;

    public WaitingRequestModel(String customerName, String showId, int seatsRequested, String joinedAt) {
        this.customerName = customerName;
        this.showId = showId;
        this.seatsRequested = seatsRequested;
        this.joinedAt = joinedAt;
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

    public int getSeatsRequested() {
        return seatsRequested;
    }

    public void setSeatsRequested(int seatsRequested) {
        this.seatsRequested = seatsRequested;
    }

    public String getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(String joinedAt) {
        this.joinedAt = joinedAt;
    }
}
