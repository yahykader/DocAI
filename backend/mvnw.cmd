@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM   https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.

@REM -------------------------------------------------------------------------
@REM Apache Maven Wrapper — Windows batch script, version 3.3.2
@REM -------------------------------------------------------------------------

@setlocal EnableExtensions EnableDelayedExpansion
@echo off

set WRAPPER_PROPS=%~dp0.mvn\wrapper\maven-wrapper.properties

if not exist "%WRAPPER_PROPS%" (
  echo ERROR: Maven wrapper config not found: %WRAPPER_PROPS%
  exit /b 1
)

for /F "tokens=2 delims==" %%G in ('findstr /B "distributionUrl=" "%WRAPPER_PROPS%"') do (
  set DISTRIBUTION_URL=%%G
)

for %%G in ("%DISTRIBUTION_URL%") do set ARCHIVE=%%~nxG
set MAVEN_VERSION=%ARCHIVE:-bin.zip=%

set M2_WRAPPER_DISTS=%USERPROFILE%\.m2\wrapper\dists
set MAVEN_HOME=%M2_WRAPPER_DISTS%\%MAVEN_VERSION%

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  echo Downloading %MAVEN_VERSION% ...
  if not exist "%MAVEN_HOME%" mkdir "%MAVEN_HOME%"
  powershell -Command "& { Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile '%TEMP%\mvn-dist.zip' -UseBasicParsing }"
  powershell -Command "& { Expand-Archive -Path '%TEMP%\mvn-dist.zip' -DestinationPath '%M2_WRAPPER_DISTS%' -Force }"
  del "%TEMP%\mvn-dist.zip"
)

"%MAVEN_HOME%\bin\mvn.cmd" %*
