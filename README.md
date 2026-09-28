# CampusCycle

A cycle sharing app for KUET. Students find a cycle, unlock it, ride, and lock it back
at any campus hub. Bike owners list their cycle and earn from every ride.

JavaFX for the desktop app. Supabase for the database and login.

**Kazi MD. Sayed Hossain**
Department of CSE, KUET
Roll: 2307050

Academic project for **Advanced Programming**, KUET.

---

## What it does

- **Student** — browse cycles, book one for 15 to 180 minutes, watch the fare tick up,
  return it, pay from the wallet or in cash at the hub, and export the passbook to CSV.
- **Owner** — list a cycle and see earnings for it: rides, total fare, platform fee, payout.
- **Technician** — work the maintenance queue and decide whether a cycle goes back in
  service or gets retired.
- **Admin** — approve students and cycle listings, watch revenue, handle disputes and
  support messages.

## How the fare works

A database trigger works the fare out from the real elapsed time. The client cannot
change it.

```
First 15 minutes      20 BDT
Each extra 15 min     10 BDT
Student discount      25% off
Late fine             5 BDT per 15 min, after a 15 min grace
Platform fee          5% of the fare, max 1.50 BDT
Owner gets            fare minus the platform fee
```

The platform keeps the fare and pays the owner when the cycle comes back. All of it
happens in one database transaction, so either the whole ride settles or none of it does.

Two rules matter most:

- The owner is never paid money the platform has not actually received.
- Money that could not be collected is never lost. It becomes a due on the rider's
  account, blocks the next booking, and clears itself on the next top up.

## Topics used

These are the course topics, and what the project used them for.

| Topic | How it was used |
|---|---|
| **Version control** | Git and GitHub, with regular commits from the first submission on 6 September |
| **Advanced OOP** | Interfaces, abstract classes, enums, records, inheritance and polymorphism across the domain and data layers |
| **JavaFX UI design** | BorderPane, StackPane, VBox, HBox, GridPane, ScrollPane, TableView, TextField, PasswordField and custom styled dialogs |
| **Layout responsiveness** | Layout panes, bindings and percentage sizing so the UI adapts to window size |
| **Concurrency** | Thread pools and multi-threading for database work, map tiles, weather and routing, kept off the UI thread |
| **Database integration** | Supabase PostgreSQL over JDBC, with schema, tables, keys, triggers and a security definer role |
| **Data manipulation** | Full CRUD on users, cycles, rentals, maintenance tickets, disputes, support threads and wallets |
| **Networking and data parsing** | HTTP calls to Supabase Auth, Bing map tiles and FreeRoute, with the JSON responses parsed into objects |

Extra things used on top of the list: SQL stored functions and triggers for fare
authority, PBKDF2 password hashing, a JWT session store, CSV export, a custom animation
system, a generated brand identity, and JUnit 5 tests that run against both an in-memory
double and the real database.

## Running it

Needs JDK 21, Maven and a Supabase project.

1. Apply the SQL files in `supabase/migrations/` in order, using the Supabase SQL editor.
2. Copy `.env.example` to `.env` and fill it in. You need the project URL, publishable
   key, service role key and the pooled database connection string.
3. Run it:

```
mvn test
mvn javafx:run
```

The service role key is needed so the app can create a login when somebody registers.
Keep it in `.env` and never commit it.

## Layout

```
src/main/java/.../campuscycle/
   domain/     entities, enums, fare rules, password hashing
   data/       the repository interface, plus Supabase and in-memory versions
   service/    wallet, maintenance, reports, notifications, weather
   ui/         the JavaFX screens, modals, theming and artwork
   config/     reads .env
src/main/resources/   stylesheets, map page, images, logo
src/test/java/        tests and a couple of small dev tools
supabase/migrations/  the database schema
```

`CampusRepository` is the interface. The Supabase version is the real one. The in-memory
version is a working copy of the rules so they can be tested without a database.

## Tests

```
mvn test
```

68 tests. Most run on their own. The rest talk to the real database and check the parts
that would be expensive to get wrong: the fare always adds up, an owner is never
overpaid, unpaid money becomes a due and clears on top up, a late fine cannot be dodged
or invented by editing the client, and a student cannot sign in before an admin approves
them.

## Not finished

Being straight about it:

- bKash withdrawal is not connected. The screen takes the details and stops there.
- The platform fee is recorded and totalled, but there is no separate account it can be
  spent from.
- Owners have one wallet like everyone else, so riding money and earnings sit together.
- No email is sent when a registration is approved or rejected.
- Per user database permissions are not set up, so the app checks who owns what instead.

## Licence

Coursework for Advanced Programming at KUET. Shared for academic review.
