/**
 * CineBook — Public Deployment Configuration
 *
 * How to connect your frontend on Vercel to your public Java backend:
 * 1. Deploy your Java backend on Render or Railway (e.g. https://cinebook-backend.onrender.com)
 * 2. Paste your backend URL below into BACKEND_URL:
 *    Example: BACKEND_URL: 'https://cinebook-backend.onrender.com'
 * 3. Commit and push to GitHub — Vercel will instantly redeploy!
 *
 * NOTE: If BACKEND_URL is left empty:
 * - On localhost, CineBook automatically uses http://localhost:8080/api.
 * - On deployed sites, you can also set or change the URL anytime using the settings cog or prompt.
 */
window.CINEBOOK_CONFIG = {
  // Set your public backend URL here (e.g. 'https://cinebook-backend.onrender.com'):
  BACKEND_URL: ''
};
