# RF-Analyser
# RF-Analyser

A real-time RF signal detection and analysis application for Android, built with Jetpack Compose and advanced sensor integration.

## 🚀 Features

### 📡 Real-Time Signal Detection
- **WiFi Networks**: Detects and analyzes WiFi signals with detailed frequency, channel, and security information
- **Bluetooth Devices**: Scans for both Bluetooth Classic and Bluetooth Low Energy (BLE) devices
- **Cellular Towers**: Identifies LTE, GSM, and WCDMA cellular towers with operator information
- **RF Activity**: Real-time electromagnetic field detection using device magnetometer
- **EMF Sources**: Electromagnetic field source detection and analysis
- **GPS Signals**: GPS satellite signal detection and tracking

### 🎯 Advanced Analysis
- **Signal Strength Measurement**: Real-time dBm readings for all detected signals
- **Distance Estimation**: Calculates approximate distance to signal sources
- **Bearing Detection**: Uses gyroscope data for directional signal analysis
- **Frequency Analysis**: Detailed frequency band identification (2.4GHz, 5GHz, 6GHz)
- **Protocol Classification**: Identifies communication protocols (802.11ac/n, LTE, GSM, etc.)
- **Security Analysis**: Detects encryption types (WPA3, WPA2, AES, etc.)

### 📊 Visualization
- **Pulse Radar Display**: Real-time radar interface with sweep animation
- **Signal Classification**: Color-coded signal categories for easy identification
- **Live Analysis View**: Detailed signal information with real-time updates
- **Animated UI**: Smooth animations and transitions throughout the interface

### 🎨 Modern Interface
- **AMOLED Optimized**: Pure black background for OLED displays
- **Material Design 3**: Latest Material Design components and theming
- **Dark Theme**: Professional dark interface with green accent colors
- **Edge-to-Edge Display**: Immersive full-screen experience
- **Animated Splash Screen**: Professional startup animation with radar effects

### 🔧 Technical Capabilities
- **Multi-Sensor Integration**: Magnetometer, gyroscope, accelerometer, light, and proximity sensors
- **Real-Time Processing**: Continuous signal scanning with 1.5-second intervals
- **Permission Management**: Comprehensive permission handling for all required sensors
- **Hardware Abstraction**: Direct access to WiFi, Bluetooth, and cellular hardware
- **Memory Optimization**: Efficient signal data management with automatic cleanup

## 🛠️ Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: Component-based with sensor event handling
- **Hardware Access**: Direct Android hardware APIs
- **Sensors**: Magnetometer, Gyroscope, Accelerometer, Light, Proximity
- **Networking**: WiFi Manager, Bluetooth Adapter, Telephony Manager
- **Permissions**: Runtime permission handling
- **Animation**: Compose Animation APIs with infinite transitions

## 📋 Requirements

- **Android Version**: Android 7.0 (API level 24) or higher
- **Permissions Required**:
  - Location (Fine & Coarse)
  - WiFi State Access
  - Bluetooth Access
  - Phone State Reading
  - Network State Access
- **Hardware Requirements**:
  - Magnetometer sensor
  - WiFi capability
  - Bluetooth capability
  - Cellular connectivity

## 🎯 Signal Categories

### WiFi Networks (Green)
- 2.4GHz, 5GHz, and 6GHz networks
- Channel identification
- Security protocol detection
- Signal strength and range estimation

### Bluetooth Devices (Blue/Cyan)
- Classic Bluetooth devices
- Bluetooth Low Energy (BLE) devices
- RSSI-based distance calculation

### Cellular Towers (Purple)
- LTE towers with operator identification
- GSM networks
- WCDMA/3G networks
- Cell ID and frequency information

### RF Signals (Yellow)
- Real-time electromagnetic field detection
- Magnetic field strength analysis
- RF activity classification

### EMF Sources (Red-Orange)
- Electromagnetic field source detection
- Field strength measurement in µT
- Variation analysis for EMF activity

### GPS Signals (Light Blue)
- GPS L1 frequency detection
- Satellite signal strength
- Location service integration

## 🚀 Installation

1. **Clone the repository**
```bash
git clone https://github.com/yourusername/RF-Analyser.git
```

2. **Open in Android Studio**
   - Import the project into Android Studio
   - Ensure you have the latest Android SDK

3. **Grant Permissions**
   - The app will request necessary permissions on first launch
   - All permissions are required for full functionality

4. **Build and Install**
   - Connect your Android device
   - Build and run the application

## 🎮 Usage

1. **Launch the App**: Start with an animated splash screen
2. **Grant Permissions**: Allow all requested permissions for full functionality
3. **Start Scanning**: Tap the play button to begin real-time signal detection
4. **View Results**: 
   - Radar display shows signals in real-time with directional information
   - Analysis view provides detailed signal information
   - Color-coded signals for easy identification
5. **Stop Scanning**: Tap the stop button to pause detection

## 🔬 Technical Details

### Signal Processing
- **Scan Interval**: 1.5 seconds for optimal performance
- **Signal Retention**: 8-second rolling window for signal history
- **Distance Calculation**: RSSI-based algorithms with frequency compensation
- **Bearing Calculation**: Real-time gyroscope data integration

### Performance Optimizations
- **Memory Management**: Automatic cleanup of old signal data
- **Hardware Efficiency**: Optimized sensor polling rates
- **UI Rendering**: Smooth 60fps animations with efficient Canvas operations
- **Background Processing**: Minimal background resource usage

## 🔒 Privacy & Security

- **No Data Collection**: All processing happens locally on device
- **No Network Transmission**: Signal data never leaves your device
- **Permission Transparency**: Clear explanation of why each permission is needed
- **Secure Hardware Access**: Direct hardware APIs without third-party dependencies

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## ⚠️ Disclaimer

This application is for educational and research purposes. Always comply with local laws and regulations regarding RF signal monitoring and analysis. The app does not intercept or decode any communications - it only detects the presence and basic characteristics of RF signals.

## 🔄 Version History

- **v1.0.0**: Initial release with comprehensive RF signal detection
- Real-time WiFi, Bluetooth, and cellular signal analysis
- Advanced radar visualization with signal classification
- Multi-sensor integration for enhanced detection capabilities

## 📞 Support

For issues, questions, or feature requests, please open an issue on GitHub or contact the development team.

---

**Built with ❤️ for the RF analysis community**
