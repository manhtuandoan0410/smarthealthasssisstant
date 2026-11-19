# Smart Health Assistant: Features and Code Guide

This document describes the current implementation added for local general-search history, account sign-in, and nearby hospitals/clinics. The app is for educational information, not diagnosis or treatment advice.

## Frontend flow

The frontend is an Expo / React Native app in `frontend/`; Expo's single entry point is `frontend/App.tsx`.

- `frontend/App.tsx` registers Home, Search History, Symptoms, Nearby Care, and Login routes. Each screen uses the shared `ButtonAppBar` as its navigation header. `frontend/src/App.tsx` was removed to avoid maintaining a second app entry file.
- `frontend/components/ButtonAppBar.tsx` renders the teal headbar and its menu. The header offers login or logout, and hides search history and symptom search unless the session is signed in.
- `frontend/components/Mainpage.tsx` calls the public `/api/conditions?query=...` endpoint. The API returns one NHS condition-page link, displayed in an in-app WebView on mobile. Searches made while signed in are saved locally for that account as `{ id, query, answer, searchedAt }`. The web version offers the link instead because NHS pages prohibit iframe embedding. The URL is generated from the search text and may not correspond to an existing NHS page.
- `frontend/components/SearchHistory.tsx` checks the session and displays the current account's local condition searches. The old shared device history is not displayed or migrated because its entries cannot be attributed to individual accounts.
- `frontend/components/SymptomsSearch.tsx` checks the current session before showing symptom input, collects at least two distinct symptoms, and calls the protected `/api/symptoms/search` route. It shows the educational disclaimer and renders possible matches with `ConditionResult`.
- `frontend/components/ConditionResult.tsx` is the shared renderer for condition name, causes, symptoms, and treatment.
- `frontend/components/Login.tsx` provides login, signup, forgot-password, and reset-password modes. Successful login or signup returns to Home. `frontend/services/AuthContext.tsx` shares session state; the bearer token is held only in memory and is lost on an app restart or reload. Previously stored tokens are removed on startup.
- `frontend/components/NearbyCare.tsx` asks for foreground location only after the user presses the location button, searches hospitals or pharmacies within 5 km, and shows returned place markers, address, rating, phone link, and map link.
- `frontend/services/api.ts` sends requests to the configured backend. It does not contain NHS, Google Places, or mail credentials.
- `frontend/types.ts` contains the data contracts used by the screens and API client.
- `frontend/app.config.ts` adds location permission text and passes optional native map SDK keys from environment variables to the Expo config plugin.

## Backend flow

The backend is a Spring Boot application in `backend/`.

- `web/AppUserController.java` exposes `/api/auth/signup`, `/login`, `/forgot-password`, `/reset-password`, and `/logout`.
- `service/AuthService.java` validates accounts, hashes passwords, issues sessions, and manages reset tokens. Passwords are **hashed with BCrypt**, not encrypted reversibly. Raw passwords are not stored. Reset tokens expire after 15 minutes; the database stores only their SHA-256 hashes. Reset requests return the same message whether or not an account exists.
- `domain/AppUser.java` stores account profile information and the BCrypt `passwordHash`.
- `domain/AuthSession.java` stores a hash of the issued bearer token and its expiry. `config/BearerTokenFilter.java` validates bearer tokens for protected backend routes.
- `domain/PasswordResetToken.java` stores a hash of each one-time password-reset token and its expiry.
- `repository/` provides Spring Data repositories for these records.
- `config/SecurityConfig.java` configures stateless bearer authentication, BCrypt, and public auth/condition/nearby routes.
- `config/AdminAccountInitializer.java` optionally creates an admin from `APP_ADMIN_EMAIL` and `APP_ADMIN_PASSWORD` at startup without promoting an existing regular user. The admin role has no additional management features yet.
- `web/NearbyCareController.java` validates location and category query parameters.
- `service/NearbyCareService.java` calls Google Places API (New) from the backend and maps its results to `NearbyCareResponse`. The Places key is not shipped in the app.
- `web/NhsController.java` exposes public GET `/api/conditions?query=...`.
- `web/SymptomSearchController.java` exposes POST `/api/symptoms/search`; Spring Security requires a valid bearer session. `web/dto/SymptomSearchRequest.java` enforces two to eight non-blank symptoms.
- `integration/nhs/NhsApiClient.java` calls the configured NHS search endpoint with the subscription-key header and normalizes common list response wrappers.
- `service/NhsService.java` generates NHS page URLs for condition-name searches. For symptom searches, it maps upstream API results to `ConditionResponse` fields and ranks candidates by overlap across the entered symptoms. This is a lightweight possibility ranking, not a medical diagnostic model.

