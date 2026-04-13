# User Permissions Response Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make backend user responses include role-derived permissions so the frontend can correctly render order action buttons.

**Architecture:** Keep the existing frontend permission checks unchanged and patch the backend user DTO mapping. Add a focused unit test around `UserServiceImpl.getCurrentUser`, then extend `UserVO` and role-to-user mapping to emit a deduplicated `permissions` list.

**Tech Stack:** Spring Boot 2.7, JUnit 5, Mockito, Lombok

---

### Task 1: Lock the missing permissions behavior with a failing test

**Files:**
- Create: `src/test/java/com/garment/service/impl/UserServiceImplTest.java`

- [ ] **Step 1: Write a failing test for admin permissions**
- [ ] **Step 2: Run `mvn -Dtest=UserServiceImplTest test` and verify it fails because `permissions` are absent**

### Task 2: Return permissions from backend user info

**Files:**
- Modify: `src/main/java/com/garment/dto/UserVO.java`
- Modify: `src/main/java/com/garment/service/impl/UserServiceImpl.java`

- [ ] **Step 1: Add a `permissions` field to `UserVO`**
- [ ] **Step 2: Collect permissions from each resolved role in `UserServiceImpl.convertToVO`**
- [ ] **Step 3: Return a deduplicated permission list in the built `UserVO`**

### Task 3: Verify the regression is fixed

**Files:**
- Test: `src/test/java/com/garment/service/impl/UserServiceImplTest.java`

- [ ] **Step 1: Re-run `mvn -Dtest=UserServiceImplTest test` and verify it passes**
- [ ] **Step 2: Re-run a small compile-safe backend verification if needed**
