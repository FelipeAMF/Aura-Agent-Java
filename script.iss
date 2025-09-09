[Setup]
AppName=Aura Agent
AppVersion=1.0
DefaultDirName={userappdata}\Aura Agent
DefaultGroupName=Aura Agent
UninstallDisplayIcon={app}\resources\images\logo_aura.ico
Compression=lzma
SolidCompression=yes
OutputDir=C:\InstaladorAuraAgent
SetupIconFile=C:\Users\felip\OneDrive\Documentos\JAVA\auraagent\src\main\resources\images\logo_aura.ico

[Files]
; Copia o JAR autossuficiente (uber-jar)
Source: "C:\Users\felip\OneDrive\Documentos\JAVA\auraagent\target\auraagent-1.0-SNAPSHOT-jar-with-dependencies.jar"; DestDir: "{app}\target"; Flags: ignoreversion
; Copia a pasta 'servidor-node' e seus conteudos
Source: "C:\Users\felip\OneDrive\Documentos\JAVA\auraagent\servidor-node\*"; DestDir: "{app}\servidor-node"; Flags: ignoreversion recursesubdirs createallsubdirs
; Copia a pasta 'vendor' e seus conteudos
; Copia a pasta 'vendor' e seus conteudos
Source: "C:\Users\felip\OneDrive\Documentos\JAVA\auraagent\vendor\*"; DestDir: "{app}\vendor"; Flags: ignoreversion recursesubdirs createallsubdirs
; Copia a pasta 'node' portatil
Source: "C:\Users\felip\OneDrive\Documentos\JAVA\auraagent\vendor\node\*"; DestDir: "{app}\vendor\node"; Flags: ignoreversion recursesubdirs createallsubdirs
; Copia a pasta 'model' e seus conteudos
Source: "C:\Users\felip\OneDrive\Documentos\JAVA\auraagent\model\*"; DestDir: "{app}\model"; Flags: ignoreversion recursesubdirs createallsubdirs
; Copia os recursos da aplicacao
Source: "C:\Users\felip\OneDrive\Documentos\JAVA\auraagent\src\main\resources\*"; DestDir: "{app}\resources"; Flags: ignoreversion recursesubdirs createallsubdirs
; Copia o arquivo .bat para a raiz da instalacao
Source: "C:\Users\felip\OneDrive\Documentos\JAVA\auraagent\launcher.bat"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
; O atalho agora aponta para o arquivo .bat
Name: "{group}\Aura Agent"; Filename: "{app}\launcher.bat"; IconFilename: "{app}\resources\images\logo_aura.ico"
Name: "{autodesktop}\Aura Agent"; Filename: "{app}\launcher.bat"; IconFilename: "{app}\resources\images\logo_aura.ico"

[Run]
; O comando run agora aponta para o arquivo .bat
Filename: "{app}\launcher.bat"; StatusMsg: "Iniciando Aura Agent..."; Flags: nowait postinstall shellexec