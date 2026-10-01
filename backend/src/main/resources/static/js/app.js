/**
 * CineBook — Main Application Logic
 * frontend/js/app.js
 *
 * Implements:
 * - Home & Movies showcase
 * - 3-Panel Cinema Hall matching user UI (Left Movie Card, Center Armchair Matrix, Right Booking Summary)
 * - Confirmation page with digital ticket
 * - My Bookings with Movie Name, poster, details, search & cancel
 */

const PRICE_PER_TICKET = 180;
const ROWS = ['A', 'B', 'C', 'D', 'E'];

/* =========================================================
   ROUTER — Runs on DOMContentLoaded
   ========================================================= */
document.addEventListener('DOMContentLoaded', () => {
  initNavbar();

  let page = document.body.getAttribute('data-page');
  if (!page) {
    const path = window.location.pathname.toLowerCase();
    if (path.includes('seats')) page = 'seats';
    else if (path.includes('confirmation')) page = 'confirmation';
    else if (path.includes('mybooking')) page = 'mybookings';
    else if (path.includes('movies')) page = 'movies';
    else page = 'home';
  }

  switch (page) {
    case 'home':
      initHome();
      break;
    case 'movies':
      initMovies();
      break;
    case 'seats':
      initSeats();
      break;
    case 'confirmation':
      initConfirmation();
      break;
    case 'mybookings':
      initMyBookings();
      break;
  }
});

/* =========================================================
   NAVBAR & LIVE SEARCH
   ========================================================= */
function initNavbar() {
  const hamburger = document.getElementById('hamburger');
  const navLinks = document.getElementById('navLinks');
  if (hamburger && navLinks) {
    hamburger.addEventListener('click', () => {
      navLinks.classList.toggle('nav-open');
      hamburger.classList.toggle('active');
    });
  }

  const searchToggle = document.getElementById('searchToggle');
  const searchBar = document.getElementById('searchBar');
  const searchClose = document.getElementById('searchClose');
  const searchInput = document.getElementById('searchInput');

  if (searchToggle && searchBar) {
    searchToggle.addEventListener('click', () => {
      const isVisible = searchBar.style.display === 'block';
      searchBar.style.display = isVisible ? 'none' : 'block';
      if (!isVisible && searchInput) searchInput.focus();
    });
  }

  if (searchClose && searchBar) {
    searchClose.addEventListener('click', () => { searchBar.style.display = 'none'; });
  }

  if (searchInput) {
    searchInput.addEventListener('input', (e) => {
      const q = e.target.value.toLowerCase().trim();
      const cards = document.querySelectorAll('.movie-card');
      cards.forEach(card => {
        const title = (card.querySelector('.movie-title')?.textContent || '').toLowerCase();
        const genre = (card.querySelector('.genre-tag')?.textContent || '').toLowerCase();
        card.style.display = (title.includes(q) || genre.includes(q)) ? '' : 'none';
      });
    });
  }

  window.addEventListener('scroll', () => {
    const navbar = document.getElementById('navbar');
    if (navbar) navbar.classList.toggle('scrolled', window.scrollY > 15);
  });
}

/* =========================================================
   HOME & MOVIES GRID
   ========================================================= */
async function initHome() {
  await renderMovieGrid('movies-grid', 6);
  initShowModal();
}

async function initMovies() {
  await renderMovieGrid('movies-grid', 0);
  initShowModal();
}

