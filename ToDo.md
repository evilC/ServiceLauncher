# ToDo list

## Support for other protocols (SSH, FTP etc)

Goal: support any service type, not just HTTP. Discovery can be mostly automatic; launching cannot.

### Discovery
- `NsdManager` needs an explicit type string (for example `_ssh._tcp`). The generic "list all types" query is not reliable on Android.
- Browse a built-in list of common types: `_http._tcp`, `_https._tcp`, `_ssh._tcp`, `_sftp-ssh._tcp`, `_ftp._tcp`, `_smb._tcp`, `_rfb._tcp` (VNC), and others.
- Add an "Add type..." option for unusual types.
- List entries with their type, for example "WearWare UI (HTTP)" and "WearWare UI (SSH)", since one name can exist under several types.
- Store the type with the name in widgets and shortcuts.

### Launching
- Android has no universal "connect" action. Map each type to a URI scheme and fire `ACTION_VIEW` (`http://ip:port`, `ssh://ip:port`).
- This needs a handler app installed (for example ConnectBot for `ssh://`).
- If nothing can handle the link, or the type is unknown, copy `ip:port` to the clipboard and tell the user.

### Per-service override
- Optional TXT key `sl-url`, for example `ssh://pi@{host}:{port}` or `http://{host}:{port}/ui`.
- `{host}` and `{port}` are filled in at tap time. This allows a username or path to be set.

### Open question
- Which SSH app is installed on the phone? That decides whether `ssh://` works.

## More robust service identification

Currently, a widget or shortcut matches a service by name only. mDNS resolves name conflicts by renaming the second device (for example "WearWare UI #2"), but which device keeps the plain name depends on boot order. A shortcut could therefore end up pointing at the wrong device.

- Store the service type and the mDNS hostname (from the SRV record, for example `wearware.local`) along with the name. The hostname is only an identifier, never used to connect. `NsdServiceInfo.getHostname` needs API 34+.
- At tap time, match by name first. If the match is ambiguous, or the name carries a conflict suffix, pick by hostname.
- Compatibility: API 34 is Android 14 (October 2023). Below API 34 the app uses the deprecated `resolveService`, where the hostname may not be available, so matching would fall back to name only.
