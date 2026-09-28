# CampusCycle

A cycle-sharing desktop application for Khulna University of Engineering & Technology
(KUET). Students find a cycle on campus, unlock it, ride, and lock it back at any hub.
Bike owners list a cycle and earn from every ride it completes.

Built with JavaFX and Supabase (PostgreSQL + GoTrue Auth), as an academic project for
the **Advanced Programming** course.

**Author:** Kazi MD. Sayed Hossain
Department of Computer Science and Engineering, KUET
Student roll: 2307050

---

## What it does

Three roles, one application.

**Student** browses the fleet and the campus map, reserves a cycle for 15 to 180
minutes, watches a live ride timer and fare counter, returns the cycle at any hub, and
settles the fare from a wallet. Rides are paid two ways: Campus Pay, where the wallet is
debited, or cash at the hub, where the attendant confirms what was collected. The passbook
keeps the ride history and exports it to CSV.

**Cycle owner** sees earnings per bike, broken down into rides, gross fare, platform fee
and payout, and can request a bKash withdrawal. Withdrawals are not wired up to bKash yet.

**Technician** works a maintenance queue: claim a ticket, move it through triage and
repair, resolve it, and put the cycle back in service or retire it.

**Admin** reviews cycle listings and student registrations, monitors fleet and platform
revenue, handles ride disputes, and reads support conversations.

## How a ride is billed

This is the part of the system that took the most iteration, so it is worth stating
plainly.

The fare is worked out by a database trigger from the real elapsed time, never from a
number the client sends:

```
base            20.00 BDT for the first 15 minutes
each extra 15   10.00 BDT
student subsidy 25% off
overdue fine    5.00 BDT per 15 minutes after a 15-minute grace window, uncapped
platform fee    5% of the settled fare, capped at 1.50 BDT
owner payout    settled fare minus the platform fee
```

The platform holds the fare and pays the owner when the cycle comes back. Everything
happens in one database transaction:

1. Lock the rental, confirm the renter owns it and the ride is still active.
2. Settle the ride. The trigger computes the real final fare and the fee split.
3. Collect the money. Campus Pay drains the wallet, overtime first and the fine last, each
   capped by the balance actually held. Cash-at-hub takes the amount the attendant
   confirmed.
4. Credit the owner `min(settled payout, collected)`.
5. Book anything still missing as a debt, in that same transaction.
6. Commit, or roll the whole thing back.

Two rules follow from this and are worth knowing:

- The owner is never paid money the platform has not received. A cash-at-hub ride that
  collected 1125.00 pays the owner 1125.00, not the full settlement, and the remainder
  becomes a debt rather than disappearing.
- Uncollected money is never forgiven. It lands in `rental_dues`, blocks the next Campus
  Pay booking, and clears itself automatically the next time money appears in the wallet.
  Three open dues puts the account under review.

Fares, fines and the fee split are all recomputed server-side from the stored timestamps.
The client can under-claim or over-claim and it makes no difference to what is charged.

## Tech stack

- Java 21, JavaFX 21, Maven
- Supabase: PostgreSQL over JDBC, GoTrue Auth, RLS
- Gson for JSON, JUnit 5 for tests
- No ORM. SQL is written by hand because most of the interesting logic has to live inside
  a transaction.

## Running it

Requirements: JDK 21 or newer, Maven, and a Supabase project.

**1. Create the database schema.** Apply the migrations in `supabase/migrations/` in
filename order through the Supabase SQL editor. The last four add the platform fee split,
a wallet ledger constraint, ride dues, and the payment method. Each is written to be safe
to re-run.

**2. Configure the app.** Copy `.env.example` to `.env` and fill it in. The short version:
project URL, publishable key, service-role key, and the pooled database connection
string. The service-role key is used to create a Supabase Auth identity when a student
registers and when an admin creates a staff account, so the app cannot run without it.

