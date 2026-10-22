// WiiOS for computers: the channel menu (web/, shared look and sounds with the TV version) in a full-screen
// window, with the computer's games as channels and updates from the project's GitHub releases.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod channels;
mod system;
mod update;

use serde::Serialize;

#[derive(Serialize)]
struct Info {
    build: u32,
    version: &'static str,
    os: &'static str,
    autostart: bool,
}

/// Runs blocking work (disk, network, starting programs) off the window's thread.
async fn blocking<T: Send + 'static>(f: impl FnOnce() -> Result<T, String> + Send + 'static) -> Result<T, String> {
    tauri::async_runtime::spawn_blocking(f).await.map_err(|e| e.to_string())?
}

#[tauri::command]
fn info() -> Info {
    Info { build: update::build(), version: env!("WIIOS_VERSION"), os: std::env::consts::OS, autostart: system::autostart() }
}

#[tauri::command]
async fn channels() -> Result<Vec<channels::Channel>, String> {
    blocking(|| Ok(channels::all())).await
}

#[tauri::command]
async fn launch(target: String, args: Option<Vec<String>>) -> Result<(), String> {
    blocking(move || {
        let args = args.unwrap_or_default();
        let path = std::path::Path::new(&target);
        if !args.is_empty() || (path.is_file() && path.extension().is_some_and(|e| e == "exe")) {
            let mut cmd = std::process::Command::new(path);
            cmd.args(&args);
            if let Some(dir) = path.parent() {
                cmd.current_dir(dir);
            }
            return cmd.spawn().map(|_| ()).map_err(|e| e.to_string());
        }
        open::that_detached(&target).map_err(|e| e.to_string())
    })
    .await
}

#[tauri::command]
async fn open_settings(page: String) -> Result<(), String> {
    blocking(move || system::open_settings(&page)).await
}

#[tauri::command]
async fn open_files() -> Result<(), String> {
    blocking(system::open_files).await
}

#[tauri::command]
async fn open_channels_file() -> Result<(), String> {
    blocking(|| open::that_detached(channels::channels_file()).map_err(|e| e.to_string())).await
}

#[tauri::command]
async fn power(app: tauri::AppHandle, action: String) -> Result<(), String> {
    if action == "quit" {
        app.exit(0);
        return Ok(());
    }
    blocking(move || system::power(&action)).await
}

#[tauri::command]
async fn set_autostart(on: bool) -> Result<(), String> {
    blocking(move || system::set_autostart(on)).await
}

#[tauri::command]
async fn check_update() -> Result<Option<update::Update>, String> {
    blocking(update::check).await
}

#[tauri::command]
async fn apply_update(app: tauri::AppHandle, url: String, sha256: String) -> Result<(), String> {
    blocking(move || update::apply(&url, &sha256)).await?;
    app.exit(0);
    Ok(())
}

fn main() {
    update::cleanup();
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            info,
            channels,
            launch,
            open_settings,
            open_files,
            open_channels_file,
            power,
            set_autostart,
            check_update,
            apply_update
        ])
        .run(tauri::generate_context!())
        .expect("WiiOS could not start");
}
