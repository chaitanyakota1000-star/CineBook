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

## Standalone CineBook DSA Visualizer (Desktop Swing Application)

CineBook includes a standalone Java Swing desktop application that connects live to the running Spring Boot backend and visualizes all 4 core data structures in real time:

1. **🪑 2D Seat Array (`int[5][6]`)**
   - Displays cinema armchair grid with memory indices `[row][col]`, labels (`A1`-`E6`), and stored binary values (`0 = AVAILABLE`, `1 = BOOKED`).
   - Click any cell to inspect direct memory coordinates and $O(1)$ random access access formulas (`'A'+row`, `col+1`).

2. **🗺️ Booking HashMap (`HashMap<String, Booking> bookingMap`)**
   - Displays real booking records stored in the backend with $O(1)$ amortized put/get complexity.
   - Interactive $O(1)$ key search (`get(key)`): calculates `hashCode()` and bucket index (`Math.abs(hash) % 16`).
   - 16-Bucket Hash Table visualizer showing separate chaining node links (`Bucket[i] ➔ Node[BK1001]`).
   - Live status tracking (`CONFIRMED` in green, `CANCELLED` in red).

3. **👥 Waiting Queue (Custom FIFO LinkedList `WaitingList`)**
   - Visualizes First-In First-Out pipeline: `FRONT (Head) ────► Node ────► REAR (Tail)`.
   - Live pointers: `front` (peek next customer) and `rear` (new requests append here in $O(1)$).
   - Built-in waitlist test tool: Enqueue test customers and watch auto-allocation occur immediately upon seat cancellation!

4. **🏷️ Occupied Seats HashSet (`HashSet<String> occupiedSeatsSet`)**
   - Visualizes all occupied seat codes and demonstrates $O(1)$ membership checks (`contains(seat)`) preventing race conditions and duplicate bookings.

### Launching the DSA Visualizer on Windows

Ensure your Spring Boot backend is running on `http://localhost:8080`, then run:

```bat
run-dsa-visualizer.bat
```

Or run manually from the terminal:
```bash
javac visualizer/CineBookDsaVisualizer.java
java visualizer.CineBookDsaVisualizer
```

---

## Running Everything Together (Quick Start)

Open THREE windows:

**Window 1 (Backend):**
```bash
cd CineBook/backend
mvnw.cmd spring-boot:run
```

**Window 2 (Frontend Website):**
```bash
start CineBook/frontend/index.html
```

**Window 3 (Desktop DSA Visualizer):**
```bat
run-dsa-visualizer.bat
```
