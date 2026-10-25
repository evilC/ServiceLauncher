# ToDo list

## Support for other protocols (SSH, FTP etc)

## More robust service identification
Currently, a widget or shortcut matches a service by name only. mDNS resolves name conflicts by renaming the second device (for example "WearWare UI #2"), but which device keeps the plain name depends on boot order. A shortcut could therefore end up pointing at the wrong device.

- Store the service type and the mDNS hostname (from the SRV record, for example `wearware.local`) along with the name. The hostname is only an identifier, never used to connect. `NsdServiceInfo.getHostname` needs API 34+.
- At tap time, match by name first. If the match is ambiguous, or the name carries a conflict suffix, pick by hostname.
- Compatibility: API 34 is Android 14 (October 2023). Below API 34 the app uses the deprecated `resolveService`, where the hostname may not be available, so matching would fall back to name only.

## IPV4 / IPV6 selection
- If a service advertises both IPv4 and IPv6, the app should allow the user to select which one to use. The default should be IPv4, but the user can change it

## Appified shortcuts may nee some more work
What was done and why (applies to both shortcuts and widgets):
- Appify is an opt-in checkbox on creation, HTTP/HTTPS services only. A shortcut carries the setting in its intent; a widget stores it per widget ID in preferences. Existing ones must be recreated or reconfigured with it ticked.
- Each appified shortcut or widget uses a unique data URI and `FLAG_ACTIVITY_NEW_DOCUMENT`, so every shortcut gets its own Recents entry. `excludeFromRecents` was removed from the activity.
- `usesCleartextTraffic="true"` was added, because Android blocks plain HTTP by default and local services are HTTP.
- Task icon and label are set with `setTaskDescription`. The first version used `TaskDescription.Builder.setIcon(Icon)`, which does not exist on API 35 (the test phone) and crashed on launch. It only exists on the API 37 emulator. The deprecated bitmap constructor works on all versions, so it is used instead. The minSdk of 26 is unchanged; the phone is not too old, the API was just too new.
- Recents ignores the task label and icon on some launchers. The emulator shows the icon but still shows "Service Launcher" as the name. The phone's Samsung launcher shows neither. This cannot be fixed from inside the app.
- Targeting API 35+ forces edge-to-edge, so the page was drawn under the status and navigation bars. The WebView sits in a container padded by the system window insets (bars, cutout, keyboard), with system bar icon colours and the background matched to light or dark mode. Padding the WebView itself did not work.
- Links to a different host than the service open in the system browser. Same-host links (tabs, redirects) stay in the WebView. Back navigates WebView history.
- File inputs ("Upload file") need an explicit `WebChromeClient.onShowFileChooser`, otherwise nothing happens. One was added.

Known gaps: popups and new-window links, file downloads, non-http schemes, cookies are separate from Chrome, fullscreen content.
