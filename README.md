# Smart Health Assistant

An educational health information application with condition information, symptom-related educational results, nearby care search, and account authentication.

> **Health disclaimer:** This app is for educational purposes only. It must not diagnose conditions or replace advice from a qualified healthcare professional. Users should seek professional care for medical concerns and emergency help for emergencies.

## Technology

- Java 17
- Spring Boot 4.1.2
- Maven
- H2 or PostgreSQL with Spring Data JPA
- Spring MVC for HTTP APIs
- Bean Validation for validating API input
- Spring Security with BCrypt password hashing and bearer sessions for account auth
- Spring Mail for configured password-reset emails

The current dependencies are listed in [`backend/pom.xml`](./backend/pom.xml). Google Places is accessed from the backend using a server-side key; the mobile map also needs a platform-restricted Maps SDK key for Android builds.

## Project layout

```text
backend/src/main/java/fi/haagahelia/smarthhealth/
├── config/             # Application and NHS API configuration
│   ├── NhsApiConfig.java
│   └── SecurityConfig.java
├── domain/             # Account and authentication entities
├── integration/nhs/    # NHS API client
├── repository/         # Account and authentication repositories
├── service/            # Application and NHS business logic
└── web/                # API controllers, DTOs, and error handling
backend/src/main/resources/
└── application.properties
frontend/components/     # Expo React Native screens and shared UI
frontend/services/       # API client and local feature integrations
FinalProject.md          # Initial feature requirements
```

The frontend is an Expo React Native application. The backend includes account endpoints, BCrypt password hashes, bearer sessions, password-reset tokens, a nearby-care proxy, a public condition-search route, and a protected symptom-search route. Condition-name searches generate NHS page URLs; symptom searches still use the configurable NHS API client. Its search path, query parameter, API version, subscription-key header, and response schema must be confirmed against the NHS subscription before relying on live results. See [FinalProject.md](./FinalProject.md) for requirements and [FEATURES_AND_CODE_GUIDE.md](./FEATURES_AND_CODE_GUIDE.md) for file flows and setup.

**Known limitation:** Search Symptoms is retained for a future update and is not currently working with a verified data source. A future update should integrate a suitable authorized API for symptom and condition searches instead of relying on the current NHS URL construction workaround.

The intended access model is:

- NHS condition-information searches are available without signing in.
- Symptom search is a separate educational flow, requires at least two distinct non-blank symptoms, and must not present possible matches as a diagnosis.
- Saving and viewing sickness history requires a signed-in user. History must be private to that account; every backend operation must enforce ownership, and records must never be exposed publicly or to another user.
- Treat sickness history as sensitive health data: avoid real personal data in demos and tests, keep it out of logs, and provide appropriate deletion controls.

The current frontend uses Expo / React Native. The app implements its own backend account endpoints rather than an external identity provider. General condition-search history is local to the device; symptom-derived sickness history remains a separate feature and must be authenticated and private to the owning account.

## Prerequisites

- JDK 17
- PostgreSQL, only if using a PostgreSQL datasource instead of the default in-memory H2 database

## Run locally

For temporary local development, run the backend with the default in-memory H2 database:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

H2 data is lost when the backend stops. To use PostgreSQL instead, create a database (for example, `smart_health`), then set the connection details in PowerShell before starting the backend:

```powershell
cd backend
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/smart_health"
$env:SPRING_DATASOURCE_USERNAME = "postgres"
$env:SPRING_DATASOURCE_PASSWORD = "your-local-password"
.\mvnw.cmd spring-boot:run
```

Keep credentials out of source control. The database must exist before starting the application.

To create a local admin account at startup, set `APP_ADMIN_EMAIL` and `APP_ADMIN_PASSWORD` in the backend process environment or in the Git-ignored `backend/.env.admin` file. The account is created only if the email is unused, and the password is stored as a BCrypt hash. An existing regular user is never promoted automatically. The admin role currently has no extra management features. For a school demo a short password can be configured, but use a strong password and HTTPS outside an isolated demo. With in-memory H2, the configured account is recreated on each restart.

Run the backend tests from the `backend/` directory:

```powershell
.\mvnw.cmd test
```

## Run the frontend

The Expo React Native frontend is in the sibling `frontend/` folder and runs separately from Spring Boot during development. From the project root:

```powershell
cd frontend
npm install
npx expo start
```

Open the project in Expo Go or use an Android/iOS development build. Keep the backend running, and set `EXPO_PUBLIC_API_BASE_URL` in `frontend/.env.local` to the computer's LAN address and backend port (for example, `http://192.168.1.25:8080`), not `localhost`. The phone and computer must be on the same network; allow inbound TCP port 8080 through the computer's firewall and restart Expo after changing the URL.

## Planned integrations

### Authentication

The current implementation uses backend accounts with BCrypt password hashes and opaque bearer sessions. Signup, login, logout, forgot-password, and reset-password endpoints are available under `/api/auth`. Password reset requires SMTP configuration. If an external identity provider is adopted later, replace or adapt these endpoints and validate its tokens in the backend.

Public condition and nearby-care searches remain separate from authenticated routes. Protect every future sickness-history operation with authentication and per-user authorization, and collect or retain as little sensitive health data as possible. Use HTTPS in deployed environments.

### Nearby hospitals and clinics

The backend uses Google Places API (New) Nearby Search to find hospitals or pharmacies within 5 km of the device location. Set `GOOGLE_PLACES_API_KEY` in the backend environment or Git-ignored `backend/.env`, enable Places API (New) and billing, and check key restrictions if requests are rejected. Use a separate server-side key restricted to Places API (New) and the server's public IP; an Android-restricted Maps SDK key cannot be used for backend requests. Restart the backend after changing the key. The existing Spring MVC dependency provides Spring's HTTP client APIs; a separate Google Maps Java dependency is not required for making REST requests. A frontend that renders a map may need the appropriate Maps JavaScript SDK separately.

Store the server-side Google API key in an environment variable or secret manager, restrict the key to the required APIs, and review Google Maps Platform pricing and quotas before enabling billing. Do not commit the key. Nearby search returns place results; route distance or travel time may require a separate routing API.

NHS health information and Google Places are separate integrations: use the NHS client for NHS catalogue APIs and add a separate integration for Google Places if nearby-care search is implemented.

## Initial development goals

1. Add public condition search and source-attributed health information from the selected NHS API.
2. Add carefully scoped educational symptom information with prominent medical safety guidance; do not present it as diagnosis or treatment advice.
3. Add authentication and private, per-user sickness history.
4. Add nearby care search with explicit user permission to use their location.
