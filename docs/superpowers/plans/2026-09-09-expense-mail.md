# Expense Mail Implementation Plan

**Goal:** Build the approved photo-only monthly evidence submission flow with Gmail sending.

**Architecture:** A dependency-free Apps Script web app, one HTML UI and a small server file. Advanced Gmail API sends MIME attachments; a local preview exposes no mail endpoint.

**Spec:** ../../design.md

## Task 1: Validated Gmail message pipeline
- [ ] Add Node tests that run Code.gs in a VM with boundary doubles for Google APIs. Assert decoded MIME recipient, Korean subject/body and original image bytes; assert invalid recipient, month, files, size, replay, changed request, ambiguous response and caller rejection.
- [ ] Run `node --test tests/*.test.cjs`; observe missing implementation failures.
- [ ] Implement `getBootstrap()`, `saveRecipient(email)`, `sendExpense(payload)` and private validation/MIME helpers in `app/Code.gs`.
- [ ] Add `app/appsscript.json` with Gmail v1 advanced service, gmail.send and userinfo.email scopes, private webapp configuration.
- [ ] Run the suite and correct errors.

## Task 2: Usable photo upload and mail preview
- [ ] Implement `app/Index.html` using the generated white/forest-green concept: two columns desktop, one column mobile; original photo thumbnails and remove actions; month-based editable email; saved recipient; review dialog; explicit connection and sending state.
- [ ] Disable sending in local preview and protect against upload/send races.
- [ ] Add a loopback-only preview server and script syntax validation with Node built-ins.
- [ ] Use the in-app browser to verify page, recipient/month editing, upload/removal, review or disconnected gating, responsive layout and console health.

## Task 3: Account handoff
- [ ] Package the three Apps Script files with Korean setup instructions and a helper page that lets the user copy each file.
- [ ] Attempt Apps Script access using the user browser. If unauthenticated, prepare the login page; do not claim Gmail connected.
- [ ] Once signed in, create the private script, populate code, deploy and obtain OAuth approval at the permission boundary.
- [ ] Report exactly which local checks passed and what live verification remains.
