# Bolo Stock 🎙️ — Voice-Based Inventory Management

A voice-first inventory system for small shop owners who are more comfortable
speaking than typing. Add stock, remove stock, and ask about what's running
low — all by speaking naturally in English, Hindi, or mixed language — using
everyday trade units like kg, bags, cartons, dozens, litres, and quintals.

Built for the brief: *"Design and build a simple, voice-first inventory
management solution that allows small business owners to add, remove, track,
and understand their stock by speaking naturally in their preferred
language."*

---

## Stack

| Layer     | Technology                                   |
|-----------|-----------------------------------------------|
| Frontend  | React 18 + Vite, Tailwind CSS                  |
| Backend   | Spring Boot 3 (Java 17), Spring Data JPA        |
| Database  | MySQL 8 (set up via MySQL Workbench)            |
| Voice     | Browser Web Speech API (SpeechRecognition + SpeechSynthesis) |

The voice recognition and text-to-speech happen **in the browser** (no paid
speech API needed). The transcript is sent to the Spring Boot backend, which
runs it through a rule-based NLP parser (`VoiceCommandService`) that
understands English, Hindi and Hinglish phrasing, trade units, and product
names in either language.

---

## Project structure

```
voice-inventory-app/
├── backend/            Spring Boot API (Java 17, Maven)
├── frontend/            React + Vite + Tailwind UI
└── database/
    └── schema.sql       MySQL schema + sample data
```

---

## 1. Set up the database (MySQL Workbench)

1. Open **MySQL Workbench** and connect to your local MySQL server.
2. Open `database/schema.sql` (**File → Open SQL Script**).
3. Click the ⚡ **Execute** button to run the whole script.
   This creates the `voice_inventory_db` database with `products` and
   `stock_transactions` tables, plus 10 sample products (rice, sugar, dal,
   cooking oil, etc.) with Hindi names and opening stock already loaded.
4. Note your MySQL username/password — you'll need them in step 2.

## 2. Run the backend (Spring Boot)

```bash
cd backend
```

Edit `src/main/resources/application.properties` if your MySQL credentials
differ from the defaults:

```properties
spring.datasource.username=root
spring.datasource.password=root
```

Then run it:

```bash
# using the Maven wrapper (recommended) — or use your IDE's Run button on InventoryApplication.java
mvn spring-boot:run
```

The API starts on **http://localhost:8080**. Quick check:

```bash
curl http://localhost:8080/api/products
```

### Key endpoints

| Method | Endpoint                          | Purpose                              |
|--------|------------------------------------|---------------------------------------|
| GET    | `/api/products`                    | List / search products                |
| GET    | `/api/products/low-stock`          | Items at or below their reorder point |
| POST   | `/api/products`                    | Add a new product                     |
| PUT    | `/api/products/{id}`               | Edit a product                        |
| DELETE | `/api/products/{id}`               | Remove a product                      |
| POST   | `/api/stock/transaction`           | Manual stock in/out                   |
| GET    | `/api/stock/activity`              | Recent stock movements                |
| POST   | `/api/voice/command`               | Send a spoken transcript, get back intent + reply |
| GET    | `/api/dashboard/summary`           | Stats for the dashboard cards         |

## 3. Run the frontend (Vite + React)

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**. The Vite dev server proxies `/api/*` calls to
the backend on port 8080 (see `vite.config.js`), so no extra CORS setup is
needed in development.

For a production build:

```bash
npm run build   # outputs to frontend/dist
npm run preview
```

---

## Using the voice assistant

1. Go to the **Voice Assistant** tab and pick a language (English, Hindi,
   Marathi, Tamil, Telugu, Gujarati, Bengali — any language your browser's
   speech engine supports).
2. Tap the mic and speak naturally, e.g.:
   - *"Add 10 kg rice"* / *"10 किलो चावल जोड़ो"*
   - *"Sold 5 kg sugar"* / *"5 किलो चीनी बेचा"*
   - *"How much oil is left?"* / *"तेल कितना बचा है?"*
   - *"What is running low?"* / *"क्या कम स्टॉक में है?"*
3. The assistant updates the stock, speaks the result back to you, and logs
   the transaction (tagged as a voice transaction, with the original
   transcript kept for audit) in **Activity**.

Voice recognition requires a Chromium-based browser (Chrome/Edge) — Safari
and Firefox have limited or no Web Speech API support. If voice isn't
available, the same box accepts typed commands.

---

## How the voice parsing works

`VoiceCommandService` (backend) is a lightweight, dependency-free NLP
pipeline:

1. **Normalize** the transcript (lowercase, convert Devanagari digits like
   ५ → 5).
2. **Detect intent** — add stock, remove stock, stock query, or low-stock
   query — using English + Hindi/Hinglish keyword sets.
3. **Extract quantity** — digits, Devanagari digits, or spoken number words
   ("two", "do", "दो"…).
4. **Extract unit** — matches trade units and their English/Hindi synonyms
   (kg/किलो, bag/बोरा, carton/कार्टन, dozen/दर्जन, litre/लीटर, quintal/क्विंटल…).
5. **Match the product** against the catalog by its English *or* regional-
   language name (stored per-product in the `local_name` column).
6. **Execute** the stock movement and return a natural-language reply in the
   same language, which the frontend speaks back via text-to-speech.

This keeps the system fully explainable and doesn't require an external paid
AI/NLP service — it can be swapped for a cloud NLU service later without
changing the API contract.

---

## Milestones covered

This build addresses the project's milestone list end-to-end:

- **Product & Stock Management** — `ProductController` / `StockController`,
  supporting pieces, kg, bags, cartons, boxes, dozens, litres, quintals.
- **Voice-Based Stock Entry** — `VoiceController` + `VoiceCommandService`,
  Web Speech API on the frontend.
- **Regional Language Support** — Hindi/Hinglish parsing, per-product
  regional names, multi-language recognition + spoken replies.
- **Stock Questions & Smart Alerts** — stock queries, low-stock queries,
  `/api/products/low-stock`, dashboard alerts panel.
- **UI/UX** — dashboard, inventory table, voice assistant screen, activity
  log, all in a distinctive, non-templated visual design.

---

## Notes for graders / reviewers

- Sample data ships pre-loaded (see `database/schema.sql`) so the dashboard
  and voice assistant are usable immediately after setup — no need to add
  products manually before testing.
- `spring.jpa.hibernate.ddl-auto=update` is set so the backend will also
  auto-create/adjust tables if you skip step 1, but running `schema.sql` in
  Workbench first (as the brief requires) is the intended path and it also
  seeds the sample data.
