# Real-time Object Detection & Navigation for the Blind

This project is an advanced Android application designed to assist visually impaired individuals in navigating their environment safely. It transforms real-time video feed into actionable navigation instructions using Computer Vision and haptic feedback.

## 🚀 Features

- **Real-time Object Detection**: Uses TFLite to identify obstacles like people, cars, furniture, and more.
- **Autonomous Navigation Engine**: Divides the camera view into Left, Center, and Right zones to calculate the safest path.
- **Intelligent Voice Guidance**: Converts visual data into semantic speech instructions (e.g., "Obstacle ahead. Move slightly right.").
- **Haptic Alerts**: Provides tactile vibration feedback for immediate danger/close proximity obstacles.
- **Dynamic Visual Overlay**: Displays an autonomous-style navigation arrow indicating the suggested path (Forward, Left, Right, or Stop).

## 🧠 VisionGuide AI Architecture

This project has been upgraded to a production-grade assistive navigation system for the blind, moving beyond simple object detection to an intelligent scene-aware guidance system.

### 1. Perception Layer
- **Object Detection**: SSD MobileNet V1 (TFLite) identifies physical entities in the environment.
- **Heuristic Depth Estimation**: The `DepthEstimator` implements a pinhole camera model to approximate real-world distance in meters, providing the critical data needed for collision avoidance.

### 2. Risk Assessment Engine
- **Dynamic Risk Scoring**: Every obstacle is assigned a risk score based on:
    - **Object Priority**: High-risk (vehicles), Medium-risk (people/furniture), etc.
    - **Distance Weight**: Risk increases exponentially as the distance closes.
    - **Path Weight**: Obstacles in the central walking path are heavily penalized.
- **Hazard Categorization**: Translates raw scores into safety levels: `NONE`, `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.

### 3. Safe Path Planner
- **Continuous Steering Logic**: Instead of discrete zones, the `SafePathPlanner` analyzes the scene to find the widest continuous "Free Corridor".
- **Path Optimization**: Calculates a precise steering angle (-30 to +30 degrees) to guide the user smoothly around obstacles.

### 4. Intelligent Guidance System
- **Natural Language Generation**: The `GuidanceManager` creates human-like instructions ("Move slightly left" vs "Chair detected").
- **Smart Throttling**: Implements context-aware cooldowns to prevent repetitive audio while ensuring critical alerts (like STOP) are delivered immediately.
- **Haptic Feedback**: The `HapticManager` translates risk levels into tactile vibration patterns (short pulses for minor warnings, continuous vibration for critical danger).

### 5. Advanced UI Overlay
- **Safe Corridor Visualization**: A dynamic green corridor indicates the recommended walking path.
- **Risk Heatmapping**: Obstacles are color-coded (Red/Yellow/Blue) based on their real-time collision risk score.
- **Distance Labels**: AR-style labels show the estimated distance to obstacles in meters.

## 🛠 Tech Stack

- **Language**: Kotlin
- **Platform**: Android SDK (API 24+)
- **ML Engine**: TensorFlow Lite
- **UI**: Custom Canvas-based Overlay Views
- **Feedback**: Android Text-to-Speech (TTS) & Vibrator API

## 📖 How it Works

1. **Capture**: Camera frames are processed at high FPS.
2. **Detect**: TFLite identifies obstacles and their coordinates.
3. **Analyze**: The `NavigationEngine` maps these to Left/Center/Right zones and estimates distance.
4. **Decide**: The `SafePathAnalyzer` picks the safest direction.
5. **Guidance**: The `ArrowOverlayView` updates the visual guide, and the TTS engine provides vocal steering instructions.

---
*Developed with assistance from AI-driven engineering tools.*
