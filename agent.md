# CampusCycle: Project Knowledge Base & Codex Transition Guide

> **Target Platform**: Desktop JavaFX 21 (JDK 21+), Supabase PostgREST & Auth Backend  
> **Package**: `bd.ac.kuet.campuscycle`  
> **Workspace**: `E:\java\CampusCycle`  
> **Last Updated**: September 2026

---

## 📌 1. Project Overview & Evolution Timeline

CampusCycle is an official micro-mobility cycle sharing and rental platform designed specifically for the **Khulna University of Engineering & Technology (KUET)** campus. It connects students and campus members to securely rent cycles, list their own cycles under university supervision, and conduct clear handovers with verified inspection tracking.

### Evolution Timeline
1. **Initial Conception**: Basic client prototype with simple rental calculations.
2. **Database & Backend Hardening**:
   - Integrated Supabase with PostgreSQL.
   - Enforced strict Row-Level Security (RLS) policies and triggers so that clients cannot tamper with roles or bypass physical inspections.
   - Implemented published rate cards in minor units (BDT Paisa) with deterministic compound tariff calculations.
   - Created client RPC functions (`request_rental`, `return_rental`, `approve_cycle`, `reject_cycle`, `file_dispute`, `get_admin_review_queue`).
3. **Architecture Decoupling**:
   - Built a repository layer (`CampusRepository`) with two implementations:
     - `SupabaseCampusRepository`: Live authenticated network calls via PostgREST and Supabase Auth.
     - `InMemoryCampusRepository`: Zero-network offline mock environment with preloaded KUET student, admin, cycles, and rate card data.
4. **Ground-Up UI Overhaul (UI_REFERENCE Benchmarking)**:
   - Purged all legacy and generic AI UI files.
   - Eliminated the sidebar entirely in favor of a spatial **Top Bar Navigation** inspired by **Forselle** and **Fintory**.
   - Built a full **Dual-Theme Engine** supporting **Dark Mode** (`#0A0F1D` + Electric Blue) and **Light Mode** (`#F4F6F9` + Royal Blue `#0284C7`), switchable on the fly with zero reload delay.
   - Implemented a **Live Interactive KUET Campus Map** with a movable "You Are Here" user location pin, real-time Euclidean distance calculations to all 5 hubs, nearest-hub detection, and dashed navigation guides.
   - Redesigned the **Login Page** with modern pill inputs, embedded leading SVG icons, and interactive password visibility eye toggle.
   - Added a dedicated **Settings Menu** with theme switcher, default hub preferences, and notification toggles.

---

## 🗄️ 2. Database Schema & SQL Architecture

All database migrations reside in `supabase/migrations/`.

### Core Tables
1. **`public.profiles`**:
   - `id uuid primary key references auth.users(id)`
   - `display_name text not null`
   - `role text not null check (role in ('STUDENT', 'ADMIN')) default 'STUDENT'`
   - **Trigger**: `on_auth_user_created` fires `create_profile_for_auth_user()` on `auth.users` insert.
2. **`public.cycles`**:
   - `id uuid primary key default gen_random_uuid()`
   - `owner_id uuid not null references public.profiles(id)`
   - `label text not null`
   - `cycle_type text not null check (cycle_type in ('CITY_BIKE', 'ROAD_BIKE', 'ELECTRIC_BIKE', 'CARGO_BIKE'))`
   - `description text not null default ''`
   - `physical_condition text not null check (physical_condition in ('EXCELLENT', 'GOOD', 'FAIR'))`
   - `owner_phone text not null`
   - `review_status text not null check (review_status in ('PENDING_REVIEW', 'APPROVED', 'REJECTED', 'SUSPENDED'))`
   - `availability_status text not null check (availability_status in ('AVAILABLE', 'RENTED', 'MAINTENANCE'))`
3. **`public.rate_cards`**:
   - `version integer not null unique`
   - `base_minutes smallint not null default 15`
   - `base_charge_poisha integer not null default 2000` (BDT 20.00)
   - `extra_block_minutes smallint not null default 15`
   - `extra_block_charge_poisha integer not null default 1000` (BDT 10.00)
   - `maximum_minutes smallint not null default 180`
