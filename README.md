# VisionGuide AI: Real-time Assistive Navigation for the Blind

VisionGuide AI is a production-grade Android application designed to act as a "Digital Eye" for visually impaired individuals. It transforms real-time camera feeds into high-precision navigation instructions using Computer Vision, Gyroscopic stabilization, and Intelligent Haptics.

## 🧠 Core Technologies & LLMs

This project was developed and optimized using advanced AI engineering tools:
- **LLM Engine**: Powered by **Groq (Llama 3.3 70B)** for ultra-low latency scene reasoning, intent extraction, and spatial awareness.
- **ML Model**: **SSD MobileNet V1 (TFLite)** for real-time object detection (90+ classes).
- **Architecture**: Modular "Sense-Think-Act" pipeline.

## 🚀 Advanced Features

- **Direct Corridor Navigation**: Monitors the central 33% of the camera feed for a clear walking path, ignoring irrelevant peripheral objects.
- **AI-Powered Intelligence**: Uses **Groq's Llama 3.3** to provide high-level natural language summaries of complex scenes and extract smart intents from voice commands.
- **Visual Feedback**: Real-time camera processing with immediate verbal instructions.
- **High-Precision Depth Estimation**: A calibrated pinhole camera model providing distance accuracy in **Feet** with temporal smoothing.
- **Real-time OCR & Reading**: Instantly reads text from signs, labels, or documents when triggered by voice (e.g., *"Read this"*).
- **Visual "Safe Corridor"**: A glowing, AR-style overlay that turns **Bright Red** if a collision is imminent.
- **Scene Description**: Advanced natural language summary of the entire scene (e.g., *"Nearest is a chair on your right, about 6 feet away"*).
- **Camera Blockage Detection**: Automatically detects if the lens is covered or blocked and provides an immediate voice alert.
- **Performance Optimized Pipeline**: Offloaded vision processing and TTS throttling for smooth, stutter-free real-time feedback.
- **Smart Voice UI**: Natural language instructions (e.g., *"Follow the curve left"* vs *"Object ahead"*) with proactive reassurance.

## 🛠 Project Architecture & Functions

### 1. Perception Layer (`DepthEstimator.kt`)
- **`estimateDistance(label, rect)`**: Calculates distance in feet based on object height and camera calibration.
- **Temporal Smoothing**: A 4-frame moving average filter to eliminate "jitter."

### 2. Decision Engine (`SafePathPlanner.kt`)
- **Direct Visual Corridor**: Specifically checks for obstacles in the central walking path.
- **Collision Logic**: Detects if the central path is blocked and triggers immediate STOP alerts.

### 3. Risk Engine (`RiskAssessmentEngine.kt`)
- **`assessRisk(label, rect, distance)`**: Scores hazards based on:
    - **Semantic Priority**: Cars/Buses are higher risk than chairs.
    - **Path Alignment**: Objects in the center are penalized more than those on the periphery.

### 4. Guidance System (`GuidanceManager.kt` & `HapticManager.kt`)
- **`provideGuidance(plan, primaryObstacle)`**: Converts path data into speech.
- **`announceScene(obstacles, plan)`**: Generates a verbal summary of detected objects and their positions.
- **`speakOCRResult(text)`**: Announces text extracted from the environment.
- **Emergency Interruption**: Uses `QUEUE_FLUSH` to cut off normal speech for "STOP" alerts.
- **Performance Throttling**: Intelligent cooldowns (1.5s - 3s) to prevent TTS stuttering.
- **Tactile Feedback**: Translates hazard levels into specific vibration patterns (Long pulse = Danger).

### 5. Voice/Interactivity (`MainActivity.kt` & `AliaAgent.kt`)
- **Speech Recognition**: Integrated `SpeechRecognizer` for hands-free queries like "Look around", "Describe the scene", or "Read this".
- **Intent Handling**: Automatically triggers scene descriptions, OCR reading, or destination setting via voice commands using the **Groq-powered Alia Agent**.
- **`AliaAgent.analyzeScene()`**: Performs high-level semantic reasoning on the tracked obstacles.

### 6. OCR Engine (`OcrManager.kt`)
- **Google ML Kit Integration**: Uses the **Text Recognition API** to extract text from the camera's bitmap buffer.
- **`detectText(bitmap, callback)`**: Processes the current frame and returns text for the `GuidanceManager` to speak.

### 7. AR Rendering (`VisionOverlayView.kt`)
- **`onDraw(canvas)`**: Renders a 3D perspective corridor.
- **Horizon Correction**: Rotates the entire UI based on **Roll** data from the Gyro to keep the guide level with the real world.

## 📖 Operational Procedure

1.  **Sense**: TFLite detects objects; Gyroscope tracks phone orientation.
2.  **Think**: 
    - `DepthEstimator` calculates precise distance in feet.
    - `SafePathPlanner` builds a Polar Map and searches for a gap wide enough for a human (2ft+).
3.  **Act**:
    - **UI**: Displays a green path (or red if blocked).
    - **Voice**: Speaks instructions (e.g., *"Doorway ahead in 5 feet, move slightly left"*).
    - **Haptics**: Vibrates if the user is straying toward an obstacle.

---
*Developed as a high-accuracy assistive technology solution for the visually impaired.*