async function renderMovieGrid(gridId, limit) {
  const grid = document.getElementById(gridId);
  if (!grid) return;

  grid.innerHTML = Array(limit || 6).fill('<div class="skeleton-card"></div>').join('');

  try {
    let movies = await window.getMovies();
    if (limit > 0) movies = movies.slice(0, limit);

    if (!movies || movies.length === 0) {
      grid.innerHTML = '<div class="empty-state"><h3>No movies currently showing.</h3></div>';
      return;
    }

    grid.innerHTML = movies.map(movie => `
      <div class="movie-card" data-movie-id="${movie.id}">
        <div class="poster-container">
          <img class="movie-poster-img" src="${movie.posterUrl}" alt="${movie.title}" onerror="this.onerror=null;this.src='https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=500&q=80';" />
          <div class="rating-badge">★ ${movie.rating.toFixed(1)}</div>
        </div>
        <div class="card-body">
          <h3 class="movie-title">${movie.title}</h3>
          <div class="movie-meta">
            <span class="genre-tag">${movie.genre}</span>
            <span class="text-muted">${movie.durationMinutes}m</span>
            <span class="text-muted">${movie.language}</span>
          </div>
          <button class="btn-primary btn-full book-btn" data-movie-id="${movie.id}">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 3h-2a2 2 0 0 0-4 0H8a2 2 0 0 0-2 2v0h12V5a2 2 0 0 0-2-2z"/></svg>
            Book Tickets
          </button>
        </div>
      </div>
    `).join('');

    grid.querySelectorAll('.book-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        handleBookClick(btn.getAttribute('data-movie-id'));
      });
    });

  } catch (err) {
    grid.innerHTML = '<div class="empty-state"><h3>Backend not reached</h3><p class="text-muted">Ensure Spring Boot is running on port 8080.</p></div>';
  }
}

function handleBookClick(movieId) {
  if (document.getElementById('showModal')) {
    openShowModal(movieId);
  } else {
    window.location.href = `movies.html?book=${movieId}`;
  }
}

/* =========================================================
   SHOW SELECTION MODAL
   ========================================================= */
let _selectedShowId = null;
let _selectedMovieId = null;

function initShowModal() {
  const modal = document.getElementById('showModal');
  const closeBtn = document.getElementById('closeShowModal');
  const dateTabs = document.getElementById('dateTabs');
  const chooseBtn = document.getElementById('chooseSeatsBtn');

  if (closeBtn) closeBtn.addEventListener('click', () => { if (modal) modal.style.display = 'none'; });
  if (modal) modal.addEventListener('click', (e) => { if (e.target === modal) modal.style.display = 'none'; });

  dateTabs?.querySelectorAll('.date-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      dateTabs.querySelectorAll('.date-tab').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
    });
  });

  chooseBtn?.addEventListener('click', () => {
    if (!_selectedShowId) {
      window.showToast('Please select a showtime first', 'warning');
      return;
    }
    window.location.href = `seats.html?show=${_selectedShowId}&movie=${_selectedMovieId}`;
  });

  const params = new URLSearchParams(window.location.search);
  const bookId = params.get('book');
  if (bookId) openShowModal(bookId);
}

async function openShowModal(movieId) {
  _selectedMovieId = movieId;
  _selectedShowId = null;
  const modal = document.getElementById('showModal');
  if (!modal) return;

  modal.style.display = 'flex';
  const showtimeGrid = document.getElementById('showtimeGrid');
  const footer = document.getElementById('showSelectFooter');
  const movieInfoDiv = document.getElementById('modalMovieInfo');

  if (footer) footer.style.display = 'none';

  try {
    const [movies, shows] = await Promise.all([
      window.getMovies(),
      window.getShowsByMovie(movieId)
    ]);
    const movie = movies.find(m => m.id === movieId);

    if (movie && movieInfoDiv) {
      movieInfoDiv.innerHTML = `
        <div class="modal-movie-hero">
          <img src="${movie.posterUrl}" alt="${movie.title}" class="modal-poster" onerror="this.onerror=null;this.src='https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=500&q=80';" />
          <div class="modal-movie-text">
            <h2>${movie.title}</h2>
            <div class="movie-meta" style="margin: 8px 0 12px 0;">
              <span class="genre-tag">${movie.genre}</span>
              <span class="text-muted">• ${movie.durationMinutes} min</span>
              <span class="text-muted">• ${movie.language}</span>
              <span class="text-success" style="font-weight:700;">★ ${movie.rating.toFixed(1)}</span>
            </div>
            <p class="text-muted" style="font-size:0.875rem; line-height:1.5;">${movie.synopsis}</p>
          </div>
        </div>`;
    }

    if (showtimeGrid && shows && shows.length > 0) {
      showtimeGrid.innerHTML = shows.map(show => `
        <button class="showtime-btn" data-show-id="${show.showId}" data-time="${show.time}">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
          <span>${show.time}</span>
          <span style="font-size:0.75rem; color:var(--accent-pink);">₹${show.pricePerSeat}</span>
        </button>
      `).join('');

      showtimeGrid.querySelectorAll('.showtime-btn').forEach(btn => {
        btn.addEventListener('click', () => {
          showtimeGrid.querySelectorAll('.showtime-btn').forEach(b => b.classList.remove('active'));
          btn.classList.add('active');
          _selectedShowId = btn.getAttribute('data-show-id');
          if (footer) {
            footer.style.display = 'flex';
            const info = document.getElementById('selectedShowInfo');
            if (info) info.textContent = `${movie?.title || ''} • ${btn.getAttribute('data-time')} • PVR Cinemas`;
          }
          // Smooth direct transition to seat layout
          setTimeout(() => {
            if (_selectedShowId && _selectedMovieId) {
              window.location.href = `seats.html?show=${_selectedShowId}&movie=${_selectedMovieId}`;
            }
          }, 200);
        });
      });
    }

  } catch (err) {
    window.showToast('Could not load shows: ' + err.message, 'error');
  }
}