**3. Create the application database role.** `202609270003_app_role_and_policies.sql`
creates a least-privilege `campuscycle_app` role. Give it a password and put that in
`.env` instead of the `postgres` superuser. `anon` and `authenticated` stay revoked; the
app talks to the database as this role.

**4. Build and run.**

```
mvn test
mvn javafx:run
```

On Windows there is a `mvnw.cmd` wrapper if you would rather not have Maven on your PATH.

## Project layout

```
src/main/java/bd/ac/kuet/campuscycle/
  CampusCycleApplication   application shell, routing, theme
  config/                  environment loading
  domain/                  entities, enums, tariff rules, password hashing
  data/                    repository interface and the Supabase / in-memory implementations
  service/                 wallet, maintenance, notifications, reports, weather
  ui/                      JavaFX views, modals, theming, brand artwork
src/main/resources/        stylesheets, map HTML, images, brand assets
src/test/java/             unit tests, live integration tests, dev tools
supabase/migrations/       schema, applied in order
```

`CampusRepository` is the seam. `SupabaseCampusRepository` is the real implementation;
`InMemoryCampusRepository` is a working double so the lifecycle, money and access rules
can be tested without a database. The tests run against the double, and separately
against the live database, so the two cannot quietly disagree.

## Tests

```
mvn test
```

65 tests. Most run offline. The rest are integration tests that talk to the real database
and cover the parts where a mistake is expensive:

- the fare and fee split always adds up to the settled fare
- an owner is never paid more than was collected
- uncollected money becomes a due, blocks the next booking, and clears on top-up
- an overdue fine is billed server-side, so a modified client cannot dodge or invent one
- a registration can be approved, and an unapproved applicant cannot sign in
- a waived due releases the rider

Integration tests clean up after themselves. They create real rows and real auth users,
and delete them again afterwards.

## Development tools

Two small programs under `src/test/java/.../tools/`, run by hand rather than by the test
suite:

- `LogoAssetGenerator` renders the window and taskbar icons from the same code that draws
  the logo in the app, so the bitmaps cannot drift from it.
- `ViewSnapshotter` writes a PNG of the login screen, the sidebar lockup or the splash, for
  checking layout and branding without clicking through the app.

Both need a JavaFX toolkit, so they are `main()` methods, not tests. Instructions are in
their javadoc.

## Known limitations

Stated plainly, because a project that hides these is harder to review.

- **bKash withdrawal is not connected.** The interface validates the amount and number and
  then stops at "coming soon". No money moves and no ledger row is written.
- **The platform fee is accounting only.** It is recorded per ride and totalled for admin
  reporting, but there is no platform treasury wallet, so it is not a spendable balance.
- **One wallet per user.** Owners are users, so a ride and an owner's earnings share the
  same balance. There is no separate earnings account and no segregation of customer funds
  from owner payouts.
- **The map needs an internet connection** and Bing tiles. There is no offline map.
- **Email is not sent.** Registration approval and rejection are recorded and shown in the
  app, but no message is actually delivered to the student.
- **Row-level security is coarse.** Tables are locked to the application role and the
  `anon`/`authenticated` roles are revoked, but per-user policies on individual reads are
  not in place. The application enforces ownership in every repository method instead, so
  correctness depends on the application layer, not the database.

## Security notes

- Passwords are stored as PBKDF2-HMAC-SHA256 with a per-user salt, 600,000 iterations.
  Legacy `sha256:` hashes from the earlier schema are verified but no longer created.
- Supabase Auth owns identity. The application never writes to `auth.users` directly, and
  there is no role inference from the login string. Roles come from `public.profiles`.
- Every credential failure, unknown account and unapproved account return the same generic
  message, so the sign-in screen cannot be used to discover which accounts exist.
- `.env` is gitignored. The service-role key bypasses row-level security, so it is treated
  as a server secret and should be rotated if it is ever exposed.

## Licence

Written as coursework for the Advanced Programming course at KUET. The code is provided
for academic reference and review.
