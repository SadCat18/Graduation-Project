# PR candidate verification — 2026-10-19

Scope: current local implementation of the admin operations dashboard, paginated private messaging/profile UI, deterministic board assistant with reference estimates and saved plans, deployment configuration and the site name 滑板知识网站. This snapshot includes local changes beyond the deployed release; it is not a deployment claim.

## Baseline implementation checks (before dependency remediation)

- Default Maven: 375 tests, 56 suites, no failures/errors/skips, isolated candidate, no production services.
- Vitest: 68 tests across 11 files passed; Vue production build passed.
- Frontend contract scripts: 39 passed after making the existing activity-expiry harness CRLF-independent.
- Mock-API browser suite: 23 passed on installed Edge; does not prove server authorization or database behavior.
- Real HTTP/H2 integration: reference acceptance passed with its one browser flow; exact T07 passed with main and explicitly confirmed linked-adjustment browser flows. Captcha storage and old-schema prerequisites are test substitutes. These are local fixtures, not production writes.
- Original/reference Python validation controls passed; source/protected hashes retained. Reference catalog is the original 50/114/60/39/3 dataset, 21 recorded domestic reference prices, zero verified exact domestic prices. Unknown prices remain null, partial assembly uses known subtotal; the cheapest plan describes the bounded search scope only.
## Production dependency security repair — 2026-10-19

- Vue/compiler/runtime/SSR family3.5.44, Axios1.20.0, ECharts/zrender6.1.0; bounded lock updates for nanoid3.3.20, production PostCSS8.5.31 and source-map-js1.2.2. Eighteen version nodes changed, no overrides or forced broad toolchain upgrade. npm10 also normalizes two official tarball URLs and optional Rollup libc metadata.
- Three ECharts consumers use richText canvas tooltips rather than HTML; newline formatters, hover, legends, 7/30-day switching and resize remain covered. A mock browser case uses a hostile img/onerror name and asserts no HTML image node, script marker or pageerror.
- Official Node20.20.2/npm10.8.2, clean independent npm ci passed; installed production audit **0 vulnerabilities / exit0** using the existing CI command. The release reviewer independently reran the installed Node20 audit and chart test successfully.
- Current Node20 local checks:68 Vitest tests,39 contract scripts,production build45511e914cffdcc0,19 original core mock browser tests and1 added chart interaction test passed. Earlier baseline23 mocks and375 Maven tests above are historical baseline results, not reruns against this dependency repair. Remote Linux/Chromium/Java17/Docker results must be checked for the new PR head before claiming CI pass.
- Complete dependency audit still reports **44 development-toolchain findings (5critical/22high/17moderate)**. They are outside the production-only audit gate and remain open risks; no CI threshold/suppression was changed. The old production6high/1moderate failure was reproduced then repaired, not waived.
- The root shared workspace, its dependencies/index and all other632 PR source files remain unchanged. This is a PR-only fix, no merge/deployment/database/provider action.


## Deterministic mock avatars and remote CI follow-up

The dependency-repair head de5710e completed remote CI run39213129800: backend passed; frontend npm ci/production audit/unit/build/contracts passed; E2E21passed/3failed; Docker skipped. The failing message/profile visual specs still fetched external DiceBear avatars. CI logs reported connection refusal without a request URL; a separate controlled local refusal reproduced the same assertion, not a direct CI network trace.

The two mock visual specs now use64x64 inline SVG data URIs (eight fixture values). Existing strict console/business assertions, production avatar code, CI thresholds and test selection are unchanged. Node20 default browser suite24/24 passed locally; all four affected visual cases passed while refusing the external avatar domain, with zero avatar requests, valid image decoding and zero console/pageerrors. The independent reviewer repeated this four-case control successfully.

Production audit remains0; complete development-toolchain audit remains44 as disclosed above. A new exact-head remote frontend/backend/docker-build run must complete successfully before marking the PR ready. The prior failed run remains historical evidence and is not reported as green.

## Confirmed CI footer-image isolation

The avatar-only head a611048 still failed21/3 on remote run39216004671. Diagnostic-only head e33cf699 retained every assertion and logged actual URLs; remote run39217040163 directly identified requestfailed and console.location for https://www.beian.gov.cn/img/ghs.png (ERR_CONNECTION_REFUSED). Thus the earlier local avatar refusal was a sufficient failure mechanism, not proof of the remaining CI root cause. Both failed histories remain disclosed.

Only the two mock visual specs now fulfill that exact image URL with existing inline SVG fixture bytes. Production App.vue,备案 links/number/loading, dependency versions, all diagnostic/error assertions and CI selection/thresholds are unchanged. No whole-domain or all-network success/error suppression is introduced.

Node20 default E2E24/24 and productionaudit0 passed locally. The reviewer independently reran all4 affected visual scenarios with a lower-priority context refusal: each exact image request was handled locally, response200/image SVG/SHA matched, serverAddr null, decoded width64, fallback refusal0, avatar network requests0, failed/console/pageerrors0. The4 are a subset of24, not28 distinct tests. Complete development audit remains44 as disclosed.

The release reviewer follows the new exact-head backend/frontend/docker-build workflow to explicit outcomes; no local result or skipped Docker job counts as remote success. Mark ready only after all three succeed, never auto-merge/deploy.

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
