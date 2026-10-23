# Server Updates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Update CCTV from a public HTTPS feed using the TV remote.
**Architecture:** Pure Java update policy/network code, native Android dialog and install handoff, public distribution repository. Retain the existing signing identity and encrypted profiles.
**Tech Stack:** Java 17, Android API 26+, HttpURLConnection, PackageManager.
**Spec:** docs/superpowers/specs/2026-10-23-server-updates-design.md

## Global Constraints
HTTPS redirects only; manifest <=64 KiB; APK <=64 MiB; matching package/signature/version/checksum; explicit user download and install actions.

## Review Focus
- Older/equal release reports up to date.
- Oversized/truncated/corrupt downloads never reach installer.
- Cancel/background disconnects work and deletes partial data.
- Android source permission denied returns to an Install action.
- Feed unavailable gives Retry and never blocks camera playback permanently.

### Task 1: Update policy and transfer
- [ ] Add standalone UpdateChecks for URL, version, size, digest, truncation and cancellation; run failure first.
- [ ] Implement UpdatePolicy.java and UpdateTransfer.java; run standalone and JUnit checks.
### Task 2: Native update screen
- [ ] Implement AppUpdater.java and UpdateApkProvider.java with explicit install permission, package and signing checks.
- [ ] Wire Settings and welcome entry points, lifecycle cancellation, version 0.3.0 and manifest permission/provider.
- [ ] Extend native UI test to open updates then cancel and resume grid.
### Task 3: Release
- [ ] Configure retained signing, run build/lint/unit/native UI CI.
- [ ] Verify signed APK; upload versioned APK and latest.json together to public repository.
- [ ] Verify feed/download, document publication command and update limits.
