
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    jvm()
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(compose.materialIconsExtended)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test);
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.firebase.admin)
            implementation(libs.pdfbox)
            implementation(libs.openhtmltopdf.pdfbox)
            implementation("com.google.zxing:core:3.5.3")
            implementation("com.google.zxing:javase:3.5.3")
        }
    }
}


compose.desktop {
    application {
        mainClass = "com.example.dreamland_reception.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Dreamland"
            // Bump this on every release. MSI only replaces an existing install when the
            // version is higher — otherwise Windows keeps the old app (the "older version"
            // problem). Increment for each new build you ship.
            packageVersion = "1.0.1"

            windows {
                // Desktop shortcut after install.
                shortcut = true
                // Start-menu group.
                menuGroup = "Dreamland"
                // Stable upgrade code — MUST stay the same across releases so a new MSI
                // upgrades (replaces) the previous install instead of installing side-by-side.
                upgradeUuid = "b2e7c4a0-1f3d-4c9b-9a2e-7d6c5b4a3f21"
            }
        }
    }
}
