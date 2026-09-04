@echo off
REM ============================================
REM Скрипт сборки и запуска МедАссистент
REM ============================================

setlocal

REM Путь к репозиторию Maven (без русских символов)
set MAVEN_REPO_LOCAL=%~dp0.m2\repository

REM Создаём директорию репозитория
if not exist "%MAVEN_REPO_LOCAL%" (
    echo Creating Maven repository directory...
    mkdir "%MAVEN_REPO_LOCAL%"
)

echo ============================================
echo МедАссистент - Сборка и запуск
echo ============================================
echo.
echo Maven repository: %MAVEN_REPO_LOCAL%
echo.

REM Проверяем наличие Java
echo Checking Java version...
java -version
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Java не найдена!
    echo Установите Java 17 или новее.
    echo Download: https://adoptium.net/
    pause
    exit /b 1
)

echo [OK] Java найдена
echo.

REM Проверяем наличие Maven
echo Checking Maven...
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Maven не найден в PATH!
    echo Установите Maven: https://maven.apache.org/download.cgi
    echo Или скачайте Maven и добавьте в PATH
    pause
    exit /b 1
)

echo [OK] Maven найден
mvn --version
echo.

REM Очистка и компиляция
echo ============================================
echo Шаг 1: Очистка и компиляция
echo ============================================
call mvn -Dmaven.repo.local="%MAVEN_REPO_LOCAL%" -s settings.xml clean compile

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Компиляция не удалась!
    echo Проверьте версию Java (требуется 17+) и наличие интернета.
    pause
    exit /b 1
)

echo.
echo [OK] Компиляция успешна
echo.

REM Запуск приложения
echo ============================================
echo Шаг 2: Запуск приложения
echo ============================================
echo.
echo Приложение будет доступно:
echo   - Пациент: http://localhost:8080/patient/start
echo   - Врач: http://localhost:8080/doctor
echo   - H2 Console: http://localhost:8080/h2-console
echo.
echo Для остановки нажмите Ctrl+C
echo.

call mvn -Dmaven.repo.local="%MAVEN_REPO_LOCAL%" -s settings.xml spring-boot:run

pause
