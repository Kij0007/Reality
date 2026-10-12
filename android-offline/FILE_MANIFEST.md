# Reality Offline file manifest

This is the independently openable Android project in android-offline. **96 source/configuration/resource/document files** are listed below, including this manifest and the binary Gradle wrapper JAR. The source ZIP is delivered as Reality-Offline.zip; the installable artifact is Reality-Offline-debug.apk.

Open the extracted directory containing settings.gradle.kts in Android Studio. None of these files belongs under Spring Boot src/main/resources/static. Local build/cache directories, local.properties, private keys/passwords and personal backup JSON files are excluded. The generated Room version 1 schema is included so future database migrations can be reviewed. Build outputs and caches are excluded.

```text
android-offline/.gitattributes
android-offline/.gitignore
android-offline/app/build.gradle.kts
android-offline/app/proguard-rules.pro
android-offline/app/schemas/com.reality.android.data.local.RealityDatabase/1.json
android-offline/app/src/androidTest/java/com/reality/android/data/backup/BackupRepositoryTest.kt
android-offline/app/src/androidTest/java/com/reality/android/data/local/LocalRealityStoreTest.kt
android-offline/app/src/androidTest/java/com/reality/android/ui/StartupNavigationTest.kt
android-offline/app/src/androidTest/java/com/reality/android/ui/tracking/SessionActionsTest.kt
android-offline/app/src/main/AndroidManifest.xml
android-offline/app/src/main/java/com/reality/android/core/network/ApiResult.kt
android-offline/app/src/main/java/com/reality/android/core/network/MutationGate.kt
android-offline/app/src/main/java/com/reality/android/core/util/ActivityValidation.kt
android-offline/app/src/main/java/com/reality/android/core/util/RealityBreakTime.kt
android-offline/app/src/main/java/com/reality/android/core/util/RealityTime.kt
android-offline/app/src/main/java/com/reality/android/data/backup/BackupRepository.kt
android-offline/app/src/main/java/com/reality/android/data/backup/RealityBackup.kt
android-offline/app/src/main/java/com/reality/android/data/local/LocalClock.kt
android-offline/app/src/main/java/com/reality/android/data/local/LocalEntities.kt
android-offline/app/src/main/java/com/reality/android/data/local/LocalRealityStore.kt
android-offline/app/src/main/java/com/reality/android/data/local/RealityCalculations.kt
android-offline/app/src/main/java/com/reality/android/data/local/RealityDatabase.kt
android-offline/app/src/main/java/com/reality/android/data/remote/dto/RealityDtos.kt
android-offline/app/src/main/java/com/reality/android/data/repository/RealityRepositories.kt
android-offline/app/src/main/java/com/reality/android/data/repository/SettingsReadRecovery.kt
android-offline/app/src/main/java/com/reality/android/data/repository/SettingsRepository.kt
android-offline/app/src/main/java/com/reality/android/di/StorageModule.kt
android-offline/app/src/main/java/com/reality/android/MainActivity.kt
android-offline/app/src/main/java/com/reality/android/RealityApplication.kt
android-offline/app/src/main/java/com/reality/android/ui/activities/ActivityScreens.kt
android-offline/app/src/main/java/com/reality/android/ui/activities/ActivityViewModels.kt
android-offline/app/src/main/java/com/reality/android/ui/activities/WorkflowComponents.kt
android-offline/app/src/main/java/com/reality/android/ui/backup/BackupScreen.kt
android-offline/app/src/main/java/com/reality/android/ui/backup/BackupViewModel.kt
android-offline/app/src/main/java/com/reality/android/ui/dashboard/DashboardScreen.kt
android-offline/app/src/main/java/com/reality/android/ui/dashboard/DashboardViewModel.kt
android-offline/app/src/main/java/com/reality/android/ui/navigation/NavigationViewModel.kt
android-offline/app/src/main/java/com/reality/android/ui/navigation/RealityNavigation.kt
android-offline/app/src/main/java/com/reality/android/ui/navigation/Routes.kt
android-offline/app/src/main/java/com/reality/android/ui/progress/InsightsComponents.kt
android-offline/app/src/main/java/com/reality/android/ui/progress/ProgressScreen.kt
android-offline/app/src/main/java/com/reality/android/ui/progress/ProgressViewModel.kt
android-offline/app/src/main/java/com/reality/android/ui/reports/ReportsScreen.kt
android-offline/app/src/main/java/com/reality/android/ui/reports/ReportsViewModel.kt
android-offline/app/src/main/java/com/reality/android/ui/schedule/ScheduleScreen.kt
android-offline/app/src/main/java/com/reality/android/ui/schedule/ScheduleViewModel.kt
android-offline/app/src/main/java/com/reality/android/ui/settings/SettingsScreen.kt
android-offline/app/src/main/java/com/reality/android/ui/settings/SettingsViewModel.kt
android-offline/app/src/main/java/com/reality/android/ui/theme/Theme.kt
android-offline/app/src/main/java/com/reality/android/ui/tracking/SessionDetailViewModel.kt
android-offline/app/src/main/java/com/reality/android/ui/tracking/TrackingModels.kt
android-offline/app/src/main/java/com/reality/android/ui/tracking/TrackingScreens.kt
android-offline/app/src/main/java/com/reality/android/ui/tracking/TrackingViewModel.kt
android-offline/app/src/main/res/drawable/ic_reality.xml
android-offline/app/src/main/res/mipmap-anydpi/ic_launcher_round.xml
android-offline/app/src/main/res/mipmap-anydpi/ic_launcher.xml
android-offline/app/src/main/res/values-night/themes.xml
android-offline/app/src/main/res/values/colors.xml
android-offline/app/src/main/res/values/strings_backup.xml
android-offline/app/src/main/res/values/strings_insights.xml
android-offline/app/src/main/res/values/strings_workflows.xml
android-offline/app/src/main/res/values/strings.xml
android-offline/app/src/main/res/values/themes.xml
android-offline/app/src/main/res/xml/data_extraction_rules.xml
android-offline/app/src/test/java/com/reality/android/core/RealityBreakTimeTest.kt
android-offline/app/src/test/java/com/reality/android/core/RealityRulesTest.kt
android-offline/app/src/test/java/com/reality/android/data/BackupCodecTest.kt
android-offline/app/src/test/java/com/reality/android/data/local/RealityCalculationsTest.kt
android-offline/app/src/test/java/com/reality/android/data/SettingsReadRecoveryTest.kt
android-offline/app/src/test/java/com/reality/android/ui/activities/ActivitiesUiStateTest.kt
android-offline/app/src/test/java/com/reality/android/ui/activities/ActivityDetailViewModelTest.kt
android-offline/app/src/test/java/com/reality/android/ui/activities/ActivityFormViewModelTest.kt
android-offline/app/src/test/java/com/reality/android/ui/dashboard/DashboardSummaryTest.kt
android-offline/app/src/test/java/com/reality/android/ui/dashboard/DashboardViewModelTest.kt
android-offline/app/src/test/java/com/reality/android/ui/navigation/RoutesTest.kt
android-offline/app/src/test/java/com/reality/android/ui/progress/ProgressViewModelTest.kt
android-offline/app/src/test/java/com/reality/android/ui/reports/ReportsViewModelTest.kt
android-offline/app/src/test/java/com/reality/android/ui/tracking/SessionDetailViewModelTest.kt
android-offline/app/src/test/java/com/reality/android/ui/tracking/SessionStatusTest.kt
android-offline/app/src/test/java/com/reality/android/ui/tracking/TrackingViewModelTest.kt
android-offline/BACKEND_INTEGRATION.md
android-offline/build.gradle.kts
android-offline/FEATURE_PARITY.md
android-offline/FILE_MANIFEST.md
android-offline/gradle.properties
android-offline/gradle/libs.versions.toml
android-offline/gradle/wrapper/gradle-wrapper.jar
android-offline/gradle/wrapper/gradle-wrapper.properties
android-offline/gradlew
android-offline/gradlew.bat
android-offline/README.md
android-offline/scripts/verify-emulator.sh
android-offline/settings.gradle.kts
android-offline/SETUP.md
android-offline/TROUBLESHOOTING.md
android-offline/VERIFICATION.md
```

## Separate PC migration files

The complete repository supplies these files under scripts/:

- Export-Reality-Localhost.ps1 — primary PowerShell 5.1/psql export.
- reality-export.sql — shared read-only full-record/streak query; keep beside the exporter.
- export-localhost.py — optional Python 3.10+ fallback.
- requirements-export.txt — pinned optional pg8000 dependency.
- test_export_localhost.py — exporter tests.

The private existing-data file is supplied separately as Reality-localhost-backup-2026-10-13.json. It is deliberately absent from this manifest, the public repository and the Android source/APK; import it using the native file picker.


