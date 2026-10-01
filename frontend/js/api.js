/**
 * CineBook — API Client Module
 * frontend/js/api.js
 *
 * Communicates with the Java Spring Boot backend on http://localhost:8080/api.
 * Works both as a standard browser script (attaching to window) and as an ES module.
 */

/**
 * Resolves the backend API base URL using priority:
 * 1. window.CINEBOOK_CONFIG.BACKEND_URL (from js/config.js)
 * 2. localStorage.getItem('cinebook_backend_url') (in-browser custom setting)
 * 3. Localhost / local files -> http://localhost:8080/api
 * 4. Deployed origin -> ${window.location.origin}/api
 */
function getApiBaseUrl() {
  if (typeof window !== 'undefined') {
    // 1. Check js/config.js
    if (window.CINEBOOK_CONFIG && window.CINEBOOK_CONFIG.BACKEND_URL && window.CINEBOOK_CONFIG.BACKEND_URL.trim()) {
      const u = window.CINEBOOK_CONFIG.BACKEND_URL.trim().replace(/\/+$/, '');
      return u.endsWith('/api') ? u : `${u}/api`;
    }
    // 2. Check localStorage
    try {
      const saved = localStorage.getItem('cinebook_backend_url');
      if (saved && saved.trim()) {
        const u = saved.trim().replace(/\/+$/, '');
        return u.endsWith('/api') ? u : `${u}/api`;
      }
    } catch (_) {}

    // 3. Local development
    const host = window.location.hostname;
    const proto = window.location.protocol;
    if (proto === 'file:' || host === 'localhost' || host === '127.0.0.1' || host === '0.0.0.0') {
      return 'http://localhost:8080/api';
    }

    // 4. Same origin (if backend serves frontend static files)
    return `${window.location.origin}/api`;
  }
  return 'http://localhost:8080/api';
}

function setBackendUrl(url) {
  if (url && url.trim()) {
    localStorage.setItem('cinebook_backend_url', url.trim());
  } else {
    localStorage.removeItem('cinebook_backend_url');
  }
  window.location.reload();
}

/**
 * Core fetch wrapper with timeout and JSON parsing.
 */
async function request(endpoint, options = {}) {
  const baseUrl = getApiBaseUrl();
  const defaultHeaders = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  };

  const config = {
    ...options,
    headers: {
      ...defaultHeaders,
      ...(options.headers || {}),
    },
  };

  let response;
  try {
    response = await fetch(`${baseUrl}${endpoint}`, config);
  } catch (netErr) {
    console.error(`[API Network Error] ${endpoint}:`, netErr);
    throw new Error(`Cannot connect to Java backend at ${baseUrl}. If hosting on Render free tier, please wait ~30 seconds for the server to wake up.`);
  }

  if (!response.ok) {
    let errorMsg = `Server returned HTTP ${response.status}`;
    try {
      const errBody = await response.json();
      if (errBody) {
        if (errBody.message) errorMsg = errBody.message;
        else if (errBody.error) errorMsg = errBody.error;
      }
    } catch (_) {
      // Body was not JSON
    }
    const err = new Error(errorMsg);
    err.status = response.status;
    throw err;
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}

/* =========================================================
   MOVIES API
   ========================================================= */

async function getMovies() {
  return await request('/movies');
}

async function getMovie(id) {
  return await request(`/movies/${id}`);
}

/* =========================================================
   SHOWS API
   ========================================================= */

async function getShows() {
  return await request('/shows');
}

async function getShowsByMovie(movieId) {
  return await request(`/shows/movie/${movieId}`);
}

async function getShow(showId) {
  return await request(`/shows/${showId}`);
}

async function getSeatMap(showId) {
  return await request(`/shows/${showId}/seats`);
}

/* =========================================================
   BOOKINGS API
   ========================================================= */

async function createBooking(customerName, showId, seatLabels) {
  return await request('/bookings', {
    method: 'POST',
    body: JSON.stringify({ customerName, showId, seatLabels }),
  });
}

async function getBooking(bookingId) {
  return await request(`/bookings/${bookingId}`);
}

async function getAllBookings() {
  return await request('/bookings');
}

async function cancelBooking(bookingId) {
  return await request(`/bookings/${bookingId}`, {
    method: 'DELETE',
  });
}

/* =========================================================
   WAITING LIST API (matches BookingController.java)
   ========================================================= */

async function joinWaitlist(customerName, showId, seatsRequested) {
  return await request('/bookings/waitlist', {
    method: 'POST',
    body: JSON.stringify({ customerName, showId, seatsRequested }),
  });
}

async function getWaitlist(showId) {
  return await request(`/bookings/waitlist/${showId}`);
}

async function getWaitlistInfo(showId) {
  return await getWaitlist(showId);
}

/* =========================================================
   TOAST NOTIFICATIONS
   ========================================================= */

function showToast(message, type = 'success') {
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    container.className = 'toast-container';
    document.body.appendChild(container);
  }

  const icons = {
    success: '✓',
    error: '✕',
    info: 'ℹ',
    warning: '⚠',
  };

  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.innerHTML = `
    <span class="toast-icon">${icons[type] || icons.info}</span>
    <span class="toast-message">${message}</span>
  `;

  container.appendChild(toast);

  const dismiss = () => {
    toast.classList.add('hiding');
    setTimeout(() => {
      if (toast.parentNode) toast.parentNode.removeChild(toast);
    }, 250);
  };

  const timer = setTimeout(dismiss, 3500);

  toast.addEventListener('click', () => {
    clearTimeout(timer);
    dismiss();
  });
}

/* =========================================================
   EXPORT / GLOBAL ATTACHMENT
   ========================================================= */

// Expose directly to window so standard <script src="js/api.js"> works without CORS
if (typeof window !== 'undefined') {
  window.getMovies = getMovies;
  window.getMovie = getMovie;
  window.getShows = getShows;
  window.getShowsByMovie = getShowsByMovie;
  window.getShow = getShow;
  window.getSeatMap = getSeatMap;
  window.createBooking = createBooking;
  window.getBooking = getBooking;
  window.getAllBookings = getAllBookings;
  window.cancelBooking = cancelBooking;
  window.joinWaitlist = joinWaitlist;
  window.getWaitlist = getWaitlist;
  window.getWaitlistInfo = getWaitlistInfo;
  window.showToast = showToast;
  window.getApiBaseUrl = getApiBaseUrl;
  window.setBackendUrl = setBackendUrl;
  window.CineAPI = {
    getMovies,
    getMovie,
    getShows,
    getShowsByMovie,
    getShow,
    getSeatMap,
    createBooking,
    getBooking,
    getAllBookings,
    cancelBooking,
    joinWaitlist,
    getWaitlist,
    getWaitlistInfo,
    showToast,
    getApiBaseUrl,
    setBackendUrl,
  };
}
