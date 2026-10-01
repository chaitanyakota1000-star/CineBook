package com.cinebook.ds;

import com.cinebook.model.WaitingRequestModel;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom FIFO linked-list queue representing the waiting list for a single show.
 * Uses singly-linked nodes with front and rear pointers.
 */
public class WaitingList {

    /**
     * Internal linked-list node.
     */
    public static class Node {
        public String customerName;
        public String showId;
        public int seatsRequested;
        public String joinedAt;
        public Node next;

        public Node(String customerName, String showId, int seatsRequested, String joinedAt) {
            this.customerName = customerName;
            this.showId = showId;
            this.seatsRequested = seatsRequested;
            this.joinedAt = joinedAt;
            this.next = null;
        }
    }

    private Node front;
    private Node rear;
    private int size;

    public WaitingList() {
        this.front = null;
        this.rear = null;
        this.size = 0;
    }

    /**
     * Adds a new customer to the rear of the queue.
     */
    public void enqueue(String customerName, String showId, int seatsRequested) {
        String joinedAt = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Node newNode = new Node(customerName, showId, seatsRequested, joinedAt);
        if (rear == null) {
            front = newNode;
            rear = newNode;
        } else {
            rear.next = newNode;
            rear = newNode;
        }
        size++;
    }

    /**
     * Removes and returns the front node. Returns null if the queue is empty.
     */
    public Node dequeue() {
        if (front == null) {
            return null;
        }
        Node removed = front;
        front = front.next;
        if (front == null) {
            rear = null;
        }
        size--;
        return removed;
    }

    /**
     * Returns the front node without removing it. Returns null if the queue is empty.
     */
    public Node peek() {
        return front;
    }

    /**
     * Returns true when the queue has no elements.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns the current number of elements in the queue.
     */
    public int getSize() {
        return size;
    }

    /**
     * Traverses the entire queue and returns all entries as a list of {@link WaitingRequestModel}
     * objects suitable for API responses.
     */
    public List<WaitingRequestModel> getAllWaiting() {
        List<WaitingRequestModel> result = new ArrayList<>();
        Node current = front;
        while (current != null) {
            result.add(new WaitingRequestModel(
                    current.customerName,
                    current.showId,
                    current.seatsRequested,
                    current.joinedAt
            ));
            current = current.next;
        }
        return result;
    }
}
