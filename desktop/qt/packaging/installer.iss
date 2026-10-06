[Setup]
AppId={{AC429F24-4B12-449C-BB01-A2EBBC9614F8}
AppName=FG MTM Qt Desktop
AppVersion=0.3.0
AppPublisher=FG Machines
DefaultDirName={localappdata}\Programs\FG Machines\FG MTM Qt
DefaultGroupName=FG Machines
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=../../../qt-installer
OutputBaseFilename=FG-MTM-Qt-Windows-0.3.0-Setup
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
Name: "{group}\FG MTM Qt"; Filename: "{app}\fg_mtm.exe"
Name: "{userdesktop}\FG MTM Qt"; Filename: "{app}\fg_mtm.exe"; Tasks: desktopicon
[Tasks]
Name: desktopicon; Description: "Create a desktop shortcut"; Flags: unchecked
[Run]
Filename: "{app}\fg_mtm.exe"; Description: "Launch FG MTM"; Flags: nowait postinstall skipifsilent
