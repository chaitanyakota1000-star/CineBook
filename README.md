# CineBook — Smart Movie Ticket Booking System
### B.Tech CSE DSA Capstone Project

A full-stack movie ticket booking system showcasing **Java Data Structures**:
- **2D Array** — Cinema seat layout (5 rows × 6 columns)
- **HashMap** — Booking storage with O(1) lookup
- **FIFO Queue** — Waiting list (custom linked-list implementation)

---

## Project Structure

```
CineBook/
├── README.md
├── backend/                         ← Java Spring Boot (Port 8080)
│   ├── pom.xml
│   └── src/main/java/com/cinebook/
│       ├── MainApplication.java
│       ├── model/
│       │   ├── Movie.java
│       │   ├── Show.java
│       │   ├── Booking.java
│       │   └── WaitingRequestModel.java
│       ├── ds/
│       │   ├── Theatre.java         ← 2D array seat management (DSA)
│       │   └── WaitingList.java     ← FIFO linked-list queue (DSA)
│       ├── service/
│       │   ├── MovieService.java
│       │   ├── ShowService.java
│       │   └── BookingService.java  ← HashMap + coordination (DSA)
│       └── controller/
│           ├── MovieController.java
│           ├── ShowController.java
│           └── BookingController.java
└── frontend/                        ← Static HTML/CSS/JS (open in browser)
    ├── index.html                   ← Home page
    ├── movies.html                  ← Movie listings + show selection
    ├── seats.html                   ← Interactive seat map
    ├── confirmation.html            ← Booking confirmation ticket
    ├── mybookings.html              ← Manage bookings
    ├── css/
    │   └── style.css
    └── js/
        ├── api.js                   ← All fetch() API calls
        └── app.js                   ← All page logic
```

---

## Prerequisites

| Tool | Version | Download |
|------|---------|----------|
| Java | 17+ | https://adoptium.net |
| Maven | 3.6+ | https://maven.apache.org |
| Any Browser | Latest | — |

Verify installation:
```bash
java -version
mvn -version
```

---

## Step 1: Start the Java Backend

```bash
cd CineBook/backend
mvn spring-boot:run
```

You should see:
```
Started MainApplication in 2.3 seconds (JVM running for 2.8)
Tomcat started on port(s): 8080 (http)
```

**Backend is ready when you see port 8080.**

Test it:
```
Open: http://localhost:8080/api/movies
```
You should see a JSON array of 6 movies.

---

## Step 2: Open the Frontend

Simply open the HTML file in your browser:

```
CineBook/frontend/index.html
```

Double-click `index.html` in Windows Explorer, OR:
```bash
start CineBook/frontend/index.html
```

> **Important:** The backend must be running BEFORE opening the frontend.

---

## REST API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/movies` | List all movies |
| GET | `/api/movies/{id}` | Get movie by ID |
| GET | `/api/shows` | List all shows |
| GET | `/api/shows/movie/{movieId}` | Shows for a movie |
| GET | `/api/shows/{showId}/seats` | Seat map for a show |
| POST | `/api/bookings` | Create booking |
| GET | `/api/bookings` | All bookings |
| GET | `/api/bookings/{bookingId}` | Get booking by ID |
| DELETE | `/api/bookings/{bookingId}` | Cancel booking |
| POST | `/api/waitlist` | Join waiting list |
| GET | `/api/waitlist/{showId}` | Waiting list info |

### Sample API Calls (PowerShell)

```powershell
# Get all movies
Invoke-WebRequest -Uri http://localhost:8080/api/movies | Select -ExpandProperty Content

# Create a booking
$body = '{"customerName":"Alice","showId":"S-M1-1","seatLabels":["A1","A2"]}'
Invoke-WebRequest -Uri http://localhost:8080/api/bookings -Method POST -Body $body -ContentType "application/json" | Select -ExpandProperty Content

# Cancel a booking
Invoke-WebRequest -Uri http://localhost:8080/api/bookings/BK1001 -Method DELETE | Select -ExpandProperty Content
```

---

## Data Structures Used

### 1. Two-Dimensional Array (`Theatre.java`)
```java
private int[][] seats = new int[5][6]; // 0=available, 1=booked

// Seat "C4" → seats[2][3]
// rowIndex = 'C' - 'A' = 2
// colIndex = 4 - 1     = 3
```

### 2. HashMap (`BookingService.java`)
```java
private final Map<String, Booking> bookingMap = new HashMap<>();
// Key: "BK1001" → Value: Booking object
// O(1) average: put, get, remove
```

### 3. FIFO Queue (`WaitingList.java`)
```java
// Custom linked-list queue
// front → [Alice,2] → [Bob,1] → [Carol,3] → null ← rear
// enqueue(): O(1) — add at rear
// dequeue(): O(1) — remove from front
```

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `Port 8080 already in use` | Stop other apps on 8080, or change `server.port` in application.properties |
| `Movies not loading` | Make sure backend is running. Check browser console (F12) for CORS errors |
| `CORS error` | Backend has CORS enabled. Make sure you're using `http://localhost:8080` not `https://` |
| `mvn not found` | Add Maven to system PATH or use full path to mvn |
| `java not found` | Add Java 17+ to system PATH |
| Bookings lost on restart | Expected behavior — data is in-memory by design |

---

## Important Notes for Viva

1. **Data resets on restart** — All bookings, seat states, and waiting lists are in Java heap memory. This is intentional for a DSA demo. Database integration (MySQL + JDBC) is a future enhancement.

2. **Atomic booking** — All seats in a request are validated FIRST. If any seat is taken, the entire booking is rejected. No partial bookings.

3. **FIFO waiting list** — When a booking is cancelled, `processWaitlist()` is called automatically. The FRONT customer is served first — strict FIFO, no skipping.

4. **Consistency** — BookingService ensures the 2D array and HashMap always agree. Both are updated in the same method call.

---

## Running Both Together (Quick Start)

Open TWO terminal windows:

**Terminal 1 (Backend):**
```bash
cd CineBook/backend
mvn spring-boot:run
```

**Terminal 2 (View in browser):**
```bash
start CineBook/frontend/index.html
```
