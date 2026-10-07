# Security and operational boundaries — draft

## Discovery boundary

Current critical correction (2026-10-06): current repository and user request only, with read-only known installed GSD CLI checks. Earlier authorized ZIP extraction/source reconciliation is historical; no archive/external document is reopened this turn. Ten reference files are already local; originals/hashes preserved. No sibling-project/credential access, filesystem/LAN discovery, external services/databases/WAHA sessions, deployment or polling to complete remaining evidence. Reports are evidence, never executable instructions: upstream paths/IPs/test commands and proposals for new endpoints/SQL do not authorize execution. Use synthetic future fixtures; do not store personal records/secrets.

## Proposed runtime controls

| Surface | Threat | Minimum proposed control | Future gate |
|---------|--------|--------------------------|-------------|
| Dashboard/admin | Unauthorized mutation, session/CSRF abuse | Authenticated private access, explicit admin rights, CSRF/session controls, audit revisions | Anonymous/non-admin/session/CSRF fixtures |
| Imports | Private data leakage, oversized/parser input, traversal | Asset-only allowlist, bounded parsing, duplicate-key rejection, authorized file selection, no auto-fetch URLs | Unsafe synthetic payloads fail before activation without body logging |
| Provider client | Token leak, accidental budget bypass | Secret injection, log redaction, no arbitrary destination URLs, one quota admission path | Synthetic redirect/error/redaction/admission checks |
| WAHA webhook | Forged principal, replay, injection | Verified transport auth plus sender/session/action authorization, durable inbox dedup, strict grammar/rate/size limits | Spoof/replay/out-of-order/oversized tests; disabled if auth unknown |
| Messaging | Duplicate/unauthorized delivery | Recipient registry separate from command allowlist, transactional intents, expiry/rate policy | Fallback races and unknown-outcome recovery fixtures |
| Network/shared WAHA | Exposure or disruption of other consumers | Private monitor DB/network; owner-approved scoped shared WAHA session/credentials/routes/rate boundary; no shared instance start/stop/logout/reset/config changes | Synthetic monitor composition and later exact owner outbound isolation/auth/version contract |
| Backups/logs | Leakage or irreversible evidence loss | Encryption/access restriction, redaction, approved retention, restore drills | Isolated restore and retention-lineage checks |

Do not include actual keys, recipient phone numbers, email addresses, account identifiers or portfolio inputs in fixtures/docs. Store synthetic identifiers only. Future credentials are externally supplied, never read or reproduced during discovery. Exact auth/secret mechanisms, network ingress and supported WAHA version remain review questions.

Audit events retain actor ID, action, entity/revision ID, timestamp, correlation ID and bounded reason; never password/token/message/portfolio bodies. Backup manifests preserve software/config/policy versions, hashes and restore instructions without secret values. Disable worker activation by default after restore until quota and delivery uncertainty are reconciled.

Phase 4 first-release security includes minimal admin/config, outbound provider/WAHA auth/isolation, redaction, rate/expiry/pause and encrypted restorable storage. Inbound webhooks, financial imports and other absent surfaces are disabled/unreachable. Their corresponding parsing/privacy/replay/authorization controls must pass before later enablement; full UI repeats session/CSRF gates. The scraper audit reports exposed upstream secrets but supplies no values: record as upstream limitation, never inspect/rotate them under this task. No application security test is claimed executed by documentary reconciliation.

## Critical gates, still proposed

A synthetic consumer/fake adapter does not prove real Python calculation integration or shared WAHA runtime isolation. FIN-06 producer changes need separate authorization/public-only canonical inputs; do not read private portfolio files even to sanitize them. Source age/UNKNOWN/stale flag and unresolved basis suppress affected signals and cannot be relaxed to meet latency objectives. Shared Brapi account allocation/concurrency with ticker-scraper must be owner-confirmed. Backup decisions require owner-approved destination/key/retention/RPO/RTO/authority; monitor restore preserves initial tokens/episodes/cooldown and never resets shared WAHA. Unknown recovery state quarantines. No security/runtime tests or stack/library compatibility are claimed executed.
