# Service Launcher

## What is Service Launcher?
An app to discover and launch services on a local network (e.g. mobile hotspot) using mDNS/DNS-SD.

Allows creation of Shortcuts and Widgets for discovered services, with the ability to customize the display text for each shortcut / widget.  .  
When adding a shortcut / widget, the app will scan the local network for available services and present them in a list for selection.  
The shortcut / widget stores the service name, but not the IP address or port. 
The IP and port are resolved at the point that you click the shortcut / widget, so that it always connects to the correct address even if the service's IP changes.

## Why does this exist?
I designed and built my own LED screen T-Shirt (See the [WearWare](https://github.com/evilC/WearWare) project) which I wear to nightclubs.    
This is a Raspberry Pi running a web server that serves a web-based UI for controlling the shirt.  
Historically, to control the shirt, I would connect my phone to the Pi's WiFi hotspot, and click a shortcut on my home screen which pointed to it's DNS name (e.g. `http://wearware.local:5000`) - in this case it's the Pi that is handling DNS resolution, not the phone.    
However, when I connected my phone to the pi's hotspot, it would lose connection to the internet (It would try to route all data via the pi) - 
Not what you want when you are trying to WhatsApp your mates to find out where they are...   
If I connected the pi to my phone's hotspot, I would have to somehow find the pi's IP address, because Android cannot resolve `.local` hostnames.  
[Service Browser](https://play.google.com/store/apps/details?id=com.druk.servicebrowser) can do this, and purports to allow you to connect to a service at a click of a button, but the button does not show for me, so I had to fall back to copy/paste.
Even if the button did appear, I would need to open the app, navigate the UI, and then click the button - 
Yeah... sod that for a game of soldiers when you are wasted in a dark and loud nightclub.  
So... I made this app. I set up DNS-SD on the pi to advertise a "WearWare UI" service announcing that it is HTTP on port 5000, and I can add a shortcut icon to my phone's home screen that connects to the pi's web server with a single tap, without needing to know the IP address.

## Disclaimer
This app is 100% vibe coded. I did not write a single line of the code myself.

### DNS-SD custom fields
The default text and labels for widgets and shortcuts can be controlled by the device by using custom `txt-record` entries in the DNS-SD service advertisement.  

#### Shortcuts
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

#### Widgets
| txt-record key | Purpose                       |
|----------------|-------------------------------|
| sl-wtext       | Text on widget icon         |

eg  
```
<txt-record>sl-wtext=WearWare UI</txt-record>
```
Widget defaults to having the text `WearWare UI` 
