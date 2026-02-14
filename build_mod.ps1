$gradleVersion = "8.5"
$gradleUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
$gradleZip = "gradle.zip"
$gradleDir = "gradle-dist"

Write-Host "Downloading Gradle $gradleVersion..."
Invoke-WebRequest -Uri $gradleUrl -OutFile $gradleZip

Write-Host "Extracting Gradle (this may take a moment)..."
Expand-Archive -Path $gradleZip -DestinationPath $gradleDir -Force

$gradleBin = "$PWD\$gradleDir\gradle-$gradleVersion\bin\gradle.bat"

if (Test-Path $gradleBin) {
    Write-Host "Starting Build..."
    & $gradleBin build
    Write-Host "Build finished."
} else {
    Write-Error "Could not find gradle.bat at $gradleBin"
}
