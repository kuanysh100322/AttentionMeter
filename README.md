# Attention Meter

This project is a desktop activity tracking system that measures user attention based on window switching behavior.

## Overview

The idea of the project is that frequent switching between applications (browser tabs, IDE, etc.) may indicate lower concentration, while fewer switches may suggest better focus.

The system tracks active windows, records switch events, stores them in daily log files, processes the data, and visualizes the results.

---

## Components

### 1. Desktop Activity Tracker (`Main.java`)
- Monitors the active window every few seconds
- Detects switches between applications
- Logs:
    - timestamp
    - event type
    - process name
    - window title
- Stores data in daily CSV files inside:

```text
logs/YYYY-MM-DD.csv