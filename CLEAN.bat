@echo off
REM ============================================
REM Полная очистка и пересборка проекта
REM ============================================

setlocal

REM Путь к репозиторию Maven
set MAVEN_REPO_LOCAL=%~dp0.m2\repository

echo ============================================
echo Очистка проекта МедАссистент
echo ============================================
echo.

REM 1. Очищаем кэш неудачных загрузок Netty
echo [1/4] Очистка кэша Netty...
if exist "%MAVEN_REPO_LOCAL%\io\netty" (
    rmdir /s /q "%MAVEN_REPO_LOCAL%\io\netty"
    echo   Удалено: io\netty
)

REM 2. Очищаем кэш Spring Boot
echo [2/4] Очистка кэша Spring Boot...
if exist "%MAVEN_REPO_LOCAL%\org\springframework\boot" (
    rmdir /s /q "%MAVEN_REPO_LOCAL%\org\springframework\boot"
    echo   Удалено: org\springframework\boot
)

REM 3. Очищаем target проекта
echo [3/4] Очистка target...
if exist "%~dp0target" (
    rmdir /s /q "%~dp0target"
    echo   Удалено: target
)

REM 4. Очищаем .lastUpdated файлы (флаги неудачных загрузок)
echo [4/4] Очистка .lastUpdated файлов...
for /r "%MAVEN_REPO_LOCAL%" %%f in (*.lastUpdated) do (
    del "%%f" 2>nul
)
echo   .lastUpdated файлы удалены

echo.
echo ============================================
echo Очистка завершена!
echo ============================================
echo.
echo Теперь запустите сборку:
echo   BUILD.bat
echo.

pause
