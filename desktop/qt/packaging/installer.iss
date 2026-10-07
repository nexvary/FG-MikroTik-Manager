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

[Code]
var
  FeaturePage: TWizardPage;

procedure FeatureCard(Top: Integer; Heading, Detail: String; Accent: TColor);
var
  Panel: TPanel;
  Title, Body: TNewStaticText;
begin
  Panel := TPanel.Create(FeaturePage);
  Panel.Parent := FeaturePage.Surface;
  Panel.SetBounds(0, ScaleY(Top), FeaturePage.SurfaceWidth, ScaleY(60));
  Panel.Color := $291C10;
  Panel.BevelOuter := bvNone;
  Title := TNewStaticText.Create(FeaturePage);
  Title.Parent := Panel;
  Title.SetBounds(ScaleX(12), ScaleY(7), Panel.Width - ScaleX(24), ScaleY(20));
  Title.Font.Color := Accent;
  Title.Font.Style := [fsBold];
  Title.Font.Size := 10;
  Title.Caption := Heading;
  Body := TNewStaticText.Create(FeaturePage);
  Body.Parent := Panel;
  Body.SetBounds(ScaleX(12), ScaleY(29), Panel.Width - ScaleX(24), ScaleY(27));
  Body.Font.Color := $E5DED1;
  Body.WordWrap := True;
  Body.Caption := Detail;
end;

procedure InitializeWizard();
begin
  WizardForm.WelcomeLabel1.Caption := 'FG MTM 0.17.4';
  WizardForm.WelcomeLabel2.Caption := 'FG Machines' + #13#10 + #13#10 +
    'Your network, subscribers and business in one workspace.' + #13#10 + #13#10 +
    'Developer: Alaa Mohamed' + #13#10 +
    'Arabic / English interface. Independent software, not a MikroTik product.';
  FeaturePage := CreateCustomPage(wpWelcome, 'What does FG MTM offer?',
    'Available tools in this version - FG Machines');
  FeatureCard(0, 'NETWORK & HOTSPOT',
    'Router discovery, saved connections, HotSpot accounts, vouchers and QR codes.', $D2C166);
  FeatureCard(67, 'SUBSCRIBERS & BUSINESS',
    'Subscribers, plans, sales, payments, expenses, receipts and business reports.', $A5D98A);
  FeatureCard(134, 'ACCESS POINT OBSERVATIONS',
    'Multi-source discovery, shop names, sampled client comparison and PDF / XLSX / CSV.', $D2C166);
  FeatureCard(201, 'PROFESSIONAL TOOLS',
    'Floating terminal, command library, diagnostics, router monitoring and backups.', $83BEDD);
  WizardForm.FinishedLabel.Caption := 'FG MTM is ready.' + #13#10 + #13#10 +
    'Start by discovering your MikroTik and connecting to it.' + #13#10 +
    'Access point reports use local observations; unavailable metrics are marked N/A.' + #13#10 + #13#10 +
    'Uninstalling the application preserves your business database and saved profiles.';
end;