/* =========================================================
   PAGE: SEATS (3-Panel Interactive Cinema Hall)
   ========================================================= */
let _selectedSeats = [];
let _seatData = null;
let _showInfo = null;
let _movieInfo = null;

async function initSeats() {
  const params = new URLSearchParams(window.location.search);
  const showId = params.get('show') || 'S-M1-1';
  const movieId = params.get('movie') || 'M1';

  try {
    const [seatMap, movies, shows] = await Promise.all([
      window.getSeatMap(showId),
      window.getMovies().catch(() => []),
      window.getShows().catch(() => [])
    ]);

    _seatData = seatMap;
    _movieInfo = movies.find(m => m.id === movieId) || movies[0] || {
      id: 'M1',
      title: 'Interstellar',
      genre: 'Sci-Fi / Adventure / Drama',
      durationMinutes: 169,
      language: 'English',
      rating: 8.6,
      posterUrl: 'https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg'
    };

    const showObj = shows.find(s => s.showId === showId);
    _showInfo = {
      showId,
      theatreName: showObj?.theatreName || 'PVR - Express Avenue, Chennai',
      date: showObj?.date || 'Sat, 14 Dec 2024',
      time: showObj?.time || guessTimeFromShowId(showId),
      pricePerSeat: showObj?.pricePerSeat || 180
    };

    const loading = document.getElementById('seatsLoading');
    if (loading) loading.style.display = 'none';

    // Populate Left Panel (Movie Card)
    populateMoviePanel(_movieInfo, _showInfo);

    // Populate Right Panel (Summary Initial)
    populateSummaryPanel(_movieInfo, _showInfo);

    // Populate Center Hall (Armchair Grid)
    if (seatMap.isFull) {
      showHouseFullBanner(showId);
    } else {
      renderArmchairMatrix(seatMap.seats);

      // Preselect seats if present in query param (e.g. ?seats=A4,A5)
      const preselect = params.get('seats');
      if (preselect) {
        preselect.split(',').map(s => s.trim()).filter(Boolean).forEach(s => toggleArmchair(s));
      }
    }

  } catch (err) {
    const loading = document.getElementById('seatsLoading');
    if (loading) loading.style.display = 'none';
    window.showToast('Could not load seats: ' + err.message, 'error');
  }

  initSummaryControls(showId);
  initWaitlistModal(showId);
}

function guessTimeFromShowId(showId) {
  const times = ['10:00 AM', '1:30 PM', '5:00 PM', '6:00 PM', '8:30 PM'];
  const parts = (showId || '').split('-');
  const idx = parseInt(parts[parts.length - 1], 10) - 1;
  return times[idx % times.length] || '6:00 PM';
}

function formatDisplayDate(rawDate) {
  if (!rawDate) return 'Sat, 14 Dec 2024';
  if (rawDate.includes('Dec') || rawDate.includes('Oct')) return rawDate;
  try {
    const d = new Date(rawDate);
    if (!isNaN(d.getTime())) {
      return d.toLocaleDateString('en-US', { weekday: 'short', day: '2-digit', month: 'short', year: 'numeric' });
    }
  } catch (_) {}
  return 'Sat, 14 Dec 2024';
}

