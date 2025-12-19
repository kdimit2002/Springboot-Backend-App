![Architecture Diagram](docs/architecture.png)

## Architecture (High-level)

- **Frontend:** React (Vite) SPA served as static assets.
- **Backend:** Spring Boot application (REST API + schedulers + async tasks).
- **Auth:** Firebase Authentication
  - Frontend uses Firebase Client SDK to sign in and obtain an ID token.
  - Backend verifies the token using Firebase Admin SDK (JWT verification at the API boundary).
- **Database:** PostgreSQL stores application data (users, auctions, bids).
- **Storage:** Images are stored in **Cloudflare R2** and served via **public URLs**.
- **Image flow:** Backend uploads images to R2, stores the public URL in the DB, and returns URLs to the frontend. The browser then loads images directly from R2.
- **Failover:** API runs in an active–passive setup behind a load balancer (routes traffic to Primary; fails over to Standby on health check failure).

---

## Tech Stack
- Java 17
- Spring Boot
- PostgreSQL
- Firebase Authentication (Client + Admin SDK)
- Cloudflare R2

---

## Main External APIs Communication Flows

### Firebase Authentication

#### Sign In Flow
- **User signs in on the client** using email/password via Firebase Authentication.
- Firebase returns **`idToken`, `refreshToken`, `localId`** to the browser.
- The browser calls the backend **`GET /api/auth/login`** with `Authorization: Bearer <idToken>`.
- Backend **verifies** the token using **Firebase Admin SDK** and returns the application user context.

![Sign In Flow](docs/SignInFlow.png)

#### Sign Up Flow
- The client checks **username availability** via the backend before creating an auth account.
- The client completes the Firebase registration flow and obtains a valid **ID token**.
- The browser sends the final signup payload to the backend with `Authorization: Bearer <idToken>`.
- Backend verifies the token, persists the user in PostgreSQL, and returns the created user/auth info.
- In case of failures after account creation, the flow supports **rollback** (cleanup of incomplete Firebase accounts).

![Sign Up Flow](docs/SignUpFlow.png)

---

### Cloudflare R2 Object Storage

#### Get Image Flow
- The browser fetches application data from the backend (e.g., auctions), which includes **public image URLs** stored in the DB.
- The browser then downloads images **directly from Cloudflare R2** using those URLs.
- This keeps the backend out of the heavy bandwidth path and improves performance.

![Get Image Flow](docs/R2StorageGetImageFlow.png)

#### Upload Image Flow
- The browser uploads an image to the backend (protected endpoint).
- Backend uploads the binary to **Cloudflare R2** and generates a **public URL**.
- Backend stores the image URL/metadata in PostgreSQL and returns the URL to the client.

![Upload Image Flow](docs/R2StorageUploadImageFlow.png)
