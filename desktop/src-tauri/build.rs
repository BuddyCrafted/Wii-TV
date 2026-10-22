fn main() {
    // CI passes the run number (the same build-N the Android APK gets), so every version shares one number.
    println!("cargo:rerun-if-env-changed=WIIOS_BUILD");
    tauri_build::build()
}