function populateMoviePanel(movie, show) {
  const poster = document.getElementById('detailPoster');
  if (poster) poster.src = movie.posterUrl || 'https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg';

  const rating = document.getElementById('detailRating');
  if (rating) rating.textContent = movie.rating ? movie.rating.toFixed(1) : '8.6';

  const title = document.getElementById('detailTitle');
  if (title) title.textContent = movie.title || 'Interstellar';

  const duration = document.getElementById('detailDuration');
  if (duration) duration.textContent = `${movie.durationMinutes || 169} min`;

  const lang = document.getElementById('detailLanguage');
  if (lang) lang.textContent = movie.language || 'English';

  const date = document.getElementById('detailDate');
  if (date) date.textContent = formatDisplayDate(show.date);

  const time = document.getElementById('detailTime');
  if (time) time.textContent = show.time || '6:00 PM';

  // Genre pills
  const genreRow = document.getElementById('detailGenrePills');
  if (genreRow && movie.genre) {
    const genres = movie.genre.split(/[\/,]/).map(g => g.trim()).filter(Boolean);
    genreRow.innerHTML = genres.map(g => `<span class="genre-pill">${g}</span>`).join('');
  }
}

function populateSummaryPanel(movie, show) {
  const miniPoster = document.getElementById('miniPoster');
  if (miniPoster) miniPoster.src = movie.posterUrl || 'https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg';

  const miniTitle = document.getElementById('miniTitle');
  if (miniTitle) miniTitle.textContent = movie.title || 'Interstellar';

  const miniGenre = document.getElementById('miniGenre');
  if (miniGenre) miniGenre.textContent = movie.genre || 'Sci-Fi / Adventure';

  const miniDate = document.getElementById('miniDate');
  if (miniDate) miniDate.textContent = formatDisplayDate(show.date);

  const miniTime = document.getElementById('miniTime');
  if (miniTime) miniTime.textContent = show.time || '6:00 PM';
}

function showHouseFullBanner(showId) {
  const banner = document.getElementById('houseFullBanner');
  if (banner) banner.style.display = 'flex';

  const joinBtn = document.getElementById('joinWaitlistBtn');
  if (joinBtn) {
    joinBtn.onclick = () => {
      document.getElementById('waitlistModal').style.display = 'flex';
    };
  }

  window.getWaitlist(showId).then(info => {
    const el = document.getElementById('waitlistCount');
    if (el) el.textContent = `${info.waitingCount || 0} customer(s) currently waiting.`;
  }).catch(() => {});
}

/* ── Render 5x6 Realistic Armchair Matrix ────────────── */
function renderArmchairMatrix(seats2D) {
  const grid = document.getElementById('seatGrid');
  if (!grid) return;

  grid.innerHTML = '';

  for (let r = 0; r < 5; r++) {
    const rowDiv = document.createElement('div');
    rowDiv.className = 'armchair-row';

    // Row Letter on the Left
    const label = document.createElement('div');
    label.className = 'armchair-row-label';
    label.textContent = ROWS[r];
    rowDiv.appendChild(label);

    // 6 Armchairs
    const seatsRow = document.createElement('div');
    seatsRow.className = 'armchair-seats-group';

    for (let c = 0; c < 6; c++) {
      const isBooked = seats2D[r][c] === 1;
      const seatCode = `${ROWS[r]}${c + 1}`;
      const isSelected = _selectedSeats.includes(seatCode);

      const seatBtn = document.createElement('button');
      seatBtn.className = `theatre-armchair ${isBooked ? 'booked' : (isSelected ? 'selected' : 'available')}`;
      seatBtn.setAttribute('data-seat', seatCode);
      seatBtn.setAttribute('title', `Seat ${seatCode} (${isBooked ? 'Booked' : 'Available'})`);

      seatBtn.innerHTML = `
        <span class="armchair-code">${seatCode}</span>
      `;

      if (isBooked) {
        seatBtn.disabled = true;
      } else {
        seatBtn.addEventListener('click', () => toggleArmchair(seatCode));
      }

      seatsRow.appendChild(seatBtn);
    }

    rowDiv.appendChild(seatsRow);
    grid.appendChild(rowDiv);
  }
}

