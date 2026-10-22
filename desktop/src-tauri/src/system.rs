//! The computer's own settings pages, files, power and "start with this computer", per operating system.

use std::path::PathBuf;
use std::process::Command;

fn run(cmd: &str, args: &[&str]) -> Result<(), String> {
    let mut c = Command::new(cmd);
    c.args(args);
    #[cfg(windows)]
    {
        // No console window flashing up over the menu.
        use std::os::windows::process::CommandExt;
        c.creation_flags(0x0800_0000);
    }
    c.spawn().map(|_| ()).map_err(|e| format!("{}: {}", cmd, e))
}

/// Opens a settings page: display, sound, network or bluetooth.
pub fn open_settings(page: &str) -> Result<(), String> {
    if cfg!(windows) {
        let uri = match page {
            "display" => "ms-settings:display",
            "sound" => "ms-settings:sound",
            "network" => "ms-settings:network-status",
            "bluetooth" => "ms-settings:bluetooth",
            _ => "ms-settings:",
        };
        return open::that_detached(uri).map_err(|e| e.to_string());
    }
    if cfg!(target_os = "macos") {
        let pane = match page {
            "display" => "com.apple.Displays-Settings.extension",
            "sound" => "com.apple.Sound-Settings.extension",
            "network" => "com.apple.Network-Settings.extension",
            _ => "com.apple.BluetoothSettings",
        };
        return open::that_detached(format!("x-apple.systempreferences:{}", pane)).map_err(|e| e.to_string());
    }
    // Linux: GNOME's settings, then KDE's, then whichever the desktop has.
    let (gnome, kde) = match page {
        "display" => ("display", "kcm_kscreen"),
        "sound" => ("sound", "kcm_pulseaudio"),
        "network" => ("network", "kcm_networkmanagement"),
        _ => ("bluetooth", "kcm_bluetooth"),
    };
    run("gnome-control-center", &[gnome])
        .or_else(|_| run("systemsettings", &[kde]))
        .or_else(|_| run("xfce4-settings-manager", &[]))
        .map_err(|_| "open your computer's settings app to change this".into())
}

pub fn open_files() -> Result<(), String> {
    let home = dirs::home_dir().ok_or("no home folder")?;
    open::that_detached(home).map_err(|e| e.to_string())
}

pub fn power(action: &str) -> Result<(), String> {
    if cfg!(windows) {
        match action {
            "sleep" => run("rundll32.exe", &["powrprof.dll,SetSuspendState", "0,1,0"]),
            "restart" => run("shutdown", &["/r", "/t", "0"]),
            "shutdown" => run("shutdown", &["/s", "/t", "0"]),
            _ => Err("unknown power action".into()),
        }
    } else if cfg!(target_os = "macos") {
        let script = match action {
            "sleep" => "tell application \"System Events\" to sleep",
            "restart" => "tell application \"System Events\" to restart",
            "shutdown" => "tell application \"System Events\" to shut down",
            _ => return Err("unknown power action".into()),
        };
        run("osascript", &["-e", script])
    } else {
        match action {
            "sleep" => run("systemctl", &["suspend"]),
            "restart" => run("systemctl", &["reboot"]),
            "shutdown" => run("systemctl", &["poweroff"]),
            _ => Err("unknown power action".into()),
        }
    }
}

/// The program to start at login: the AppImage when running from one.
fn program() -> Result<PathBuf, String> {
    if let Some(p) = std::env::var_os("APPIMAGE") {
        return Ok(PathBuf::from(p));
    }
    std::env::current_exe().map_err(|e| e.to_string())
}

#[cfg(windows)]
pub fn autostart() -> bool {
    use winreg::enums::HKEY_CURRENT_USER;
    winreg::RegKey::predef(HKEY_CURRENT_USER)
        .open_subkey("Software\\Microsoft\\Windows\\CurrentVersion\\Run")
        .and_then(|k| k.get_value::<String, _>("wiiOS"))
        .is_ok()
}

#[cfg(windows)]
pub fn set_autostart(on: bool) -> Result<(), String> {
    use winreg::enums::HKEY_CURRENT_USER;
    let (key, _) = winreg::RegKey::predef(HKEY_CURRENT_USER)
        .create_subkey("Software\\Microsoft\\Windows\\CurrentVersion\\Run")
        .map_err(|e| e.to_string())?;
    if on {
        key.set_value("wiiOS", &format!("\"{}\"", program()?.display())).map_err(|e| e.to_string())
    } else {
        // Already off is fine.
        let _ = key.delete_value("wiiOS");
        Ok(())
    }
}

#[cfg(not(windows))]
fn desktop_file() -> Option<PathBuf> {
    Some(dirs::config_dir()?.join("autostart").join("wiios.desktop"))
}

#[cfg(not(windows))]
pub fn autostart() -> bool {
    desktop_file().map(|f| f.exists()).unwrap_or(false)
}

#[cfg(not(windows))]
pub fn set_autostart(on: bool) -> Result<(), String> {
    let f = desktop_file().ok_or("no config folder")?;
    if !on {
        let _ = std::fs::remove_file(&f);
        return Ok(());
    }
    std::fs::create_dir_all(f.parent().unwrap()).map_err(|e| e.to_string())?;
    let text = format!("[Desktop Entry]\nType=Application\nName=wiiOS\nExec=\"{}\"\nX-GNOME-Autostart-enabled=true\n", program()?.display());
    std::fs::write(&f, text).map_err(|e| e.to_string())
}
