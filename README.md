# ☁️ SyncVault

SyncVault is a self-hosted, full-stack cloud synchronization platform. It features a Spring Boot REST API backed by AWS S3, and a native Windows background daemon that provides real-time, two-way file synchronization with secure JWT authentication and multi-user support.

## ✨ Features
* **Real-Time Synchronization:** Automatically detects file changes locally and syncs them to your private cloud storage.
* **Chunked Uploads:** Bypasses standard HTTP request size limits by splitting large files into chunks for reliable AWS S3 delivery.
* **Native Windows Integration:** Runs seamlessly in the background with a modern System Tray menu and a native `.exe` Windows installer.
* **Secure Multi-User Architecture:** Supports multiple accounts on the same PC by isolating local file states using dynamic SQLite databases stored safely in the user's home directory.
* **JWT Authentication:** Stateless, secure API communication with token persistence.
* **Resilient Connection:** Built-in heartbeat monitor that handles server downtime and auto-reconnects without spamming logs.

## 🛠️ Tech Stack
**Backend (Server)**
* Java 17+
* Spring Boot 3 (Web, Security)
* AWS SDK (S3 integration)
* JSON Web Tokens (JWT) for Authentication

**Frontend (Desktop Client)**
* Java (AWT / Swing for UI)
* SQLite (Local sync state management)
* `jpackage` & WiX Toolset (Native Windows `.exe` bundling)

---

## 🚀 Server Deployment (AWS EC2)

The server is designed to run on an Ubuntu Linux environment using background execution.

### 1. Prerequisites
* Java 17 or higher installed on the server.
* Maven installed locally to build the `.jar`.
* An AWS S3 Bucket and IAM User credentials.

### 2. Environment Variables
For security, credentials are **never** hardcoded. Create a hidden script on your server (e.g., `~/.syncvault_secrets.sh`) and add your production values:

```bash
# JWT and Auth Secrets
export JWT_SECRET="your_super_long_random_jwt_secret"
export APP_USERNAME="admin_username"
export APP_PASSWORD="admin_password"

# AWS Secrets
export AWS_ACCESS_KEY_ID="your_aws_access_key"
export AWS_SECRET_ACCESS_KEY="your_aws_secret_key"
export AWS_DEFAULT_REGION="ap-south-1"

# Spring Boot Configurations
export SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE="10MB"
export SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE="10MB"
```
### 3. Build & Run
1. Build the project locally: `./mvnw clean package -DskipTests`
2. Securely copy the `target/server-0.0.1-SNAPSHOT.jar` to your EC2 instance.
3. Start the server in the background:

```bash
source ~/.syncvault_secrets.sh
nohup java -jar target/server-0.0.1-SNAPSHOT.jar > server.log 2>&1 &
```

---

## 💻 Client Installation & Build

The client is bundled into a native Windows Installer (`.exe`) so users don't need to mess with `.jar` files or terminals.

### 1. Build the Client Jar
Compile your client code and ensure `sqlite-jdbc.jar` is included in your classpath.

### 2. Create the Windows Installer
Use the `jpackage` tool (requires WiX toolset installed on your Windows machine) to generate the exe. 
Run this command from your build directory:

```cmd
jpackage --type exe --dest output_installers --input package_input --name SyncVault --main-jar SyncVaultClient.jar --main-class SyncDaemon --win-dir-chooser --win-shortcut --win-menu --add-modules java.sql,java.naming,java.desktop,jdk.unsupported --app-version 1.0
```
*Note: The `--add-modules java.sql` flag is strictly required to prevent the bundled JRE from stripping out the SQLite database drivers.*

### 3. Usage
1. Install the resulting `SyncVault-1.0.exe`.
2. Launch the app from the Start Menu.
3. Right-click the SyncVault icon in your System Tray and select **Settings** to configure your server URL.
4. Log in, select your sync folder, and your files will begin syncing immediately!

---

## 🛡️ Security & Best Practices
This repository utilizes a strict `.gitignore` policy to prevent sensitive data leaks.

* **Never commit `.pem` keys.** Keep AWS access keys entirely off version control.
* **Never commit `users.json`.** The user database should only live on the production server.
* **Client Configuration:** Server URLs and local paths are managed via a local `syncvault.properties` file generated on the user's machine at runtime.

---

## 📝 License
This is a personal/indie project built for educational and self-hosting purposes.
