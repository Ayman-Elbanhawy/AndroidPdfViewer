$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

./gradlew.bat :app:assembleDevDebug :app:assembleDevDebugAndroidTest
./gradlew.bat :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.aymanelbanhawy.enterprisepdf.app.smoke.CriticalWorkflowSmokeTest
./gradlew.bat :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.aymanelbanhawy.enterprisepdf.app.diagnostics.LargeDocumentOpenBenchmarkTest
