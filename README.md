# 🚗 SmartPark - Smart Parking Management System

A desktop-based Smart Parking Management System built using **Java** and **Java Swing** GUI. It provides a real-time, visually interactive dashboard to manage parking slots, user registrations, bookings, dynamic pricing, and administrative controls.

---

## ✨ Features

* **Interactive GUI Dashboard:** Real-time visual tracking of 10 parking slots with color-coded status (Available, Reserved, Occupied).
* **User Management:** Register and login system with custom User IDs and vehicle tracking.
* **Smart Slot Booking:** Select start and end times with dynamic hourly pricing ($\text{₹}20$–$\text{₹}40$/hr based on occupancy).
* **Live Timers & Progress Bars:** Visual countdowns and occupancy gauge tracking active reservations.
* **Detailed Slot Insights:** Click any slot card to view current status, booking history, and free timings.
* **My Bookings View:** Table view displaying active and past user bookings.
* **Admin Panel:** Password-protected dashboard ($\text{password: } \texttt{admin123}$) to monitor all reservations and simulate vehicle entry/exit.

---

## 📸 Screenshots

### 1. Main Dashboard & Slot Booking
<img width="1917" height="1078" alt="Screenshot 2026-11-10 203924" src="https://github.com/user-attachments/assets/17fda7e4-db75-48e6-9f3a-d96aeb0bef7b" />

### 2. Reserved Slot & Live Countdown
<img width="1478" height="1078" alt="Screenshot 2026-11-10 204359" src="https://github.com/user-attachments/assets/9d7dc44e-3b64-4571-a0f4-74769c6f995f" />


### 3. Slot Details Popup
<img width="330" height="503" alt="Screenshot 2026-11-10 204417" src="https://github.com/user-attachments/assets/b8111a0c-290c-47fd-87c5-c0fc1e7d5da3" />


### 4. My Bookings Table
<img width="806" height="487" alt="Screenshot 2026-11-10 204447" src="https://github.com/user-attachments/assets/98bf4596-4cd7-48fb-b49c-9962c874d8ec" />



---

## 🛠️ Tech Stack

* **Language:** Java
* **UI Framework:** Java Swing (`javax.swing`, `java.awt`)
* **Time API:** Java `java.time` (`LocalDateTime`, `Duration`)

---

## 🚀 How to Run Locally

1. **Prerequisites:** Make sure you have **Java JDK** installed on your system.
2. **Clone or Download** this repository.
3. Open your terminal or command prompt inside the project folder.
4. Compile the Java file:
   ```bash
   javac SmartParkingSystem.java