General Search History and symptom-derived Sickness History are intentionally different. The current Search History screen contains only the general condition searches stored on-device. Any future sickness-history feature must be a separate authenticated feature with per-user authorization as described in the root README and `FinalProject.md`.

Search Symptoms is retained for future work but does not currently have a verified working data source. A future update should integrate a suitable authorized API for both symptom and condition searches, replacing the condition URL construction workaround.

The NHS catalogue documentation could not be retrieved during implementation. Confirm the selected subscription's endpoint path, query parameter, API version, response schema, and key header before relying on live NHS results; these are configurable below.

## Configuration

Never commit credentials or real user health data. Create local environment configuration outside source control.

Frontend (`frontend/.env.local`):

```dotenv
EXPO_PUBLIC_API_BASE_URL=http://YOUR_DEVELOPMENT_HOST:8080
GOOGLE_MAPS_ANDROID_API_KEY=restricted_android_maps_sdk_key
GOOGLE_MAPS_IOS_API_KEY=restricted_ios_maps_sdk_key
```

The `EXPO_PUBLIC_` API base URL is bundled into the client and is not a secret. Native map SDK keys are also present in the built app; restrict them to the application identifier and Maps SDK. For Android development on a device, use the computer's reachable LAN address, not `localhost`. Keep the backend running, allow inbound TCP port 8080 through the computer's firewall, and restart Expo after changing this URL. Spring binds to all interfaces by default for local device testing; restrict access appropriately on untrusted networks.

Backend environment variables:

```text
GOOGLE_PLACES_API_KEY=server_side_places_api_key
NHS_API_KEY=nhs_subscription_key
NHS_API_BASE_URL=https://api.nhs.uk
NHS_API_VERSION=2
NHS_API_SEARCH_PATH=/search
NHS_API_QUERY_PARAMETER=query
CORS_ALLOWED_ORIGINS=http://localhost:8081,http://localhost:19006
MAIL_HOST=your_smtp_host
MAIL_PORT=587
MAIL_USERNAME=your_smtp_username
MAIL_PASSWORD=your_smtp_password
MAIL_FROM=verified_sender_address
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
```

For this development scaffold, Hibernate schema update is enabled by default so the new account/session tables and password-hash column are created when the backend starts. Set `JPA_DDL_AUTO` to `validate` or another deployment-appropriate value when using managed database migrations in production; do not rely on automatic schema updates for production data.

`GOOGLE_PLACES_API_KEY` is a server-side Places API key restricted to the Places API. SMTP must be configured for password-reset emails to arrive; without it, the API keeps its generic response but no email can be delivered. Do not log passwords, reset tokens, bearer tokens, precise coordinates, or health-search answers.

## Validation and run commands

From `frontend/`:

```powershell
npm run typecheck
npx expo start
```

From `backend/`:

```powershell
.\mvnw.cmd test
```

The mobile app needs foreground location permission for Nearby Care. Its coordinates are sent to the configured backend for the selected nearby search and forwarded to Google Places. General condition-search history remains in local, unencrypted AsyncStorage, separated by account and backend. This is not a secure boundary against someone with device or application storage access.