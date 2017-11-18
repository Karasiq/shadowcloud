#define OutputName "shadowcloud-server"
#define MyAppName "shadowcloud"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "Karasiq, Inc."
#define MyAppURL "http://www.github.com/Karasiq/shadowcloud"
#define MyAppExeName "bin\shadowcloud-desktop.bat"
#define ProjectFolder "..\"

[Setup]
AppId={{0b1992b6-1fc7-4031-b3ed-89e5d384d05e}}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
;AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={pf}\{#MyAppName}
DefaultGroupName={#MyAppName}
OutputDir={#ProjectFolder}\target\iss
OutputBaseFilename={#OutputName}-{#MyAppVersion}
SetupIconFile={#ProjectFolder}\setup\icon.ico
Compression=lzma
SolidCompression=true
PrivilegesRequired=admin

[Languages]
Name: english; MessagesFile: compiler:Default.isl
Name: russian; MessagesFile: compiler:Languages\Russian.isl

[Tasks]
Name: desktopicon; Description: {cm:CreateDesktopIcon}; GroupDescription: {cm:AdditionalIcons}; Languages: 

[Files]
Source: {#ProjectFolder}\desktop-app\target\universal\stage\*; DestDir: {app}; Flags: ignoreversion recursesubdirs
Source: {#ProjectFolder}\setup\icon.ico; DestDir: {app}; Flags: ignoreversion
Source: {#ProjectFolder}\setup\shadowcloud_example.conf; DestDir: {app}

[Icons]
Name: {group}\{#MyAppName}; Filename: {app}\{#MyAppExeName}; IconFilename: {app}\favicon.ico; WorkingDir: {app}
Name: {commondesktop}\{#MyAppName}; Filename: {app}\{#MyAppExeName}; Tasks: desktopicon; IconFilename: {app}\icon.ico; WorkingDir: {app}

[Run]
Filename: {app}\{#MyAppExeName}; Description: {cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}; Flags: shellexec postinstall skipifsilent; WorkingDir: {app}; Check: IsJREInstalled

[Code]
#define MinJRE "1.8"
#define WebJRE "https://www.java.com/ru/download/manual.jsp"

function IsJREInstalled: Boolean;
var
  JREVersion: string;
begin
  // read JRE version
  Result := RegQueryStringValue(HKLM32, 'Software\JavaSoft\Java Runtime Environment',
    'CurrentVersion', JREVersion);
  // if the previous reading failed and we're on 64-bit Windows, try to read 
  // the JRE version from WOW node
  if not Result and IsWin64 then
    Result := RegQueryStringValue(HKLM64, 'Software\JavaSoft\Java Runtime Environment',
      'CurrentVersion', JREVersion);
  // if the JRE version was read, check if it's at least the minimum one
  if Result then
    Result := CompareStr(JREVersion, '{#MinJRE}') >= 0;
end;

function InitializeSetup: Boolean;
var
  ErrorCode: Integer;
begin
  Result := True;
  // check if JRE is installed; if not, then...
  if not IsJREInstalled then
  begin
    // show a message box and let user to choose if they want to download JRE;
    // if so, go to its download site and exit setup; continue otherwise
    if MsgBox('Java is required. Do you want to download it now ?',
      mbConfirmation, MB_YESNO) = IDYES then
    begin
      Result := False;
      ShellExec('', '{#WebJRE}', '', '', SW_SHOWNORMAL, ewNoWait, ErrorCode);
    end;
  end;
end;