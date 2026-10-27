# StayMatch frontend

React + Vite search UI for PGs, rooms, and rental homes. Search results and match percentages are returned by the StayMatch Spring Boot matching endpoint. The frontend sends relative `/api/...` requests; Vite proxies those requests to `http://localhost:8080` during development.

## Start both applications (Windows PowerShell)

1. Start the backend from the repository root. The existing launcher reads `DB_PASSWORD` from the project `.env` file:

   ```powershell
   cd C:\Users\nitis\OneDrive\Desktop\StayMatch
   .\run-local.ps1
   ```

   The backend uses the existing local PostgreSQL configuration and must be able to connect to its configured database.

2. In a second PowerShell window, start the frontend:

   ```powershell
   cd C:\Users\nitis\OneDrive\Desktop\StayMatch\frontend
   npm install
   npm run dev
   ```

3. Open <http://localhost:5173/>. On initial load it requests `GET /api/v1/properties/matches?city=Hyderabad`. Search fields are sent as `city` or `area`, `type`, `maxRent`, `bhk`, `sharingType`, `gender`, and comma-separated `amenities`; empty filters are omitted. Search results display the backend's `matchPercentage` unchanged.

Use the controls and click **Search stays** to submit the location, type, and budget. BHK, sharing, gender, and amenity filters refresh results when changed. Reset returns to the initial Hyderabad search.

## Production build

```powershell
cd C:\Users\nitis\OneDrive\Desktop\StayMatch\frontend
npm run build
```

Property photos and Google Fonts load from public URLs and need internet access. The frontend does not connect directly to PostgreSQL.
