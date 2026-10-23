# ServiceLauncher Project Constraints & Architecture

## Application Purpose & Capabilities
- **ServiceLauncher** is a native Android utility designed for users operating in private, local network environments (specifically mobile phone hotspots).
- Its primary job is to **discover active network services** on the hotspot network via mDNS/DNS-SD (Avahi) and provide a **one-click launcher** to connect to them.
- Instead of just listing what is on the network, it bridges the discovery-to-connection gap by immediately spawning the correct application interface (e.g., launching a web browser for HTTP endpoints, or handing off an automated socket connection to a system terminal handler for SSH instances).
- The end goal is that the user should be able to add a widget to their home screen which connects to a specific service with a single tap, without needing to know the underlying IP address or port.
- For example, user adds a widget, and as part of the widget creation process, an mDNS scan happens and presents the user with a list.
   - The mDNS scan finds a number of services, one of which is `WearWare UI` - an http service on the local network.
   - The user selects the `WearWare UI` service, which is reporting to be on Port 5000, and has addresses for both IPv4 and IPv6.
   - The user is able to select from IPv4 or IPv6 (4 should be the default)
   - The widget is created, and when the user taps it, the system browser opens to `http://<resolved-ip>:5000` without the user needing to know the IP address or port.
- The app should also support launching other types of services, such as SSH, FTP, or custom TCP services, by resolving the service's IP and port and passing them to the appropriate handler.
  This is considered a secondary feature, and the app should focus on HTTP services first.

## Developer Persona & Style Rules
- The developer is fairly experienced in **C#**, and somewhat experienced in Java.
- The developer has no prior experience with Android development or Kotlin.
- Prefer clean, concise property access, and type inference. Avoid verbose, old-school Java boilerplate structures.
- Do not use functional emojis or verbose conversational preambles in code comments.

## Critical Network Constraints
1. **No System Hostname Resolution:** Android intentionally blocks `.local` text hostname resolution at the OS level (e.g., Chrome/WebView cannot resolve `http://wearware.local:5000`).
2. **Direct IP/Port Execution:** The app must use the native `NsdManager` (DNS-SD) to run a meta-query (`_services._dns-sd._udp.`) to discover active local services, filter for `_tcp`, and resolve the raw network metrics directly.
