# System Backup & Archival Guide

This document describes the automated and manual backup mechanism implemented in the I Rasa Perfumes Backend.

---

## 1. Architecture Overview

The backup system is structured around three key components:
1. **`BackupService`**: The core execution engine responsible for invoking `mysqldump`, compressing the file systems, sending emails with attachments, and cleaning up temporary space.
2. **`BackupScheduler`**: The automated runner configured to invoke the service on the last day of each month.
3. **`BackupController`**: The secure API endpoint that allows administrators to manually trigger backups on demand using a dedicated passcode.

### System Workflow
```
[Backup Scheduler (Cron)] ──────┐
                                ├─> [BackupService] ─> Executes mysqldump & zips uploads/logs ─> Emails Zip & cleanups temp files
[Manual Trigger API (POST)] ────┘
```

---

## 2. Configuration & Environment Settings

The backup system is fully configurable using environment variables. The configuration is defined in the base `application.yaml` file:

| Property | Environment Variable | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `app.backup.email` | `BACKUP_EMAIL` | `irasaperfumes@gmail.com` | The destination email where all backup files will be sent. |
| `app.backup.passcode` | `BACKUP_PASSCODE` | `irasabackup123` | The passcode required to authenticate the manual trigger API. |

### SMTP Config
The system uses the configured Spring Mail Sender (`spring.mail.*`) to dispatch the backups. Ensure that the SMTP credentials in `application-prod.yaml` or standard environment variables (`MAIL_USERNAME` and `MAIL_PASSWORD`) are correct and have permissions to send attachments up to 25MB.

---

## 3. Automation Scheduler

The backup scheduler is configured in `BackupScheduler.java` using Spring's `@Scheduled` annotation.

- **Cron Expression**: `0 0 23 L * ?`
- **Execution Time**: 11:00 PM on the Last day (`L`) of every month (`*`).
- **Timezone**: `Asia/Kolkata` (Indian Standard Time / IST).

This ensures that the database and files are safely backed up at the end of each month without manual intervention.

---

## 4. API Reference: Manual Backup Trigger

For on-demand backups, a public REST endpoint is provided. This endpoint is configured to bypass Spring Security's standard filters and ignores CSRF token checks.

* **Route**: `/api/auth/backup`
* **Method**: `POST`
* **Content-Type**: `application/json`

### Request Body Format
```json
{
  "email": "irasaperfumes@gmail.com",
  "passcode": "irasabackup123"
}
```

### Response Formats

#### 1. Success (200 OK)
```json
{
  "success": true,
  "message": "Backup process successfully started in the background. The backup files will be sent to: irasaperfumes@gmail.com"
}
```

#### 2. Unauthorized (401 Unauthorized)
Returned if the email or passcode does not match the configured environment variables.
```json
{
  "success": false,
  "message": "Invalid email or passcode."
}
```

#### 3. Bad Request (400 Bad Request)
Returned if input parameters are missing.
```json
{
  "success": false,
  "message": "Email and passcode inputs are required."
}
```

#### 4. Server Error (500 Internal Server Error)
Returned if system error occurs.
```json
{
  "success": false,
  "message": "Backup configuration error on the server."
}
```

---

## 5. Shell Command Testing Examples

### Using `curl` (Linux / macOS / Git Bash)

**Local Server:**
```bash
curl -X POST http://localhost:8080/api/auth/backup \
  -H "Content-Type: application/json" \
  -d '{"email": "irasaperfumes@gmail.com", "passcode": "irasabackup123"}'
```

**Production Server:**
```bash
curl -X POST https://www.irasaperfumes.in/api/auth/backup \
  -H "Content-Type: application/json" \
  -d '{"email": "irasaperfumes@gmail.com", "passcode": "irasabackup123"}'
```

### Using PowerShell (Windows)

**Local Server:**
```powershell
$body = @{
    email = "irasaperfumes@gmail.com"
    passcode = "irasabackup123"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/auth/backup" -Method Post -Body $body -ContentType "application/json"
```

**Production Server:**
```powershell
$body = @{
    email = "irasaperfumes@gmail.com"
    passcode = "irasabackup123"
} | ConvertTo-Json

Invoke-RestMethod -Uri "https://www.irasaperfumes.in/api/auth/backup" -Method Post -Body $body -ContentType "application/json"
```

### Using Windows Command Prompt (cmd.exe)

**Local Server:**
```cmd
curl -X POST http://localhost:8080/api/auth/backup -H "Content-Type: application/json" -d "{\"email\":\"irasaperfumes@gmail.com\",\"passcode\":\"irasabackup123\"}"
```

**Production Server:**
```cmd
curl -X POST https://www.irasaperfumes.in/api/auth/backup -H "Content-Type: application/json" -d "{\"email\":\"irasaperfumes@gmail.com\",\"passcode\":\"irasabackup123\"}"
```

---

## 6. Troubleshooting & Best Practices

1. **`mysqldump` Command Not Found**:
   - The backup service executes `mysqldump` as a system subprocess. The host operating system MUST have the MySQL client package installed, and `mysqldump` must be present in the system's `PATH`.

2. **Mail Attachment Size Limits**:
   - Gmail and most mail providers limit email attachments to 25MB.
   - If the database or `uploads/` directory becomes excessively large, the email delivery might fail due to SMTP size limits.

3. **Memory and Space Cleanup**:
   - The backup process creates temporary files inside the system's temporary directory.
   - The code handles cleanup using a `finally` block that deletes all temp `.sql` and `.zip` files.

4. **Nginx 504 Gateway Time-out & Asynchronous Execution**:
   - The backup operation performs heavy blocking tasks (SQL dumping, zipping directories, and sending emails) that can easily take over 60 seconds to complete.
   - **Fix**: The manual trigger endpoint `/api/auth/backup` runs asynchronously in a background thread pool (`emailTaskExecutor`). The client receives a `200 OK` status immediately, and the backup proceeds in the background, preventing timeouts.
