fn main() {
    // CI passes the run number (the same build-N the Android APK gets), so every version shares one number.
    println!("cargo:rerun-if-env-changed=WIIOS_BUILD");
    // The version people see (1.0, 1.1, 2.0...), from the VERSION file every WiiOS version shares.
    let version = std::fs::read_to_string("../../VERSION").unwrap_or_else(|_| "0.0".into());
    println!("cargo:rerun-if-changed=../../VERSION");
    println!("cargo:rustc-env=WIIOS_VERSION={}", version.trim());
    tauri_build::build()
}
