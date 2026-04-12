# GhostLink 🛡️

**Real-time, Privacy-First Phishing & Scam Detection for Android.**

GhostLink is a specialized security tool designed to protect users from the rising tide of mobile phishing, SMS scams (smishing), and fraudulent banking requests. By analyzing screen content in real-time, GhostLink acts as a "second pair of eyes," identifying malicious patterns before users can accidentally disclose sensitive information or click dangerous links.

---

## 🚀 Overview

Mobile scams are increasingly sophisticated, often bypassing traditional spam filters. GhostLink fills this gap by monitoring the user's screen during active messaging or banking sessions. It uses a combination of on-device OCR and a weighted heuristic scoring engine to detect urgency, threats, and impersonation attempts.

### Key Features
- **Live Screen Analysis**: Continuous monitoring using the Android Media Projection API.
- **On-Device OCR**: Local text extraction from screen content—no data ever leaves the device.
- **Smart Risk Scoring**: A rule-based engine (`RiskScorer`) that analyzes text for urgency, threats, and suspicious URLs.
- **Floating Overlay**: Real-time visual feedback (SAFE, RISKY, DANGER) that persists over other apps without blocking interaction.
- **Actionable History**: A dedicated stats dashboard to review past detections, view screenshots, and directly block senders.
- **Context-Aware Responses**: Differentiates between Email and SMS to provide platform-specific blocking instructions.

---

## 🛠️ How It Works (Technical Architecture)

GhostLink is built on a robust, performance-optimized pipeline:

1.  **Capture**: A `Foreground Service` utilizes the `Media Projection API` to take snapshots of the screen at a throttled rate (every 2 seconds) to preserve battery and memory.
2.  **Extraction**: Images are processed via a local `OcrProcessor` to extract raw text strings.
3.  **Analysis**: The `RiskScorer` applies weighted heuristics and regex patterns to the text. For example:
    *   **Threats** (e.g., "Account suspended"): +30 points
    *   **Urgency** (e.g., "Act within 24 hours"): +25 points
    *   **Suspicious Links**: +20 points
    *   **Thresholds**: 60+ points triggers a **DANGER** alert.
4.  **UI Feedback**: The `OverlayViewManager` renders a floating bar using the `WindowManager` API with `FLAG_NOT_TOUCHABLE`, allowing users to see alerts while still interacting with the underlying app.
5.  **Storage**: Detections and statistics are stored locally using `SharedPreferences` (serialized JSON) and internal storage for screenshots.

---

## 🧰 Tech Stack

- **Language**: Java / XML
- **Platform**: Android SDK (API 26+)
- **Build System**: Gradle
- **Data Handling**: Google GSON (JSON serialization)
- **UI Components**: Material Design, Android WindowManager (Overlay)
- **APIs**: Media Projection API, Activity Result API, Foreground Services

---

## 🔒 Privacy & Security

Privacy is the core pillar of GhostLink. 
- **100% Offline**: No cloud APIs, no telemetry, and no external data transmission.
- **Local Analysis**: All OCR and text scoring happens strictly on-device.
- **Transparency**: Users are notified via a persistent notification whenever screen capture is active.

---

## 🚧 Challenges Overcome

*   **Performance Optimization**: Managed high-frequency screen capturing by implementing bitmap recycling and temporal throttling to avoid device lag.
*   **Accuracy Balancing**: Developed the `looksLikeInboxList` heuristic to reduce false positives in email navigation screens.
*   **User Experience**: Engineered a non-intrusive "touch-through" overlay that provides vital security information without interrupting the user's workflow.

---

## 📥 Getting Started

1.  Clone the repository.
2.  Open the project in **Android Studio**.
3.  Build and deploy to an Android device (API 26 or higher).
4.  Grant **Screen Recording** and **Display over other apps** permissions when prompted.
5.  Open Gmail or a Messaging app to see GhostLink in action.

---

## ⚖️ Disclaimer

GhostLink is a security assistance tool. While it uses advanced heuristics to identify scams, it is not a replacement for user vigilance. Users should always exercise caution when interacting with unknown senders or clicking links.

---
*Developed for the Bitcamp 2026.*
