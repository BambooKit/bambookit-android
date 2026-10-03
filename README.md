# BambooKit Android

> Mobile control plane and observability client for **BambooKit** — "The control plane for AI software engineers."

BambooKit Android enables developers to monitor, inspect, and approve autonomous AI coding agents operating across their Windows PCs (via **BambooKit Desktop**) and cloud environments (via **BambooKit Worker**).

---

## 1. What BambooKit Android Is

BambooKit Android is **NOT**:
* An AI coding agent itself
* A terminal emulator for arbitrary remote shell access
* A generic remote desktop application

Its architectural role:
```text
Android App
     ↓
BambooKit API
     ↓
Desktop / Cloud Worker
     ↓
Coding Agent
     ↓
Project / Files / Git / Tests / Deployment
     ↓
Events
     ↓
BambooKit API
     ↓
Android App
```

---

## 2. Tech Stack & Architecture

* **Language**: Kotlin 2.0.21
* **UI**: Jetpack Compose + Material 3
* **Design Language**: Dark-first (Obsidian surface, Bamboo Green `#10B981` accent, structured high-density developer cards)
* **Architecture**: Repository pattern, Kotlin Coroutines, StateFlow / Flow reactive architecture
* **Build System**: Gradle 8.13 + Android Gradle Plugin 8.8.2 + Version Catalog (`gradle/libs.versions.toml`)
* **Serialization**: Kotlinx Serialization JSON
* **Target SDK**: Android 15 (API 35) / Min SDK: Android 8.0 (API 26)

---

## 3. Core Features & Screens

1. **Control Center (Home)**:
   - Real-time workspace overview (`Satyams Workspace`)
   - Needs Attention queue (interactive pending approval requests)
   - Active agents and running tasks status badges (`RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`)
   - Connected Windows PC status indicator (`ONLINE`, `BUSY`, `OFFLINE`)
2. **Interactive Approvals**:
   - High-security policy gate for terminal commands (`npm install ...`), filesystem writes, and Git pushes
   - Instant "Approve" / "Reject" responsive actions
3. **Projects Registry**:
   - Monitored repositories and local project workspaces
   - Active agent binding indicators
4. **Task Lifecycle Management**:
   - Real-time task inspection
   - Authoritative task cancellation triggers

---

## 4. Building & Running

### Prerequisites
* JDK 17 or 21
* Android SDK (API 35 platform, Build-Tools 35.0.0+)

### Commands

```powershell
# Run unit tests
.\gradlew.bat testDebugUnitTest

# Assemble debug APK
.\gradlew.bat assembleDebug
```

The compiled APK is located at:
`app/build/outputs/apk/debug/app-debug.apk`