function toggleArmchair(seatCode) {
  if (_selectedSeats.includes(seatCode)) {
    _selectedSeats = _selectedSeats.filter(s => s !== seatCode);
  } else {
    _selectedSeats.push(seatCode);
  }

  // Update visual state of that specific armchair
  const btn = document.querySelector(`.theatre-armchair[data-seat="${seatCode}"]`);
  if (btn) {
    if (_selectedSeats.includes(seatCode)) {
      btn.classList.remove('available');
      btn.classList.add('selected');
    } else {
      btn.classList.remove('selected');
      btn.classList.add('available');
    }
  }

  updateSummaryChipsAndTotals();
}

function updateSummaryChipsAndTotals() {
  const chipsContainer = document.getElementById('selectedSeatChips');
  const countDisplay = document.getElementById('ticketCountDisplay');
  const totalDisplay = document.getElementById('totalAmountDisplay');
  const proceedBtn = document.getElementById('proceedBtn');
  const stepMinus = document.getElementById('stepMinus');

  const count = _selectedSeats.length;
  const total = count * PRICE_PER_TICKET;

  if (countDisplay) countDisplay.textContent = count;
  if (totalDisplay) totalDisplay.textContent = `₹${total}`;
  if (proceedBtn) proceedBtn.disabled = count === 0;
  if (stepMinus) stepMinus.disabled = count === 0;

  if (chipsContainer) {
    if (count === 0) {
      chipsContainer.innerHTML = '<span class="no-seats-placeholder">No seats selected yet. Click any armchair in the hall!</span>';
    } else {
      chipsContainer.innerHTML = _selectedSeats.map(seat => `
        <div class="seat-chip" data-seat="${seat}">
          <span>${seat}</span>
          <button class="chip-remove" aria-label="Remove ${seat}" onclick="event.stopPropagation(); removeSeat('${seat}')">✕</button>
        </div>
      `).join('');
    }
  }
}

window.removeSeat = function(seatCode) {
  toggleArmchair(seatCode);
};

function initSummaryControls(showId) {
  const proceedBtn = document.getElementById('proceedBtn');
  const customerModal = document.getElementById('customerModal');
  const closeCustomer = document.getElementById('closeCustomerModal');
  const cancelCustomer = document.getElementById('cancelCustomerModal');
  const confirmBookingBtn = document.getElementById('confirmBookingBtn');
  const nameInput = document.getElementById('customerNameInput');
  const previewDiv = document.getElementById('modalBookingPreview');

  const stepMinus = document.getElementById('stepMinus');
  const stepPlus = document.getElementById('stepPlus');
  const btnEdit = document.getElementById('btnEditSeats');

  // Edit button resets or focuses
  btnEdit?.addEventListener('click', () => {
    window.showToast('Click any armchair to add or remove seats', 'info');
  });

  // Plus button auto-picks first available seat
  stepPlus?.addEventListener('click', () => {
    const availableBtn = document.querySelector('.theatre-armchair.available');
    if (availableBtn) {
      const code = availableBtn.getAttribute('data-seat');
      toggleArmchair(code);
    } else {
      window.showToast('No more seats available in this row', 'warning');
    }
  });

  // Minus button removes the last selected seat
  stepMinus?.addEventListener('click', () => {
    if (_selectedSeats.length > 0) {
      const last = _selectedSeats[_selectedSeats.length - 1];
      toggleArmchair(last);
    }
  });

  proceedBtn?.addEventListener('click', () => {
    if (_selectedSeats.length === 0) {
      window.showToast('Please select at least one armchair', 'warning');
      return;
    }
    if (previewDiv) {
      previewDiv.innerHTML = `
        <div style="font-size:0.88rem; color:var(--text-muted); margin-bottom:4px;">Seats Selected:</div>
        <div style="font-weight:800; color:var(--accent-purple); font-size:1.1rem;">${_selectedSeats.join(', ')}</div>
        <div style="font-size:0.88rem; color:var(--success); font-weight:700; margin-top:4px;">Total: ₹${_selectedSeats.length * PRICE_PER_TICKET}</div>
      `;
    }
    customerModal.style.display = 'flex';
    nameInput?.focus();
  });

  closeCustomer?.addEventListener('click', () => { customerModal.style.display = 'none'; });
  cancelCustomer?.addEventListener('click', () => { customerModal.style.display = 'none'; });

  customerModal?.addEventListener('click', (e) => {
    if (e.target === customerModal) customerModal.style.display = 'none';
  });

  confirmBookingBtn?.addEventListener('click', async () => {
    const name = nameInput?.value.trim();
    if (!name) {
      window.showToast('Please enter your full name', 'warning');
      nameInput?.focus();
      return;
    }

    confirmBookingBtn.textContent = 'Processing Booking...';
    confirmBookingBtn.disabled = true;

    try {
      const result = await window.createBooking(name, showId, _selectedSeats);
      if (result.success) {
        const bookingId = result.booking?.bookingId || result.bookingId;
        window.showToast('Booking Confirmed! Generating your ticket...', 'success');
        setTimeout(() => {
          window.location.href = `confirmation.html?bookingId=${bookingId}`;
        }, 700);
      } else {
        window.showToast(result.message || 'Booking failed', 'error');
        customerModal.style.display = 'none';
      }
    } catch (err) {
      window.showToast(err.message, 'error');
    } finally {
      confirmBookingBtn.textContent = 'Confirm & Pay';
      confirmBookingBtn.disabled = false;
    }
  });

  nameInput?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') confirmBookingBtn?.click();
  });
}

