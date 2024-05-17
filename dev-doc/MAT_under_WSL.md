# MAT under WSL

it is possible to test Linux builds on a Windows 10 (or 11) machine using Windows Subsystem for Linux.

## Windows Subsystem for Linux

Install WSL2 with for example Ubuntu or Ubuntu 20 

For Windows 10: Install Cygwin and X-Server

For Windows 11: X-Server is installed on WSL2 by default

For Windows 10: Install the appropriate graphics driver: [Microsoft WSL GUI apps](https://docs.microsoft.com/en-us/windows/wsl/tutorials/gui-apps)

For Windows 11: Graphics driver is installed on WSL2 by default

Install unzip

```sudo apt install unzip```

Install GTK:

```sudo apt-get install libswt-gtk-4-jni libswt-gtk-4-java```

Install WebKit:

[Eclipse instructions](https://www.eclipse.org/swt/faq.php#browserlinux)

```sudo apt-get install libwebkit2gtk-4.0-37```

Install Java 17 or later

```sudo apt install openjdk-17-jre-headless```

For Windows 10, start the X-server:
Find the IP address of the WSL2 system:

```ip addr | grep eth0```

From Cygwin64 command prompt. xhost should have the IP address of the WSL2 system as seen from Windows / Cygwin

```
startxwin -- -listen tcp
xhost +127.0.0.1 #Add the appropriate IP address, need to check for WSL2
# xhost +172.22.46.35 # or use this for WSL2, replace the address with the address from ip addr above
# xhost +$(wsl hostname -I) # or use this from a Cygwin xterm window to automatically find the WSL2 address
```

Download Memory Analyzer zip

```unzip MemoryAnalyzer-1.12.0.20210602-linux.gtk.x86_64.zip```

For WSL1

```
cd mat
export DISPLAY=:0
./MemoryAnalyzer
```

or for WSL2, Windows 10

```
cd mat
export DISPLAY=$(ip route | grep default | cut -d ' ' -f 3)':0' # Finds the IP address of the Windows machine
./MemoryAnalyzer
```

or for WSL2, Windows 11

```./MemoryAnalyzer```

## Problems

Problem: Failed to load swt-pi3

```
./MemoryAnalyzer
SWT OS.java Error: Failed to load swt-pi3, loading swt-pi4 as fallback.
MemoryAnalyzer:
An error has occurred. See the log file
/home/user1/mat/configuration/1689022953567.log.
```

Solution:

Install GTK4

Problem: Failed to create a browser

```
Failed to create a browser because of: No more handles because there is no underlying browser available.
Please ensure that WebKit with its GTK 3.x/4.x bindings is installed.
Consult error log for more details.
Press F1 or the help icon for help.
```

Solution:

Install WebKit

## Charts

To get charts working, add `-Djava.awt.headless=true` to `MemoryAnalyzer.ini` in the vmargs section.

### WebKit problems - e.g. Ubuntu 22.04

If you get errors such as the following, and blank pages for reports or help:

```
(WebKitWebProcess:22883): Gdk-ERROR **: 12:42:02.853: The program 'WebKitWebProcess' received an X Window System error.
This probably reflects a bug in the program.
The error was 'GLXBadFBConfig'.
  (Details: serial 148 error_code 161 request_code 148 (GLX) minor_code 21)
  (Note to programmers: normally, X errors are reported asynchronously;
   that is, you will receive the error a while after causing it.
   To debug your program, run it with the GDK_SYNCHRONIZE environment
   variable to change this behavior. You can then get a meaningful
   backtrace from your debugger if you break on the gdk_x_error() function.)
```

try starting Memory Analyzer like this
```
WEBKIT_DISABLE_COMPOSITING_MODE=1 ./MemoryAnalyzer
```

See [https://bugs.launchpad.net/ubuntu/+source/evolution/+bug/1966418] for details.