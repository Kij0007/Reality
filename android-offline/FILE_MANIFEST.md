# Reality Offline file manifest

This is the independently openable Android project in android-offline. All source/configuration/resources and the Gradle wrapper belong in this directory; none belongs under Spring Boot src/main/resources/static.

The initial implementation is being updated from the native online client. The exact final file list/count will be regenerated after Room, backup, offline settings and tests are integrated. The final ZIP should exclude local.properties, .gradle, build outputs, private keystores and credentials.

Top-level deliverables:

- README.md
- SETUP.md
- BACKEND_INTEGRATION.md
- FEATURE_PARITY.md
- TROUBLESHOOTING.md
- VERIFICATION.md
- FILE_MANIFEST.md
- settings.gradle.kts, build.gradle.kts, gradle.properties
- gradlew, gradlew.bat, gradle/wrapper and gradle/libs.versions.toml
- app/build.gradle.kts, proguard-rules.pro, AndroidManifest.xml
- app/src/main Kotlin, Compose screens, Room/local repositories, backup and resources
- app/src/test meaningful local/validation/backup calculation tests
- app/src/androidTest Room/Compose/persistence tests

The one-time PC exporter is supplied separately from Android. It runs on the PC, exports records to JSON, and does not embed PostgreSQL credentials in the application.