function initWaitlistModal(showId) {
  const modal = document.getElementById('waitlistModal');
  const closeBtn = document.getElementById('closeWaitlistModal');
  const cancelBtn = document.getElementById('cancelWaitlistModal');
  const confirmBtn = document.getElementById('confirmWaitlistBtn');
  const nameInput = document.getElementById('waitlistNameInput');
  const seatsInput = document.getElementById('waitlistSeatsInput');

  closeBtn?.addEventListener('click', () => { modal.style.display = 'none'; });
  cancelBtn?.addEventListener('click', () => { modal.style.display = 'none'; });

  modal?.addEventListener('click', (e) => {
    if (e.target === modal) modal.style.display = 'none';
  });

  confirmBtn?.addEventListener('click', async () => {
    const name = nameInput?.value.trim();
    const count = parseInt(seatsInput?.value || '1', 10);

    if (!name) {
      window.showToast('Please enter your name', 'warning');
      return;
    }

    confirmBtn.textContent = 'Joining Queue...';
    confirmBtn.disabled = true;

    try {
      const res = await window.joinWaitlist(name, showId, count);
      if (res.success) {
        modal.style.display = 'none';
        window.showToast(`Joined waiting list at position #${res.position}!`, 'success');
        const countEl = document.getElementById('waitlistCount');
        if (countEl) countEl.textContent = `${res.position} customer(s) currently waiting.`;
      } else {
        window.showToast(res.message || 'Could not join waiting list', 'error');
      }
    } catch (err) {
      window.showToast(err.message, 'error');
    } finally {
      confirmBtn.textContent = 'Join Queue';
      confirmBtn.disabled = false;
    }
  });
}

/* =========================================================
   PAGE: CONFIRMATION
   ========================================================= */