4. **`public.rentals`**:
   - `id uuid primary key default gen_random_uuid()`
   - `cycle_id uuid references public.cycles(id)`
   - `renter_id uuid references public.profiles(id)`
   - `state text not null check (state in ('ACTIVE', 'RETURNED', 'CANCELLED', 'DISPUTED', 'CLOSED'))`
   - `requested_minutes integer not null`
   - `started_at timestamptz not null default now()`
   - `returned_at timestamptz`
   - `idempotency_key uuid not null`

---

## 🖥️ 3. UI Component Architecture (`bd.ac.kuet.campuscycle.ui`)

* **`BingMapView.java` & `bing-map.html`**: High-resolution interactive **Leaflet + Bing Satellite & Street Map** in `WebView` with floating HUD (`LAT: 22.9009° N, LNG: 89.5016° E`), live draggable user pin, Euclidean walking time math, 5 campus quad-docks, and extended **Khulna city-wide roaming checkpoints** (Fulbarigate, Daulatpur, Khalishpur, Boyra, Sonadanga, Shibbari, Royal Mor).
* **`DatabaseConnection.java`**: Supabase PostgreSQL JDBC connection manager (`aws-0-ap-northeast-1.pooler.supabase.com:5432/postgres`) with 4s timeouts and non-blocking health checks.
* **`SupabaseCampusRepository.java`**: Live database operations with `SELECT ... FOR UPDATE` atomic transactions, double-booking prevention, and automatic fallback to `InMemoryCampusRepository`.
* **`ThemeManager.java`**: Central theme engine managing `DARK` and `LIGHT` mode states, reactive listeners, and SVG vector paths.
* **`theme-dark.css` & `theme-light.css`**: Obsidian dark (`#0A0F1D`) and clean slate-light (`#F4F6F9`) stylesheets located in `src/main/resources/bd/ac/kuet/campuscycle/`.
* **`AppHeader.java`**: Spatial top navigation bar with brand island, centered navigation pill capsule (`Dashboard`, `Fleet Catalog`, `Campus Map`, `Active Journey`, `Passbook`, `Admin Operations`), location chip, theme toggle button, settings button, and user profile chip.
* **`CampusMapView.java`**: Dedicated split-screen navigation radar implementing the Wheat Field UI reference with interactive station directory cards on the left and live Microsoft Bing Maps on the right.
* **`DashboardView.java`**: Bento telemetry overview (Available fleet, Online hubs, Subsidy tier, Carbon emissions) with live Bing Map and quick reservation cards.
* **`FleetCatalogView.java`**: Vehicle discovery with search input, category chips (`All Models`, `City Commuters`, `Road Racers`, `Electric Assisted`, `Cargo Utility`), `Available Only` checkbox, collapsible map toggle, and cycle cards.
* **`ReservationModal.java`**: Tactile 15–180 min duration slider with live return time, route dropdowns, 25% student subsidy calculation, and unlock trigger.
* **`ActiveJourneyView.java`**: Active ride cockpit with live ticking clock, speed & odometer widget, real-time accrued fare in BDT, drop-off hub selector, and two sensor check confirmations.
* **`PassbookView.java`**: Mobility logs list, summary telemetry, and university transit receipt with PDF export trigger.
* **`AdminOperationsView.java`**: Cycle Office command center with KPI cards, hub rebalancer, and hardware inventory inspection table with one-click approval/rejection.
* **`SettingsModal.java`**: Settings dialog with theme switcher, hub preference, notification toggles, and account sign-out.
* **`LoginView.java`**: Split-hero login screen with rounded pill text inputs, embedded SVG icons, and interactive password visibility eye toggle.

---

## 🛠️ 4. Build, Test & Run Commands

```powershell
# In E:\java\CampusCycle:

# 1. Compile all source files:
.\mvnw.cmd clean test-compile

# 2. Run unit tests:
.\mvnw.cmd test

# 3. Launch desktop application:
.\mvnw.cmd javafx:run
```
