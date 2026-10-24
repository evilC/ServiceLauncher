# Service Launcher

An app to discover and launch services on a local network (e.g., mobile hotspot) using mDNS/DNS-SD. It allows users to add widgets to their home screen for one-click access to discovered services.

Allows creation of Widgets and Shortcuts for discovered services, with the ability to customize the display text for each widget/shortcut.


## Custom fields
The default text and labels for widgets and shortcuts can be controlled by the device by using custom `txt-record` entries in the mDNS service advertisement.  

## Shortcuts
| txt-record key | Purpose                       |
|----------------|-------------------------------|
| sl-stext       | Text on shortcut icon         |
| sl-slabel      | Label text below the shortcut |

eg
```
<txt-record>sl-stext=UI</txt-record>
<txt-record>sl-slabel=WearWare</txt-record>
```
Shortcut defaults to having the text `UI` on the icon and `WearWare` below the icon.

## Widgets
| txt-record key | Purpose                       |
|----------------|-------------------------------|
| sl-wtext       | Text on widget icon         |

eg  
```
<txt-record>sl-wtext=WearWare UI</txt-record>
```
Widget defaults to having the text `WearWare UI` 