async function initConfirmation() {
  const params = new URLSearchParams(window.location.search);
  const bookingId = params.get('bookingId');
  const loading = document.getElementById('confLoading');
  const success = document.getElementById('confSuccess');
  const error = document.getElementById('confError');

  if (!bookingId) {
    if (loading) loading.style.display = 'none';
    if (error) error.style.display = 'flex';
    return;
  }

  try {
    const booking = await window.getBooking(bookingId);
    if (loading) loading.style.display = 'none';

    if (!booking) {
      if (error) error.style.display = 'flex';
      return;
    }

    document.getElementById('conf-booking-id').textContent = booking.bookingId;
    document.getElementById('conf-movie').textContent = booking.movieTitle || await resolveMovieTitle(booking.showId);
    document.getElementById('conf-theatre').textContent = 'PVR - Express Avenue, Chennai';
    document.getElementById('conf-date').textContent = 'Sat, 14 Dec 2024';
    document.getElementById('conf-time').textContent = booking.showTime || guessTimeFromShowId(booking.showId);
    document.getElementById('conf-seats').textContent = (booking.bookedSeats || []).join(', ');
    document.getElementById('conf-count').textContent = (booking.bookedSeats || []).length;
    document.getElementById('conf-customer').textContent = booking.customerName;
    document.getElementById('conf-amount').textContent = `₹${booking.totalAmount}`;

    if (success) success.style.display = 'block';

  } catch (err) {
    if (loading) loading.style.display = 'none';
    if (error) error.style.display = 'flex';
  }
}

async function resolveMovieTitle(showId) {
  try {
    const parts = (showId || '').split('-');
    const movieId = parts[1];
    const movies = await window.getMovies();
    const movie = movies.find(m => m.id === movieId);
    return movie?.title || 'Interstellar';
  } catch {
    return 'Interstellar';
  }
}

/* =========================================================
   PAGE: MY BOOKINGS (with prominent Movie Name!)
   ========================================================= */
let _allBookings = [];

async function initMyBookings() {
  const loading = document.getElementById('bookingsLoading');
  const listDiv = document.getElementById('bookingsList');
  const emptyDiv = document.getElementById('bookingsEmpty');
  const errorDiv = document.getElementById('bookingsError');
  const searchInput = document.getElementById('bookingSearchInput');
  const clearSearch = document.getElementById('clearSearch');

  try {
    _allBookings = await window.getAllBookings();
    if (loading) loading.style.display = 'none';

    if (!_allBookings || _allBookings.length === 0) {
      if (emptyDiv) emptyDiv.style.display = 'flex';
    } else {
      if (listDiv) listDiv.style.display = 'block';
      renderBookings(_allBookings);
    }
  } catch (err) {
    if (loading) loading.style.display = 'none';
    if (errorDiv) errorDiv.style.display = 'flex';
    return;
  }

  searchInput?.addEventListener('input', () => {
    const q = searchInput.value.trim().toLowerCase();
    if (clearSearch) clearSearch.style.display = q ? 'inline-flex' : 'none';

    const filtered = _allBookings.filter(b =>
      (b.bookingId || '').toLowerCase().includes(q) ||
      (b.movieTitle || '').toLowerCase().includes(q) ||
      (b.customerName || '').toLowerCase().includes(q) ||
      (b.showId || '').toLowerCase().includes(q)
    );

    if (filtered.length === 0) {
      const grid = document.getElementById('bookingsGrid');
      if (grid) {
        grid.innerHTML = `
          <div class="empty-state" style="grid-column:1/-1;">
            <h3>No bookings match "${searchInput.value}"</h3>
            <p class="text-muted">Check your Movie Name, Booking ID, or Customer Name.</p>
          </div>`;
      }
    } else {
      renderBookings(filtered);
    }
  });

  clearSearch?.addEventListener('click', () => {
    searchInput.value = '';
    clearSearch.style.display = 'none';
    renderBookings(_allBookings);
  });

  initCancelModal();
}

