# Narrative News — API (backend)

The server for the Narrative News newspaper site: admins upload the day's PDF and write posts; readers flip the paper page by page and like / comment / share posts. The website lives in the separate repo [Narritive-tool-frontend](https://github.com/samir281/Narritive-tool-frontend).

**Stack (all free):** Java 21 + Spring Boot 3 (Web, Data JPA, Security, JWT), Apache PDFBox for PDF → images, Tesseract (Tess4J) for Hindi/English page text, PostgreSQL (H2 file DB for local).

## Design notes

| Topic | Choice | Reason |
|---|---|---|
| Image storage | In the database (`media` table, `bytea`), served with long-lived cache headers | No S3 / paid services |
| Background work | A single-thread `@Async` executor renders PDFs and reads page text | Free hosting has little RAM; one instance is enough |
| PDF → images | Scanned pages: the embedded JPEG is the zoom image, decoded at reduced size for the 1300 px view and 480 px thumbnail. Other pages: PDFBox render at 2600 px | Original scan quality, low memory |
| Original PDF | Not stored | Saves database space; re-upload to re-render |
| Page text (OCR) | Tesseract, Hindi + English, after the pages are live | Powers the reader's Text view and tap-a-story |
| Reader login | Email + password, one JWT (admin 8 h, reader 30 days) | OTP SMS is paid |
| Posts | Plain text (line breaks kept) | No XSS risk |

## Run locally

Needs JDK 21 and Maven.

```bash
mvn spring-boot:run        # uses a local H2 file database in ./data
```
API on http://localhost:8080 (health: `/actuator/health`). Run the website from the frontend repo with `npm run dev`; it proxies `/api` here. First admin login: `admin@news.local` / `ChangeMe123!` (override with `ADMIN_EMAIL` / `ADMIN_PASSWORD`; only used when the database has no admin yet).

**Page text (OCR):** put `hin.traineddata` and `eng.traineddata` from https://github.com/tesseract-ocr/tessdata into `./tessdata/` (or set `TESSDATA_PATH`). On Windows the Tesseract engine ships inside the Tess4J jar; the Docker image installs it. Text is read in the background after each upload (~20 s per page). Without the files the site works normally. `OCR_ENABLED=false` turns it off.

## Deploy

All configuration is environment variables (see `.env.example`). The `Dockerfile` builds and runs the jar.

**Render (free):** `render.yaml` is a ready Blueprint — open
https://render.com/deploy?repo=https://github.com/samir281/Narritive-tool-Backend and enter the database password when asked.
The free instance has 512 MB RAM and 0.1 CPU, sleeps after 15 idle minutes (the website pings it every 10 minutes to keep it awake), and OCR is switched off there because it needs ~600 MB. On a host with 1 GB+ set `OCR_ENABLED=true` and raise `-Xmx` in `JAVA_OPTS`.

**Storage planning:** a scanned 8-page edition is about 15 MB in the database, so a 500 MB free database holds roughly 30 editions. Delete old editions in the admin to free space.

## API summary
Public: `GET /api/public/editions/latest`, `/editions/{date}`, `/editions/{date}/pages/{n}/text`, `/editions?month=`, `/posts/{id}/comments`, `/media/{id}`.
Reader: `POST /api/auth/register|login`, `GET/PUT/DELETE /api/me/likes…`, `POST /api/posts/{id}/comments`, `DELETE /api/me/comments/{id}`.
Admin (`/api/admin/**`): login, editions CRUD + `pdf` upload + publish/unpublish, posts CRUD + order + image, comment hide/restore, admins list/add/disable.
Errors are `{ "error": { "code", "message" } }`. Browsers may call the API from the origins in `CORS_ORIGINS`.

## Known limits
- Story areas are Tesseract's text blocks joined per column, not true article boundaries. No search yet.
- In-memory rate limiting (fine for one instance).
- Schema is created by Hibernate (`ddl-auto: update`); switch to Flyway before the schema starts changing in production.
