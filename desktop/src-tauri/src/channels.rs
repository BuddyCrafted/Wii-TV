//! The channels a computer gets: installed Steam and Epic games, and the viewer's own channels from
//! channels.json in the app's config folder (anything that opens: a program, a file, a link).

use base64::Engine;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};

#[derive(Serialize, Clone)]
pub struct Channel {
    pub key: String,
    pub label: String,
    pub kind: String,
    /// What `launch` opens: a URL (steam://, https://, ...) or a path.
    pub target: String,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub args: Vec<String>,
    /// The tile's picture: a data: URL for local files, or an https URL.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub image: Option<String>,
}

pub fn all() -> Vec<Channel> {
    let mut out = steam();
    out.extend(epic());
    out.extend(custom());
    out
}

// ---- Steam ----------------------------------------------------------------------------------------

fn steam_roots() -> Vec<PathBuf> {
    let mut roots = Vec::new();
    #[cfg(windows)]
    {
        use winreg::enums::HKEY_CURRENT_USER;
        if let Ok(key) = winreg::RegKey::predef(HKEY_CURRENT_USER).open_subkey("Software\\Valve\\Steam") {
            if let Ok(p) = key.get_value::<String, _>("SteamPath") {
                roots.push(PathBuf::from(p));
            }
        }
        roots.push(PathBuf::from("C:\\Program Files (x86)\\Steam"));
    }
    if let Some(home) = dirs::home_dir() {
        for rel in [".local/share/Steam", ".steam/steam", ".var/app/com.valvesoftware.Steam/.local/share/Steam",
            "Library/Application Support/Steam"] {
            roots.push(home.join(rel));
        }
    }
    let mut seen = Vec::new();
    roots.retain(|r| {
        let ok = r.join("steamapps").is_dir() && !seen.iter().any(|s: &PathBuf| same(s, r));
        if ok {
            seen.push(r.clone());
        }
        ok
    });
    roots
}

fn same(a: &Path, b: &Path) -> bool {
    match (a.canonicalize(), b.canonicalize()) {
        (Ok(x), Ok(y)) => x == y,
        _ => a == b,
    }
}

/// The value of `"key"  "value"` on the first line that has it (Valve's text format).
pub fn vdf_values(text: &str, key: &str) -> Vec<String> {
    let want = format!("\"{}\"", key);
    text.lines()
        .filter_map(|l| {
            let l = l.trim();
            let rest = l.strip_prefix(&want)?.trim();
            let v = rest.strip_prefix('"')?.strip_suffix('"')?;
            Some(v.replace("\\\\", "\\"))
        })
        .collect()
}

/// Steam's own tools that are installed like games but aren't ones.
pub fn steam_tool(appid: &str, name: &str) -> bool {
    appid == "228980"
        || ["Proton", "Steam Linux Runtime", "Steamworks Common", "Steam Runtime", "Redistributable"]
            .iter()
            .any(|t| name.contains(t))
}

fn steam() -> Vec<Channel> {
    let mut out: Vec<Channel> = Vec::new();
    for root in steam_roots() {
        let mut libs = vec![root.clone()];
        if let Ok(t) = fs::read_to_string(root.join("steamapps").join("libraryfolders.vdf")) {
            for p in vdf_values(&t, "path") {
                let p = PathBuf::from(p);
                if !libs.iter().any(|l| same(l, &p)) {
                    libs.push(p);
                }
            }
        }
        for lib in libs {
            let Ok(entries) = fs::read_dir(lib.join("steamapps")) else { continue };
            for e in entries.flatten() {
                let name = e.file_name().to_string_lossy().to_string();
                if !(name.starts_with("appmanifest_") && name.ends_with(".acf")) {
                    continue;
                }
                let Ok(t) = fs::read_to_string(e.path()) else { continue };
                let (Some(id), Some(label)) = (vdf_values(&t, "appid").into_iter().next(), vdf_values(&t, "name").into_iter().next())
                else {
                    continue;
                };
                if steam_tool(&id, &label) || out.iter().any(|c| c.key == format!("steam:{}", id)) {
                    continue;
                }
                out.push(Channel {
                    key: format!("steam:{}", id),
                    label,
                    kind: "steam".into(),
                    target: format!("steam://rungameid/{}", id),
                    args: vec![],
                    image: steam_art(&root, &id),
                });
            }
        }
    }
    out
}

fn steam_art(root: &Path, id: &str) -> Option<String> {
    let cache = root.join("appcache").join("librarycache");
    for p in [cache.join(id).join("header.jpg"), cache.join(format!("{}_header.jpg", id))] {
        if let Some(d) = data_url(&p) {
            return Some(d);
        }
    }
    Some(format!("https://cdn.cloudflare.steamstatic.com/steam/apps/{}/header.jpg", id))
}

// ---- Epic Games ---------------------------------------------------------------------------------