function renderBookings(bookings) {
  const grid = document.getElementById('bookingsGrid');
  if (!grid) return;

  grid.innerHTML = bookings.map(b => {
    const movieName = b.movieTitle || 'Interstellar';
    const poster = b.moviePosterUrl || 'https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg';
    const isConfirmed = b.status === 'CONFIRMED';

    return `
      <div class="booking-card" id="card-${b.bookingId}">
        <div class="booking-card-poster-wrap">
          <img class="booking-poster" src="${poster}" alt="${movieName}" onerror="this.onerror=null;this.src='https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=500&q=80';" />
          <div class="booking-poster-overlay">
            <span class="status-badge ${isConfirmed ? 'badge-success' : 'badge-error'}">${b.status}</span>
          </div>
        </div>

        <div class="booking-card-content">
          <div class="booking-card-header">
            <div>
              <div class="booking-id-tag">${b.bookingId}</div>
              <!-- Prominent Movie Title -->
              <h2 class="booking-movie-title">${movieName}</h2>
              <div class="booking-show-meta">
                <span>PVR Cinemas, Chennai</span>
                <span>•</span>
                <span>${b.showTime || '6:00 PM'}</span>
              </div>
            </div>

            <div>
              ${isConfirmed ? `
                <button class="btn-danger btn-sm cancel-booking-btn" data-booking-id="${b.bookingId}">
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                  Cancel
                </button>
              ` : ''}
            </div>
          </div>

          <div class="booking-card-body">
            <div class="booking-detail-row">
              <span class="text-muted">Customer Name:</span>
              <strong style="color:var(--text-main);">${b.customerName}</strong>
            </div>

            <div class="booking-detail-row">
              <span class="text-muted">Seats Booked:</span>
              <div class="booked-seats-chips">
                ${(b.bookedSeats || []).map(s => `<span class="seat-badge-chip">${s}</span>`).join('')}
              </div>
            </div>

            <div class="booking-detail-row">
              <span class="text-muted">Total Paid:</span>
              <span class="text-success" style="font-weight:800; font-size:1.05rem;">₹${b.totalAmount}</span>
            </div>

            <div class="booking-detail-row">
              <span class="text-muted">Booking Time:</span>
              <span style="font-size:0.8rem; color:var(--text-muted);">${b.bookingTime}</span>
            </div>
          </div>
        </div>
      </div>
    `;
  }).join('');

  grid.querySelectorAll('.cancel-booking-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      openCancelDialog(btn.getAttribute('data-booking-id'));
    });
  });
}

let _targetCancelId = null;

function initCancelModal() {
  const modal = document.getElementById('cancelModal');
  const noBtn = document.getElementById('cancelModalNo');
  const yesBtn = document.getElementById('cancelModalYes');

  noBtn?.addEventListener('click', () => {
    modal.style.display = 'none';
    _targetCancelId = null;
  });

  modal?.addEventListener('click', (e) => {
    if (e.target === modal) {
      modal.style.display = 'none';
      _targetCancelId = null;
    }
  });

  yesBtn?.addEventListener('click', async () => {
    if (!_targetCancelId) return;

    yesBtn.textContent = 'Cancelling...';
    yesBtn.disabled = true;

    try {
      await window.cancelBooking(_targetCancelId);
      modal.style.display = 'none';
      window.showToast(`Booking ${_targetCancelId} cancelled. Seats released!`, 'success');

      _allBookings = _allBookings.map(b =>
        b.bookingId === _targetCancelId ? { ...b, status: 'CANCELLED' } : b
      );
      renderBookings(_allBookings);

    } catch (err) {
      window.showToast('Cancellation error: ' + err.message, 'error');
    } finally {
      yesBtn.textContent = 'Yes, Cancel';
      yesBtn.disabled = false;
      _targetCancelId = null;
    }
  });
}

function openCancelDialog(bookingId) {
  _targetCancelId = bookingId;
  const modal = document.getElementById('cancelModal');
  const infoDiv = document.getElementById('cancelBookingInfo');
  const booking = _allBookings.find(b => b.bookingId === bookingId);

  if (infoDiv && booking) {
    infoDiv.innerHTML = `
      <div style="background:var(--bg-secondary); border:1px solid var(--border); border-radius:8px; padding:14px; margin-bottom:16px;">
        <div style="font-size:0.8rem; color:var(--text-muted);">${booking.bookingId}</div>
        <div style="font-weight:800; font-size:1.15rem; color:var(--text-main); margin-top:2px;">${booking.movieTitle || 'Interstellar'}</div>
        <div style="font-size:0.875rem; color:var(--text-muted); margin-top:4px;">Customer: <strong>${booking.customerName}</strong></div>
        <div style="font-size:0.875rem; color:var(--accent-purple); font-weight:700; margin-top:2px;">Seats: ${(booking.bookedSeats || []).join(', ')}</div>
      </div>`;
  }

  if (modal) modal.style.display = 'flex';
}
