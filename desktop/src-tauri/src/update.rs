//! Updates from the same GitHub releases as the TV version: every build of main is release build-N with the
//! Android APK and each computer's file attached. This checks the latest one and, when it is newer than this
//! build, downloads this platform's file, checks its SHA-256 against the one GitHub lists, swaps it in for the
//! running program (renaming a running program is allowed on Windows and Linux; deleting it is not) and starts
//! it. A copy on a flash drive updates itself in place the same way.

use serde::Serialize;
use sha2::{Digest, Sha256};
use std::fs;
use std::io::{Read, Write};
use std::path::PathBuf;

pub const REPO: &str = "BuddyCrafted/Wii-TV";

pub fn build() -> u32 {
    option_env!("WIIOS_BUILD").and_then(|b| b.parse().ok()).unwrap_or(0)
}

/// This computer's file in a release, or None where there is no build for it yet.
pub fn asset_name() -> Option<&'static str> {
    if cfg!(all(windows, target_arch = "x86_64")) {
        Some("WiiOS-windows-x64.exe")
    } else if cfg!(all(target_os = "linux", target_arch = "x86_64")) {
        Some("WiiOS-linux-x86_64.AppImage")
    } else {
        None
    }
}

#[derive(Serialize, Debug, PartialEq)]
pub struct Update {
    pub build: u32,
    pub url: String,
    pub sha256: String,
    pub notes: String,
}

/// build-N -> N.
pub fn build_of(tag: &str) -> Option<u32> {
    tag.strip_prefix("build-")?.parse().ok()
}

/// The release, if it is newer than `current` and has this computer's file.
pub fn parse(release: &serde_json::Value, current: u32, asset: &str) -> Option<Update> {
    let n = build_of(release["tag_name"].as_str()?)?;
    if n <= current {
        return None;
    }
    let a = release["assets"].as_array()?.iter().find(|a| a["name"].as_str() == Some(asset))?;
    Some(Update {
        build: n,
        url: a["browser_download_url"].as_str()?.to_string(),
        sha256: a["digest"].as_str().and_then(|d| d.strip_prefix("sha256:")).unwrap_or("").to_string(),
        notes: release["body"].as_str().unwrap_or("").to_string(),
    })
}

pub fn check() -> Result<Option<Update>, String> {
    let Some(asset) = asset_name() else { return Ok(None) };
    if build() == 0 {
        return Err("this is a development build; updates come with released builds".into());
    }
    let url = format!("https://api.github.com/repos/{}/releases/latest", REPO);
    let release: serde_json::Value = ureq::get(&url)
        .set("User-Agent", "WiiOS-updater")
        .set("Accept", "application/vnd.github+json")
        .call()
        .map_err(|e| e.to_string())?
        .into_json()
        .map_err(|e| e.to_string())?;
    Ok(parse(&release, build(), asset))
}

/// The file to replace: the AppImage itself when running from one, else this program.
fn target() -> Result<PathBuf, String> {
    if let Some(p) = std::env::var_os("APPIMAGE") {
        return Ok(PathBuf::from(p));
    }
    std::env::current_exe().map_err(|e| e.to_string())
}

fn sibling(p: &std::path::Path, suffix: &str) -> PathBuf {
    let mut s = p.as_os_str().to_owned();
    s.push(suffix);
    PathBuf::from(s)
}

/// Removes the copy an earlier update set aside.
pub fn cleanup() {
    if let Ok(t) = target() {
        let _ = fs::remove_file(sibling(&t, ".old"));
    }
}

/// Downloads, verifies and swaps in the new build, then starts it. The caller then exits.
pub fn apply(url: &str, sha256: &str) -> Result<PathBuf, String> {
    if !url.starts_with("https://github.com/") && !url.starts_with("https://objects.githubusercontent.com/") {
        return Err("updates only come from GitHub".into());
    }
    let target = target()?;
    let new = sibling(&target, ".new");
    let old = sibling(&target, ".old");
    let resp = ureq::get(url).set("User-Agent", "WiiOS-updater").call().map_err(|e| e.to_string())?;
    let mut reader = resp.into_reader();
    let mut file = fs::File::create(&new).map_err(|e| format!("can't write next to {}: {}", target.display(), e))?;
    let mut hash = Sha256::new();
    let mut buf = vec![0u8; 1 << 16];
    loop {
        let n = reader.read(&mut buf).map_err(|e| e.to_string())?;
        if n == 0 {
            break;
        }
        hash.update(&buf[..n]);
        file.write_all(&buf[..n]).map_err(|e| e.to_string())?;
    }
    file.sync_all().map_err(|e| e.to_string())?;
    drop(file);
    let got = format!("{:x}", hash.finalize());
    if !sha256.is_empty() && !got.eq_ignore_ascii_case(sha256) {
        let _ = fs::remove_file(&new);
        return Err("the download was damaged (checksum mismatch); nothing was changed".into());
    }
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(&new, fs::Permissions::from_mode(0o755)).map_err(|e| e.to_string())?;
    }
    let _ = fs::remove_file(&old);
    fs::rename(&target, &old).map_err(|e| e.to_string())?;
    if let Err(e) = fs::rename(&new, &target) {
        let _ = fs::rename(&old, &target);
        return Err(e.to_string());
    }
    std::process::Command::new(&target).spawn().map_err(|e| e.to_string())?;
    Ok(target)
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    fn release(tag: &str) -> serde_json::Value {
        json!({
            "tag_name": tag,
            "body": "notes",
            "assets": [
                {"name": "WiiOS-build-42.apk", "browser_download_url": "https://github.com/x/apk"},
                {"name": "WiiOS-windows-x64.exe", "browser_download_url": "https://github.com/x/exe", "digest": "sha256:abc"}
            ]
        })
    }

    #[test]
    fn newer_release_with_our_file() {
        let u = parse(&release("build-42"), 41, "WiiOS-windows-x64.exe").unwrap();
        assert_eq!(u.build, 42);
        assert_eq!(u.url, "https://github.com/x/exe");
        assert_eq!(u.sha256, "abc");
    }

    #[test]
    fn same_or_older_or_missing_file_is_no_update() {
        assert_eq!(parse(&release("build-42"), 42, "WiiOS-windows-x64.exe"), None);
        assert_eq!(parse(&release("build-40"), 41, "WiiOS-windows-x64.exe"), None);
        assert_eq!(parse(&release("build-42"), 1, "WiiOS-linux-x86_64.AppImage"), None);
        assert_eq!(parse(&release("v1.0"), 1, "WiiOS-windows-x64.exe"), None);
    }
}
