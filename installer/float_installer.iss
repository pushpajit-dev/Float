; Script generated for Float by NoishiXzen
; Uses Inno Setup 6 with custom Float branding

#define MyAppName "Float"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "NoishiXzen"
#define MyAppURL "https://aivorygen.netlify.app"
#define MyAppSupportURL "https://github.com/pushpajit-dev"
#define MyAppExeName "Float.exe"

[Setup]
AppId={{D37E6F81-9C4B-4E38-99A8-1E7C1C2F7A8D}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppSupportURL}
AppUpdatesURL={#MyAppSupportURL}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
LicenseFile=..\LICENSE
OutputDir=..\dist
OutputBaseFilename=Float-Setup-v{#MyAppVersion}
SetupIconFile=..\desktop\src-tauri\icons\icon.ico
WizardImageFile=wizard.bmp
WizardSmallImageFile=wizard-small.bmp
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesInstallIn64BitMode=x64compatible
DisableProgramGroupPage=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "autostart"; Description: "Start Float automatically when Windows starts"; GroupDescription: "Startup:"

[Files]
Source: "..\dist_app\Float.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\dist_app\meghxl-server-x86_64-pc-windows-msvc.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\dist_app\meghxl-server.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\dist_app\cloudflared.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\dist_app\resources\*"; DestDir: "{app}\resources"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\dist_app\icon.ico"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\NOTICE"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\LICENSE"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\README.md"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
; Start Menu
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\icon.ico"; Comment: "Float — Local Network File Transfer"
Name: "{group}\Uninstall {#MyAppName}"; Filename: "{uninstallexe}"

; Desktop Icon
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\icon.ico"; Tasks: desktopicon; Comment: "Float — Local Network File Transfer"

; Windows Startup (Autostart task)
Name: "{userstartup}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\icon.ico"; Tasks: autostart; Comment: "Float — Local Network File Transfer"

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[UninstallRun]
; Terminate running Float before uninstalling
Filename: "taskkill.exe"; Parameters: "/F /IM Float.exe /IM meghxl-server.exe /IM meghxl-server-x86_64-pc-windows-msvc.exe /IM cloudflared.exe"; Flags: runhidden; RunOnceId: "StopFloatOnUninstall"

[UninstallDelete]
Type: filesandordirs; Name: "{app}\uploads"
Type: files; Name: "{app}\metadata.json"
