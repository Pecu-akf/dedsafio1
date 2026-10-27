# DEDsafío (Fabric 1.21.1) - etapa 1

Requisitos: Java 21 y conexión a internet (la primera vez baja Minecraft y Fabric).

Compilar:
    Linux/Mac:  ./gradlew build
    Windows:    gradlew.bat build

El mod queda en build/libs/dedsafio-0.1.0.jar (no uses el -sources.jar).
Ponlo en la carpeta mods junto con Fabric API 1.21.1.

Probar sin instalar nada: ./gradlew runClient  (o runServer)

Comandos (solo operadores):
    /dedsafio iniciar | pausar | reanudar | finalizar
    /dedsafio dia | dia fijar <n> | dia sumar <n> | dia restar <n>
    /dedsafio ruleta | evento <id> | cancelar | estado | historial | recargar

Configuración: config/dedsafio.json (se crea sola al abrir el juego).
