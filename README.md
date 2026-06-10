# ☁️ SyncVault
SyncVault is a self-hosted, full-stack cloud synchronization platform. It features a Spring Boot REST API backed by AWS S3, and a native Windows background daemon that provides real-time, two-way file synchronization with secure JWT authentication and multi-user support.

### SyncVault Demo Video
[Watch the SyncVault Demo Here](https://www.youtube-nocookie.com/embed/mneZNZqm8EU?autoplay=1&mute=1&loop=1)

## ✨ Core Features
* **Real-Time Synchronization:** Automatically detects file changes locally and syncs them to your private cloud storage.
* **SQLite Tombstoning:** Local file states (and deletions) are tracked in a transaction-safe embedded SQLite database to prevent memory drift and data loss during sudden crashes.
* **Chunked Uploads:** Bypasses standard HTTP request size limits by splitting large files into chunks for reliable AWS S3 delivery.
* **Native Windows Integration:** Runs seamlessly in the background with a modern System Tray menu.
* **Secure Multi-User Architecture:** Supports multiple accounts on the same PC by isolating local file states using dynamic SQLite databases stored safely in the user's home directory.
* **JWT Authentication:** Stateless, secure API communication with persistent token sessions.
* **Resilient Connection:** Built-in heartbeat monitor that handles server downtime and auto-reconnects without spamming logs.

## 🛠️ Tech Stack
**Backend (Server)**
* Java 21
* Spring Boot 3 (Web, Security)
* AWS SDK (S3 integration)
* JSON Web Tokens (JWT) for Authentication

**Frontend (Desktop Client)**
* Java 21 (AWT / Swing for UI)
* Maven (Dependency & Build Management)
* SQLite (Local sync state management)

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
1. Build the project locally:
```bash
./mvnw clean package -DskipTests
```
3. Securely copy the `target/server-0.0.1-SNAPSHOT.jar` to your EC2 instance.
4. Start the server in the background:
```bash
source ~/.syncvault_secrets.sh
nohup java -jar target/server-0.0.1-SNAPSHOT.jar > server.log 2>&1 &
```
---

## 💻 Client Installation & Build

The client is built using Maven, which handles all dependency resolution (like `sqlite-jdbc`) automatically.

### 1. Prerequisites
* Java 21 installed on your local machine.
* Maven installed and added to your system PATH.

### 2. Build & Run the Client
Navigate to your client directory in your terminal and run the following commands to compile and execute the application:
```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="SyncDaemon"
```
*(Note: Replace `"SyncDaemon"` with the actual fully-qualified name of your main class if it is located inside a package, e.g., `"com.syncvault.SyncDaemon"`).*

### 3. Usage
1. Once the application starts, check your Windows System Tray for the SyncVault icon.
2. Right-click the icon and select **Settings** to configure your server URL.
3. Log in, select your target sync folder, and your background daemon will begin syncing immediately!

---

## 🛡️ Security & Best Practices
This repository utilizes a strict `.gitignore` policy to prevent sensitive data leaks.

* **Never commit `.pem` keys.** Keep AWS access keys entirely off version control.
* **Never commit `users.json`.** The user database should only live on the production server.
* **Client Configuration:** Server URLs and local paths are managed via a local `syncvault.properties` file generated on the user's machine at runtime.

---

## 📝 License
This is a personal/indie project built for educational and self-hosting purposes.
