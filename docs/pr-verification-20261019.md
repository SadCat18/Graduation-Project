# PR candidate verification — 2026-10-19

Scope: current local implementation of the admin operations dashboard, paginated private messaging/profile UI, deterministic board assistant with reference estimates and saved plans, deployment configuration and the site name 滑板知识网站. This snapshot includes local changes beyond the deployed release; it is not a deployment claim.

## Current checks

- Default Maven: 375 tests, 56 suites, no failures/errors/skips, isolated candidate, no production services.
- Vitest: 68 tests across 11 files passed; Vue production build passed.
- Frontend contract scripts: 39 passed after making the existing activity-expiry harness CRLF-independent.
- Mock-API browser suite: 23 passed on installed Edge; does not prove server authorization or database behavior.
- Real HTTP/H2 integration: reference acceptance passed with its one browser flow; exact T07 passed with main and explicitly confirmed linked-adjustment browser flows. Captcha storage and old-schema prerequisites are test substitutes. These are local fixtures, not production writes.
- Original/reference Python validation controls passed; source/protected hashes retained. Reference catalog is the original 50/114/60/39/3 dataset, 21 recorded domestic reference prices, zero verified exact domestic prices. Unknown prices remain null, partial assembly uses known subtotal; the cheapest plan describes the bounded search scope only.
- **Draft PR unresolved security gate:** production dependency audit reports six high and one moderate advisory findings. ECharts requires a major-version upgrade; the coordinator explicitly directed submitting the current code as a Draft PR without changing dependencies or the audit threshold. Existing CI audit is expected to fail until remediation. This report does not claim the security/CI gate passes.

## Reproduce from a clean checkout

Use JDK17, Python3 and Node20+ with npm. From the repository root:

```sh
./mvnw -s .mvn/settings.xml -B test
python -X utf8 -B docs/equipment-data/test_validate_normalized.py
python -X utf8 -B docs/equipment-data/test_reference_candidate.py
python -X utf8 -B docs/equipment-data/validate_normalized.py docs/equipment-data/normalized-equipment.reference.v1.json --previous docs/equipment-data/normalized-equipment.v1.json
cd frontend
npm ci
npm audit --omit=dev --registry=https://registry.npmjs.org
npm run test:unit
npm run build
npx playwright install chromium
npm run test:e2e -- --workers=1
```

Run `node frontend/tests/<name>.test.cjs` for each frontend contract script. The mock suite uses committed fixtures under frontend/tests/fixtures/board and writes ignored outputs; it does not depend on local agent evidence. Java evidence outputs are under target/test-evidence/board.

Browser-enhanced Java/H2 checks are explicit, after building frontend/dist and installing Chromium:

```sh
./mvnw -s .mvn/settings.xml -B '-Dtest=BoardT07HttpIntegrationTest,BoardReferenceAcceptanceHttpTest' '-Dboard.browser-tests=true' test
```

Ports8080/4173 must be free. Optionally set PLAYWRIGHT_CHANNEL=msedge when using installed Edge. Default Maven still executes the HTTP assertions; it does not launch browsers. Default Playwright excludes the two tests owned by the Java/H2 harness.

## Operational scope

Default Compose uses source Dockerfiles so a clean checkout can build. Prebuilt Dockerfiles remain available for explicit prepared releases. A .env copied from the example needs real local secrets; never commit it. Compose includes local MySQL dependencies; deployments against external CDB need an explicit reviewed no-deps workflow and backup. This PR work neither deploys nor migrates a database.

Historical production evidence: reference functionality/import and anonymous acceptance were completed on2026-10-10; the isolated five-string site rename was released on2026-10-16. Those results apply to their exact deployed baselines, not all later local changes. No current production write, paid provider call, TLS change or full load/limit validation is claimed here. The unused hero SVG remains outside the four-source rename and retains its old drawn text.
