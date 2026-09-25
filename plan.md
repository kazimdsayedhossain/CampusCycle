# CampusCycle — Production Build Plan

## Product direction

CampusCycle is a KUET cycle-sharing platform for Khulna, Bangladesh. KUET students can list personal cycles for approval, rent approved cycles by time, return them after handover, ask for help, and raise payment disputes. Cycle Office administrators approve listings, answer support requests, and resolve disputes.

The product will be a JavaFX desktop application backed by Supabase Auth, PostgreSQL, Row Level Security, database RPCs, and audited admin actions. The desktop app is an untrusted client: it will contain only the Supabase URL and publishable key. Database passwords, service keys, payment credentials, and admin credentials will never be stored in the app, Git, FXML, or CSS.

## UX reference interpretation

The supplied reference images define the visual standard: a bright neutral canvas, generous whitespace, white panels with thin borders, strong black typography, compact metric cards, cobalt blue primary actions, sky-blue data highlights, restrained green success states, and red error or destructive states. Use a single horizontal top navigation bar; do not add a sidebar. The UI must use hand-authored SVG paths or JavaFX shapes for icons, never Unicode symbols, emoji, or keyboard-character icons. Every visible metric, control, table row, card, and action must come from real state or trigger a supported use case.

## Target architecture

```text
JavaFX + FXML desktop client
        │ HTTPS with authenticated user token
        ▼
Supabase Auth ──► PostgreSQL RPC functions
                         │
                         ├── RLS policies and constraints
                         ├── transaction locks and idempotency
                         ├── audit events
                         └── optional Edge Functions for payment/webhooks
```

## Map and route integration

Google Maps is a functional campus map, not a decorative mockup. The JavaFX map page will host the Google Maps JavaScript API in a `WebView`, start at KUET, show only repository-supplied approved and available cycle markers, fit the map to valid marker coordinates, let users select a marker to request a cycle route from KUET Main Gate through FreeRoute, and surface a useful configuration or network error when either provider cannot load. `GOOGLE_MAPS_API_KEY` and `FREEROUTE_API_KEY` are supplied only through the local environment or local `.env`; no key is committed to Git.

## 150 goals

### Foundation and delivery

1. Create a clean Maven Java 24+ JavaFX project.
2. Set Maven compiler source and target to a supported Java LTS release.
3. Add JavaFX Controls and JavaFX FXML dependencies.
4. Add JUnit 5 dependencies for domain and repository tests.
5. Configure a reproducible JavaFX Maven run command.
6. Add a project `.gitignore` for environment files, IDE files, and build output.
7. Add an `.env.example` containing only public Supabase configuration names.
8. Ensure `.env` files are ignored by Git.
9. Add a concise README with setup, test, and run instructions.
10. Document supported Java and Maven versions.
11. Add a conventional package structure for domain, application, data, infrastructure, config, and UI.
12. Keep FXML resources under the application resource package.
13. Keep all UI styles in a small, named CSS design system.
14. Use UTF-8 source and resource encoding.
15. Add a startup health check for missing public configuration.
16. Show configuration failures as safe user-facing messages.
17. Never print secrets, access tokens, SQL errors, or HTTP response bodies to the UI.
18. Define an application error model with safe messages and internal error codes.
19. Use a bounded executor for HTTP and database-adapter work.
20. Ensure all JavaFX node mutations run on the JavaFX application thread.
21. Set connection, request, and read timeouts for every remote call.
22. Add retry behavior only for safe idempotent read requests.
23. Cancel or ignore stale screen requests when users navigate away or sign out.
24. Add structured client logs that redact tokens and personal data.
25. Define release build, smoke test, and rollback procedures.

### Identity, roles, and session security

