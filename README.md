# CampusCycle

KUET cycle-sharing desktop application (JavaFX + Supabase RPCs). Pilot runs with payments `UNPAID`; no collection until a Bangladesh provider + webhook verification lands.

## Run locally

1. Install JDK 21+ and Maven.
2. Copy `.env.example` to `.env` and add only public values: `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `FREEROUTE_API_KEY` (optional, for cycle routes).
3. Apply `supabase/migrations/*` in order to the Supabase project (latest: `202609260001_live_alignment.sql` aligns live RPC names with the Java client); verify RLS revokes + RPC grants.
3. Apply `supabase/migrations/*` to a clean Supabase project; verify RLS revokes + RPC grants.
4. Run `.\mvnw.cmd test` then `.\mvnw.cmd javafx:run`.

Without Supabase config the app runs on the offline demo store with a banner on login. The campus map is Bing-only (Leaflet + Esri tiles in `bing-map.html`, no Google key). Restrict the FreeRoute key to your scope and rotate if exposed.

## Release smoke + rollback

Smoke: sign-in (KUET email) -> Fleet catalog -> book 15-180m -> Active Journey return -> Passbook CSV + dispute (RETURNED only) -> Support thread -> Admin approvals/disputes.
Rollback: keep prior `target/` build; `git revert` migration apply via Supabase dashboard; clear `%USERPROFILE%\.campuscycle` only for local cache issues, never for server state.

The application starts without a Maps key, but the campus map will show a configuration state until `GOOGLE_MAPS_API_KEY` is available. For Google Cloud setup, create an API key, enable **Maps JavaScript API**, and apply API restrictions. The key is a public client key, but it still must be restricted and kept out of Git.

Selecting a cycle marker draws a FreeRoute cycling route from KUET Main Gate. Route requests use only starting and destination coordinates and do not send owner or renter details.

## Map data

When `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` are configured, the map reads the `map_cycle_locations` RPC. The migration deliberately returns only approved, available cycle locations and no owner contact details. The development fallback uses three local sample locations around KUET; it is never used when Supabase is configured.
