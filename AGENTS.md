# ServiceLauncher Project Constraints & Architecture

## Notes
- Items which are ~~struck through~~ are planned but not yet implemented.
- This app supports both Widgets and Shortcuts. For brevity, the documentation will refer to "widgets" only, but the same applies to shortcuts.

## Application Purpose & Capabilities
- **ServiceLauncher** is a native Android utility designed for users operating in private, local network environments (specifically mobile phone hotspots).
- Its primary job is to **discover active network services** on the hotspot network via mDNS/DNS-SD (Avahi) and provide a **one-click launcher** to connect to them.
- The flow is as follows:
   - User adds a widget, and as part of the widget creation process, an mDNS scan happens and the user is presented with a list of what responded.
   - The user selects a service (eg `WearWare UI`, which is reporting that it is HTTP and on Port 5000).
   - ~~The user is able to select from IPv4 or IPv6 (4 should be the default)~~
   - The widget is created and points solely to the service name (eg `WearWare UI`) and service type (eg `_http._tcp.`).
   - When the user taps the widget, a fresh request is made (To get the *current* IP and port, which could have changed since the widget was created).
   - The app prepends the appropriate protocol (eg `http://`) to the IP and port, and raises an "intent" with Android - it is up to Android to decide what to do with it (eg a web browser will open, or a terminal app will open for SSH).
   - If no app is installed that can handle the protocol, the IP and port are copied to the clipboard and a notification is shown to the user.
- Custom DNS-SD TXT records can be used to control various aspects of the URI created, for example:
  - The text on the widget and shortcut icons, as well as the label below them.
  - The username to use (eg `ssh://pi@host:22`).
- Any protocol can be supported, as long as it has a URI scheme that Android can handle. The app ships with a list of common protocols, and the user can add their own.

## Developer Persona & Style Rules
- The developer is fairly experienced in **C#**, and somewhat experienced in Java.
- The developer has no prior experience with Android development or Kotlin.
- Prefer clean, concise property access, and type inference. Avoid verbose, old-school Java boilerplate structures.
- Do not use functional emojis or verbose conversational preambles in code comments.