26. Configure Supabase Auth for KUET email registration.
27. Validate KUET email addresses before registration requests.
28. Require a minimum password length and delegate password security to Supabase Auth.
29. Require email verification before students can use protected workflows.
30. Create a profile row automatically when an Auth user is created.
31. Store only display name, role, and safe campus identity fields in profiles.
32. Default every new account to the student role.
33. Restrict admin role assignment to protected Supabase administration workflows.
34. Never offer an admin-selector control in the desktop login screen.
35. Sign users in through the Supabase password grant endpoint.
36. Load the user role from the protected profile record after sign-in.
37. Route students and administrators to role-appropriate navigation.
38. Store access tokens only in memory for the current desktop session.
39. Clear in-memory session state when users sign out.
40. Refresh expired sessions through a dedicated session service.
41. Handle expired sessions by returning users to the sign-in screen.
42. Prevent late responses from a prior session from updating a later session UI.
43. Limit profile queries to the signed-in user or scoped admin queues.
44. Add authentication tests for invalid credentials and unverified accounts.
45. Add authorization tests for student and administrator route separation.

### Domain model and business rules

46. Model CampusUser as an immutable domain record.
47. Model cycle type with city, road, electric, and cargo options.
48. Model cycle condition with excellent, good, and fair options.
49. Model cycle inspection state as pending, approved, rejected, or suspended.
50. Model cycle availability as available, rented, or maintenance.
51. Model rental state as active, returned, cancelled, disputed, or closed.
52. Model dispute state as open, under review, resolved, or rejected.
53. Model support conversations and immutable support messages.
54. Model rate cards with integer poisha amounts only.
55. Use Asia/Dhaka for all displayed business times.
56. Use server timestamps as the authoritative rental timeline.
57. Define the initial rate as BDT 20 for the first 15 minutes.
58. Define BDT 10 for each additional 15-minute block, rounded up.
59. Set a minimum rental period of 15 minutes.
60. Set a maximum rental period of 180 minutes for the pilot.
61. Store the rate-card version on every rental.
62. Store the server-calculated quoted amount on every rental.
63. Reject bookings of a user’s own cycle.
64. Reject booking a cycle that is not approved and available.
65. Restrict a renter to one active rental at a time.
66. Require the renter who opened a rental to return it.
67. Require a written reason for a rejected cycle listing.
68. Require a written resolution for every closed dispute.
69. Require support messages to be non-empty and bounded in length.
70. Add unit tests for fare calculation boundary values and invalid durations.

### PostgreSQL schema and database integrity

71. Create versioned Supabase migrations from an empty project.
72. Enable required PostgreSQL extensions through migrations.
73. Create the profiles table linked one-to-one with auth.users.
74. Create the cycles table with owner, descriptive fields, private phone, and lifecycle columns.
75. Create the rate_cards table with version and effective dates.
76. Create the rentals table with timeline, quote, state, and idempotency key.
77. Create payment_records with provider reference and immutable amount fields.
78. Create disputes with rental reference, state, resolution, and assigned administrator.
79. Create support_conversations with student, assigned administrator, and state.
80. Create support_messages with sender, body, and timestamp.
81. Create an append-only audit_events table.
82. Add foreign keys for every user, cycle, rental, dispute, and support relation.
83. Add check constraints for every lifecycle enum value.
84. Add check constraints for valid phone format and text lengths.
85. Add check constraints to prevent negative payment or fare amounts.
86. Add a unique index for rental idempotency keys.
87. Add indexes for public catalog filtering and admin review queues.
88. Add indexes for renter rental history and owner listing history.
89. Add indexes for open dispute and support queues.
90. Enable Row Level Security on every public table.
91. Revoke direct public table access from anonymous and authenticated roles.
92. Expose only narrow RPC functions to authenticated users.
93. Use `security definer` RPCs with an explicit safe search path.
94. Validate the authenticated role inside each administrator RPC.
95. Add audit events for every lifecycle-changing action.

### Transactions, APIs, and payment boundary

