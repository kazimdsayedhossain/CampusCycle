# CampusCycle

KUET cycle-sharing desktop application. This repository is being rebuilt from the production plan in `plan.md`.

## Run locally

1. Install JDK 24 or newer and Maven.
2. Copy `.env.example` to `.env` and add public Supabase configuration plus a restricted Google Maps JavaScript API key. Add `FREEROUTE_API_KEY` to enable cycle routes.
3. Run `mvn javafx:run`.

The application starts without a Maps key, but the campus map will show a configuration state until `GOOGLE_MAPS_API_KEY` is available. For Google Cloud setup, create an API key, enable **Maps JavaScript API**, and apply API restrictions. The key is a public client key, but it still must be restricted and kept out of Git.

Selecting a cycle marker draws a FreeRoute cycling route from KUET Main Gate. Route requests use only starting and destination coordinates and do not send owner or renter details.

## Map data

When `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` are configured, the map reads the `map_cycle_locations` RPC. The migration deliberately returns only approved, available cycle locations and no owner contact details. The development fallback uses three local sample locations around KUET; it is never used when Supabase is configured.
