# Smart Health Assistant mobile app

The frontend is an Expo and React Native app. Install dependencies and start Expo from this directory:

```powershell
npm install
npm start
```

Scan the QR code with Expo Go, or press `a` to open an Android emulator. Run `npm run typecheck` to check TypeScript.

Login is kept in memory only. Switching apps keeps the session while it is running; a fresh app process or JavaScript reload requires login again. Expo Go and the operating system may keep a backgrounded process alive even after its screen is dismissed. Use Log out to revoke the session on the backend; force-closing cannot reliably notify the server, so a session not explicitly revoked remains valid until its 30-day expiry. On first startup after this change, any previously saved token is removed and the app attempts to revoke it.

Search History is available only when signed in. Condition searches made while signed in are stored separately per account and backend on that device; public searches are not saved. Old shared history is left on the device but not displayed because its ownership is unknown. This local storage is not encrypted and is not protected against someone with access to the device's application data.

Condition search displays the NHS condition page inside the mobile app. The backend generates a page URL from the entered name, so some names may lead to an NHS not-found page. On web, use the provided link because NHS pages do not permit iframe embedding.

For a physical phone to reach a backend running on your computer, set `EXPO_PUBLIC_API_BASE_URL` in `.env.local` to your computer's LAN IP and backend port (for example, `http://192.168.1.25:8080`). Keep the backend running, make sure the phone and computer are on the same network, and allow inbound TCP port 8080 through the computer's firewall. `localhost` refers to the phone itself on a physical device. Restart Expo after changing `.env.local` so the new URL is included in the app bundle. Expo public environment variables are included in the app bundle, so never put private API credentials in them.
