[Setup]
AppId={{B8CC0898-5B7F-443D-BCF5-E2D4EAB292D5}
AppName=FG MTM Desktop
AppVersion=0.2.0
AppPublisher=FG Machines
DefaultDirName={localappdata}\Programs\FG Machines\FG MTM
DefaultGroupName=FG Machines
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=installer-output
OutputBaseFilename=FG-MTM-Windows-0.2.0-Setup
SetupIconFile=FG.MTM.Desktop\Assets\fg-machines.ico
UninstallDisplayIcon={app}\FG.MTM.Desktop.exe
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes

[Files]
Source: "publish\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\FG MTM"; Filename: "{app}\FG.MTM.Desktop.exe"
Name: "{userdesktop}\FG MTM"; Filename: "{app}\FG.MTM.Desktop.exe"; Tasks: desktopicon

[Tasks]
Name: desktopicon; Description: "Create a desktop shortcut"; Flags: unchecked

[Run]
Filename: "{app}\FG.MTM.Desktop.exe"; Description: "Launch FG MTM"; Flags: nowait postinstall skipifsilent
