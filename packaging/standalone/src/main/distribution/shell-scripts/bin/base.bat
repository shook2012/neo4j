@echo off
rem Copyright (c) 2002-2015 "Neo Technology,"
rem Network Engine for Objects in Lund AB [http://neotechnology.com]
rem
rem This file is part of Neo4j.
rem
rem Neo4j is free software: you can redistribute it and/or modify
rem it under the terms of the GNU General Public License as published by
rem the Free Software Foundation, either version 3 of the License, or
rem (at your option) any later version.
rem
rem This program is distributed in the hope that it will be useful,
rem but WITHOUT ANY WARRANTY; without even the implied warranty of
rem MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
rem GNU General Public License for more details.
rem
rem You should have received a copy of the GNU General Public License
rem along with this program.  If not, see <http://www.gnu.org/licenses/>.

setlocal ENABLEEXTENSIONS

call:main %1

goto:eof

:main
  set settingError=""
  call:checkSettings
  if not %settingError% == "" (
    echo %settingError% variable is not set.
    call:instructions
    goto:eof
  )

  call "%~dps0functions.bat" :findJavaHome
  if not "%javaHomeError%" == "" (
    echo %javaHomeError%
    call:instructions
    goto:eof
  )

  call:verifySupportedJavaVersion
  if not "%javaVersionError%" == "" (
    echo %javaVersionError%
    call:instructions
    goto:eof
  )

  rem Check classpath

  rem Unescape javaPath
  for /f "tokens=* delims=" %%P in (%javaPath%) do (
      set javaPath=%%P
  )

  set wrapperJarFilename=#{windows-wrapper.filename}
  set command=""

  for /F %%v in ('echo %1^|findstr "^help$ ^console$"') do set command=%%v

  if %command% == "" (
      if not "%1" == "" (
             echo This command is not supported by the Neo4j utility. Please try "Neo4j.bat help" for more info.
             goto:eof
      )
      set command=console
  )

  goto:%command%
  if errorlevel 1 goto:callerror
  goto:eof


:verifySupportedJavaVersion

  set javaVersionError=
  set javaCommand=%javaPath:"=%\bin\java.exe

  rem Remove leading spaces
  for /f "tokens=* delims= " %%a in ("%javaCommand%") do set javaCommand=%%a

  rem Find version
  for /f "usebackq tokens=3" %%g in (`"%javaCommand%" -version 2^>^&1`) do (
      set JAVAVER=%%g
      goto:breakJavaVersionLoop
  )
  :breakJavaVersionLoop

  set JAVAVER=%JAVAVER:"=%
  set "JAVAVER=%JAVAVER:~0,3%"

  if "%JAVAVER%"=="1.8" goto:eof
  
  set javaVersionError=ERROR! You are using an unsupported version of Java, please use Oracle JDK 1.8.
  goto:eof

:checkSettings
  if %classpath% == "" (
    set settingError=classpath
    goto:eof
  )
  if %mainclass% == "" (
    set settingError=mainclass
    goto:eof
  )
  if %configFile% == "" (
    set settingError=configFile
    goto:eof
  )

  goto :eof

:callerror
  echo An error occurred in the process.
  pause
  goto :eof

:console
  "%javapath%\bin\java.exe" -DworkingDir="%~dps0.." -DconfigFile=%configFile% %classpath% %mainclass% -jar "%~dps0%wrapperJarFilename%"
  goto :eof

:help
  echo Proper arguments for this command are: help console
  goto :eof

:instructions
  echo * Please use Oracle(R) Java(TM) 8 to run Neo4j Server.
  echo * Download "Java Platform (JDK) 8" from:
  echo   http://www.oracle.com/technetwork/java/javase/downloads/index.html
  echo * Please see http://neo4j.com/docs/ for Neo4j Server installation instructions.
  goto:eof