96. Create a public catalog RPC that omits private owner phone numbers.
97. Create an owner listing RPC that returns only the owner’s own cycles.
98. Create a register-cycle RPC with server-side field validation.
99. Create an admin review RPC for approve and reject decisions.
100. Create a rate-card RPC that returns the active card only.
101. Create a booking RPC with a supplied idempotency key.
102. Lock the cycle row inside the booking transaction.
103. Check approval, availability, ownership, and active rental conditions inside the booking transaction.
104. Calculate the booking quote inside PostgreSQL.
105. Create the rental and mark the cycle rented in one transaction.
106. Create an idempotent return RPC that verifies renter ownership.
107. Mark a returned cycle available only after a valid return transaction.
108. Create a renter rental-history RPC.
109. Create a renter dispute-open RPC for eligible returned rentals.
110. Create an admin dispute-queue RPC.
111. Create an admin dispute-resolution RPC.
112. Create a student support-conversation list RPC.
113. Create an admin support-inbox RPC.
114. Create a support-message history RPC with participant access checks.
115. Create a support-message post RPC with role and membership checks.
116. Keep payment records explicitly `UNPAID` until a payment provider is implemented.
117. Do not show a false paid, refunded, or successful payment state.
118. Isolate future payment webhooks in Supabase Edge Functions.
119. Verify payment-provider signatures before changing payment state.
120. Make provider event processing idempotent by unique event reference.

### Java application and repository layer

121. Create a typed CampusRepository interface for all supported use cases.
122. Implement an authenticated Supabase repository using HTTPS RPC calls.
123. Map every RPC response into immutable domain records.
124. Normalize HTTP and RPC failures into safe application errors.
125. Keep the Supabase service role key out of the Java repository.
126. Keep PostgreSQL connection credentials out of the Java project.
127. Create an in-memory repository for deterministic UI development and tests.
128. Seed the in-memory repository with safe KUET demonstration data.
129. Ensure the mock repository follows the same lifecycle rules as production.
130. Add repository tests for listing, booking, return, review, and dispute workflows.
131. Add contract tests for all expected RPC response fields.
132. Add integration tests for unauthorized direct access and role enforcement.
133. Add race-condition tests for two concurrent booking attempts.
134. Add idempotency tests for duplicate booking and return requests.
135. Add migration tests against a disposable Supabase project before release.

### FXML user interface and interaction design

136. Build a light app shell in FXML with a top brand bar, horizontal navigation, and campus-map route.
137. Use no sidebar, drawer, or hidden left navigation.
138. Build an FXML sign-in and registration page with a dark brand panel and clean white form surface.
139. Build an FXML dashboard with compact live metric cards and a real available-cycle preview.
140. Build an FXML cycle catalog with search, live result count, cycle cards, pickup information, map selection, and booking action.
141. Build an FXML owner listing page with inspection status and a real cycle-registration dialog.
142. Build an FXML rentals page with active return action, history, quote details, and dispute action.
143. Build an FXML support page with request creation, conversation list, message thread, and reply composer.
144. Build an FXML Cycle Office page with real inspection, dispute, and support queues.
145. Use compact white panels, 1px neutral borders, 8–12px radii, and generous margins like the reference images.
146. Use Aptos or Segoe UI Variable with a 25px page heading, 16–18px section heading, and 11–12px body type scale.
147. Use black and graphite text, cobalt blue actions, sky-blue accents, green success states, and red failure states.
148. Draw every icon as an SVG path or JavaFX vector shape; use no emoji or Unicode icons.
149. Animate page changes and hover states subtly, under 180ms, without blocking interaction or causing layout shifts.
150. Complete a manual accessibility, keyboard-navigation, map-loading, error-state, empty-state, responsive-window, and release smoke-test review before delivery.

## Completion standard

The project is ready for release only when all 150 goals are complete, every mutation is authorized by server-side role and state checks, migrations are tested on a clean Supabase project, the full JavaFX workflow builds and runs, and payment collection remains disabled until a Bangladesh payment provider, webhook verification, reconciliation, and refund policy are separately implemented.
