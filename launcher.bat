@echo on
java -Dprism.order=sw -jar "%~dp0target\auraagent-1.0-SNAPSHOT-jar-with-dependencies.jar"
ECHO.
ECHO Fim da execucao. Se houve um erro, ele foi exibido acima.
ECHO Pressione qualquer tecla para fechar...
PAUSE
