# Definir as variáveis do projeto
$appName = "Aura Agent"
$appVersion = "1.0"
$appJar = "auraagent-1.0-SNAPSHOT.jar"
$mainClass = "com.auraagent.MainApplication"
$appIcon = "C:\Users\felip\OneDrive\Documentos\JAVA\auraagent\src\main\resources\images\logo_aura.ico"
$targetPath = "C:\Users\felip\OneDrive\Documentos\JAVA\auraagent\target"

# Caminho para o executável do WiX
# Mude isso se a sua versão do WiX for diferente
$wixPath = "${env:ProgramFiles(x86)}\WiX Toolset v3.11\bin" 

# Verificar se o WiX está no PATH
Write-Host "Verificando a instalação do WiX..."
if (-not (Test-Path "$wixPath\candle.exe")) {
    Write-Host "WiX Toolset não encontrado no caminho padrão. Por favor, verifique se está instalado e adicione-o ao PATH." -ForegroundColor Red
    Write-Host "Baixe em: https://wixtoolset.org" -ForegroundColor Yellow
    exit
}

# Adicionar o WiX Toolset ao PATH da sessão atual
$env:Path += ";$wixPath"
Write-Host "WiX Toolset encontrado. Iniciando a criação do instalador..." -ForegroundColor Green

# Comando jpackage para criar o instalador
jpackage --name "$appName" `
         --app-version "$appVersion" `
         --input "$targetPath" `
         --main-jar "$appJar" `
         --main-class "$mainClass" `
         --icon "$appIcon" `
         --type msi `
         --win-menu `
         --win-dir-chooser `
         --win-shortcut

Write-Host "Processo concluído." -ForegroundColor Green