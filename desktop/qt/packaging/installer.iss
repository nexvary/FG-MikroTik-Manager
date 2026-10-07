[Setup]
AppId={{AC429F24-4B12-449C-BB01-A2EBBC9614F8}
AppName=FG MTM
AppVersion=0.17.4
AppPublisher=FG Machines
AppPublisherURL=https://fgmachines.org
DefaultDirName={localappdata}\Programs\FG Machines\FG MTM Qt
DefaultGroupName=FG Machines
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=../../../qt-installer
OutputBaseFilename=FG-MTM-Windows-0.17.4-Setup
SetupIconFile=fg-mtm.ico
UninstallDisplayIcon={app}\fg_mtm.exe
InfoBeforeFile=about.rtf
WizardImageFile=wizard.bmp
WizardSmallImageFile=wizard-small.bmp
WizardStyle=modern
Compression=lzma2
SolidCompression=yes
[Files]
Source: "../../../qt-package\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
[Icons]
Name: "{group}\FG MTM"; Filename: "{app}\fg_mtm.exe"
Name: "{userdesktop}\FG MTM"; Filename: "{app}\fg_mtm.exe"; Tasks: desktopicon
[Tasks]
Name: desktopicon; Description: "Create a desktop shortcut"; Flags: unchecked
Name: firewall; Description: "Allow MikroTik discovery on Private/Domain networks"
[Run]
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""FG MTM MNDP Discovery"""; Tasks: firewall; Flags: runhidden
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall add rule name=""FG MTM MNDP Discovery"" dir=in action=allow program=""{app}\fg_mtm.exe"" protocol=UDP localport=5678 profile=private,domain enable=yes"; Tasks: firewall; Flags: runhidden
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""FG MTM MNDP Discovery Out"""; Tasks: firewall; Flags: runhidden
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall add rule name=""FG MTM MNDP Discovery Out"" dir=out action=allow program=""{app}\fg_mtm.exe"" protocol=UDP remoteport=5678 profile=private,domain enable=yes"; Tasks: firewall; Flags: runhidden
Filename: "{app}\fg_mtm.exe"; Description: "Launch FG MTM"; Flags: nowait postinstall skipifsilent
[UninstallRun]
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""FG MTM MNDP Discovery"""; Flags: runhidden
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""FG MTM MNDP Discovery Out"""; Flags: runhidden
