![Architecture Diagram](docs/architecture.png)
*Diagram shows an optional active–passive deployment model; current deployment uses a single instance.*

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
- **Scheduler Execution Model:** Scheduled jobs are enabled only on the primary instance, if primary is down,then secondary's instance schedulers are automatically enabled
---

## Tech Stack
- Java 17
- Spring Boot
- PostgreSQL
- Firebase Authentication (Client + Admin SDK)
- Cloudflare R2

---

## System Internals (Design Notes)

### Persistence (JPA / Database)
- **JPA/Hibernate** is used for persistence and domain modeling.
- An **Admin user** is created manually in the database for security.

### Async Processing (Notifications / Emails / Auditing)
- Notifications and email events are handled **asynchronously** to keep request latency low.
- Each Api call is **logged to the database** to support:
  - **Auditability** (who/what/when),
  - **Analytics** 

### Caching Strategy
- **Auction caching (Cache Manager):**
  - Auction reads are cached to reduce DB load.
- **Rate limiting cache:**
  - Cache-backed counters are used to enforce request rate limiting efficiently without hitting the DB.

### Reliability: Retry Services 
To reduce failure rates caused by transient connectivity issues:
- **FirebaseRetryService** retries transient Firebase calls, in order to maintain a high level of consistency with the database.. 
- **R2RetryService** retries transient Cloudflare R2 operations (uploads/updates) to improve success rates.
- Retries are designed to be **bounded** (no infinite loops) and safe for transient network errors.

### Schedulers (Background Jobs)
Schedulers are used for lifecycle management and consistency:

- **Auction expiration scheduler**
  - Periodically marks auctions as **expired**.

- **Reminders scheduler**
  - Sends reminder notifications/emails.

- **Nightly consistency scheduler (DB ↔ Firebase)**
  - Ensures the system remains consistent between **PostgreSQL** (application source-of-truth) and **Firebase Auth** (identity provider).
  - Example actions:
    - If a user exists in **Firebase** but is missing in **DB** → the Firebase user is **deleted**.
    - If Firebase user metadata differs from DB → Firebase data is **updated** to match DB.

### Security (API Boundary)
- **RateLimiter filter** (cache-backed) protects the API from abuse and reduces brute-force attempts.
  - For public Apis cache user's IP
  - For secured Apis cache user's firebase Id.
- **Firebase auth filter** enforces:
  - Presence/validity of `Bearer idToken`,
  - User existence in the application database (prevents "valid token but unknown user" edge cases).

#### Planned Security Improvements
- **XSSSanitizationFilter** is planned to sanitize untrusted inputs at the API boundary.

---

## Realtime Features (Roadmap)

### WebSockets
Real-time communication supports:
- **Live bids updates** in auctions.
- **Auction chat** between participants with near real-time delivery.

Implementation includes, Event-driven updates.

WebSocket communication does not use an intermediate message broker
- As a result, WebSocket connections are handled by a **single active application instance** at any given time.



---

## Main External APIs Communication Flows

### Firebase Authentication

#### Planned: Sign In Flow
- **User signs in on the client** using email/password via Firebase Authentication.
- Firebase returns **`idToken`, `refreshToken`, `localId`** to the browser.
- The browser calls the backend **`GET /api/auth/login`** with `Authorization: Bearer <idToken>`.
- Backend **verifies** the token using **Firebase Admin SDK** and returns the application user context.

![Sign In Flow](docs/SignInFlow.png)

#### Planned: Sign Up Flow
- The client checks **username availability** via the backend before creating an auth account.
- The client completes the Firebase registration flow and obtains a valid **ID token**.
- The browser sends the final signup payload to the backend with `Authorization: Bearer <idToken>`.
- Backend verifies the token, persists the user in PostgreSQL, and returns the created user/auth info.
- In case of failures after account creation, the flow supports **rollback** (cleanup of incomplete Firebase accounts).

![Sign Up Flow](docs/SignUpFlow.png)

---

### Cloudflare R2 Object Storage

#### Planned: Get Image Flow
- The browser fetches application data from the backend (e.g., auctions), which includes **public image URLs** stored in the DB.
- The browser then downloads images **directly from Cloudflare R2** using those URLs.
- This keeps the backend out of the heavy bandwidth path and improves performance.

![Get Image Flow](docs/R2StorageGetImageFlow.png)

#### Upload Image Flow
- The browser uploads an image to the backend (protected endpoint).
- Backend uploads the binary to **Cloudflare R2** and generates a **public URL**.
- Backend stores the image URL/metadata in PostgreSQL and returns the URL to the client.

![Upload Image Flow](docs/R2StorageUploadImageFlow.png)
