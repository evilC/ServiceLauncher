# ToDo list

## Support for other protocols (SSH, FTP etc)

## More robust service identification
Currently, a widget or shortcut matches a service by name only. mDNS resolves name conflicts by renaming the second device (for example "WearWare UI #2"), but which device keeps the plain name depends on boot order. A shortcut could therefore end up pointing at the wrong device.

- Store the service type and the mDNS hostname (from the SRV record, for example `wearware.local`) along with the name. The hostname is only an identifier, never used to connect. `NsdServiceInfo.getHostname` needs API 34+.
- At tap time, match by name first. If the match is ambiguous, or the name carries a conflict suffix, pick by hostname.
- Compatibility: API 34 is Android 14 (October 2023). Below API 34 the app uses the deprecated `resolveService`, where the hostname may not be available, so matching would fall back to name only.

# IPV4 / IPV6 selection
- If a service advertises both IPv4 and IPv6, the app should allow the user to select which one to use. The default should be IPv4, but the user can change it
