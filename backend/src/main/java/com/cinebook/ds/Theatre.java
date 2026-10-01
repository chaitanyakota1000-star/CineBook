package com.cinebook.ds;

import java.util.ArrayList;
import java.util.List;

/**
 * 2D array-based seat management for a single show.
 * Rows A-E (0-4), Columns 1-6 (0-5). Seat value: 0 = available, 1 = booked.
 */
public class Theatre {

    public static final int ROWS = 5;
    public static final int COLS = 6;

    private final int[][] seats;
    private final String showId;

    public Theatre(String showId) {
        this.showId = showId;
        this.seats = new int[ROWS][COLS];
        // All seats initialised to 0 (available) by default in Java
    }

    public String getShowId() {
        return showId;
    }

    /**
     * Returns true when the given seat is available (value == 0).
     */
    public boolean isSeatAvailable(int row, int col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) {
            return false;
        }
        return seats[row][col] == 0;
    }

    /**
     * Marks the seat as booked (value = 1).
     */
    public void bookSeat(int row, int col) {
        if (row >= 0 && row < ROWS && col >= 0 && col < COLS) {
            seats[row][col] = 1;
        }
    }

    /**
     * Marks the seat as available again (value = 0).
     */
    public void cancelSeat(int row, int col) {
        if (row >= 0 && row < ROWS && col >= 0 && col < COLS) {
            seats[row][col] = 0;
        }
    }

    /**
     * Returns the total number of available seats.
     */
    public int getAvailableCount() {
        int count = 0;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (seats[r][c] == 0) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Returns true when every seat is booked.
     */
    public boolean isFull() {
        return getAvailableCount() == 0;
    }

    /**
     * Converts a seat label like "C4" to [row, col] indices (0-based).
     * Row letter: A=0, B=1, C=2, D=3, E=4.
     * Column number: 1-6 mapped to 0-5.
     *
     * @param label e.g. "C4"
     * @return int[]{row, col} or null if the label is invalid
     */
    public int[] parseSeatLabel(String label) {
        if (label == null || label.length() < 2) {
            return null;
        }
        char rowChar = Character.toUpperCase(label.charAt(0));
        int row = rowChar - 'A';
        if (row < 0 || row >= ROWS) {
            return null;
        }
        String colStr = label.substring(1);
        int col;
        try {
            col = Integer.parseInt(colStr) - 1; // convert 1-based to 0-based
        } catch (NumberFormatException e) {
            return null;
        }
        if (col < 0 || col >= COLS) {
            return null;
        }
        return new int[]{row, col};
    }

    /**
     * Converts 0-based [row, col] indices back to a seat label like "C4".
     *
     * @param row 0-based row index
     * @param col 0-based column index
     * @return seat label string e.g. "C4"
     */
    public String toSeatLabel(int row, int col) {
        char rowChar = (char) ('A' + row);
        int colNum = col + 1;
        return "" + rowChar + colNum;
    }

    /**
     * Returns a deep copy of the internal seats array so callers cannot mutate state.
     */
    public int[][] getSeatsCopy() {
        int[][] copy = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) {
            System.arraycopy(seats[r], 0, copy[r], 0, COLS);
        }
        return copy;
    }

    /**
     * Finds the first {@code count} available seats in row-major order and returns their labels.
     *
     * @param count number of seats required
     * @return list of seat labels (may be shorter than count if not enough seats remain)
     */
    public List<String> findFirstAvailableSeats(int count) {
        List<String> result = new ArrayList<>();
        for (int r = 0; r < ROWS && result.size() < count; r++) {
            for (int c = 0; c < COLS && result.size() < count; c++) {
                if (seats[r][c] == 0) {
                    result.add(toSeatLabel(r, c));
                }
            }
        }
        return result;
    }
}