#[derive(Deserialize)]
#[serde(rename_all = "PascalCase")]
struct EpicItem {
    display_name: String,
    app_name: String,
    catalog_namespace: String,
    catalog_item_id: String,
    #[serde(default, rename = "bIsIncompleteInstall")]
    incomplete: bool,
}

fn epic() -> Vec<Channel> {
    let dir = std::env::var_os("PROGRAMDATA")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("C:\\ProgramData"))
        .join("Epic\\EpicGamesLauncher\\Data\\Manifests");
    let Ok(entries) = fs::read_dir(dir) else { return vec![] };
    let mut out = Vec::new();
    for e in entries.flatten() {
        if e.path().extension().and_then(|x| x.to_str()) != Some("item") {
            continue;
        }
        let Ok(t) = fs::read_to_string(e.path()) else { continue };
        let Ok(item) = serde_json::from_str::<EpicItem>(&t) else { continue };
        if item.incomplete {
            continue;
        }
        out.push(Channel {
            key: format!("epic:{}", item.app_name),
            label: item.display_name,
            kind: "epic".into(),
            target: format!(
                "com.epicgames.launcher://apps/{}%3A{}%3A{}?action=launch&silent=true",
                item.catalog_namespace, item.catalog_item_id, item.app_name
            ),
            args: vec![],
            image: None,
        });
    }
    out
}

// ---- the viewer's own channels ------------------------------------------------------------------

#[derive(Deserialize)]
struct CustomFile {
    #[serde(default)]
    channels: Vec<Custom>,
}

#[derive(Deserialize)]
struct Custom {
    name: String,
    open: String,
    #[serde(default)]
    args: Vec<String>,
    #[serde(default)]
    image: Option<String>,
}

pub fn config_dir() -> PathBuf {
    dirs::config_dir().unwrap_or_else(std::env::temp_dir).join("WiiOS")
}

pub const EXAMPLE: &str = r#"{
  "help": "Each channel needs a name and what to open: a program, a file, a folder or a link. image is optional (a picture file or an https link). Example: {\"name\": \"Kodi\", \"open\": \"C:\\\\Program Files\\\\Kodi\\\\kodi.exe\", \"image\": \"C:\\\\Pictures\\\\kodi.png\"}",
  "channels": []
}
"#;

/// channels.json, created with an explanation the first time.
pub fn channels_file() -> PathBuf {
    let dir = config_dir();
    let file = dir.join("channels.json");
    if !file.exists() {
        let _ = fs::create_dir_all(&dir);
        let _ = fs::write(&file, EXAMPLE);
    }
    file
}

fn custom() -> Vec<Channel> {
    let Ok(t) = fs::read_to_string(channels_file()) else { return vec![] };
    let Ok(f) = serde_json::from_str::<CustomFile>(&t) else { return vec![] };
    f.channels
        .into_iter()
        .enumerate()
        .map(|(i, c)| Channel {
            key: format!("custom:{}:{}", i, c.name),
            label: c.name,
            kind: "custom".into(),
            target: c.open,
            args: c.args,
            image: c.image.and_then(|im| if im.starts_with("https://") { Some(im) } else { data_url(Path::new(&im)) }),
        })
        .collect()
}

fn data_url(p: &Path) -> Option<String> {
    let meta = fs::metadata(p).ok()?;
    if !meta.is_file() || meta.len() > 2_000_000 {
        return None;
    }
    let mime = match p.extension()?.to_str()?.to_ascii_lowercase().as_str() {
        "jpg" | "jpeg" => "image/jpeg",
        "png" => "image/png",
        "webp" => "image/webp",
        "gif" => "image/gif",
        _ => return None,
    };
    let bytes = fs::read(p).ok()?;
    Some(format!("data:{};base64,{}", mime, base64::engine::general_purpose::STANDARD.encode(bytes)))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_valve_text() {
        let t = "\"AppState\"\n{\n\t\"appid\"\t\t\"620\"\n\t\"name\"\t\t\"Portal 2\"\n}\n\"libraryfolders\"\n{\n\t\"0\"\n\t{\n\t\t\"path\"\t\t\"D:\\\\SteamLibrary\"\n\t}\n}";
        assert_eq!(vdf_values(t, "appid"), vec!["620"]);
        assert_eq!(vdf_values(t, "name"), vec!["Portal 2"]);
        assert_eq!(vdf_values(t, "path"), vec!["D:\\SteamLibrary"]);
    }

    #[test]
    fn skips_steam_tools() {
        assert!(steam_tool("228980", "Steamworks Common Redistributables"));
        assert!(steam_tool("1493710", "Proton Experimental"));
        assert!(steam_tool("1628350", "Steam Linux Runtime 3.0 (sniper)"));
        assert!(!steam_tool("620", "Portal 2"));
    }

    #[test]
    fn example_file_parses_to_no_channels() {
        let f: CustomFile = serde_json::from_str(EXAMPLE).unwrap();
        assert!(f.channels.is_empty());
    }
}
