package studio;

import com.sun.jna.platform.win32.Crypt32Util;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;

/**
 * App settings in %APPDATA%\MathTestsStudio\settings.properties.
 * The admin password is encrypted with Windows DPAPI,
 * so only this Windows user on this PC can read them. Nothing here is ever committed.
 */
public final class Settings {
    private static final Path DIR = Path.of(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), "MathTestsStudio");
    private static final Path FILE = DIR.resolve("settings.properties");
    private final Properties p = new Properties();

    public static final String REPO_NAME = "math-tests-studio";

    /** Private copies of the files made in Claude (they contain the answers). Never inside the repo. */
    public static Path originalsDir() { return DIR.resolve("originals"); }

    public Settings() {
        if (Files.exists(FILE)) {
            try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) { p.load(r); } catch (IOException ignored) {}
        }
    }

    public void save() {
        try {
            Files.createDirectories(DIR);
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) { p.store(w, "Math Tests Studio"); }
        } catch (IOException e) {
            throw new RuntimeException("Could not save settings: " + e.getMessage(), e);
        }
    }

    public String get(String key) { return p.getProperty(key, ""); }
    public void set(String key, String value) { p.setProperty(key, value == null ? "" : value.trim()); }

    public String secret(String key) {
        String enc = p.getProperty(key, "");
        if (enc.isEmpty()) return "";
        try {
            return new String(Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(enc)), StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            return "";
        }
    }

    public void setSecret(String key, String value) {
        if (value == null || value.isEmpty()) { p.remove(key); return; }
        p.setProperty(key, Base64.getEncoder().encodeToString(Crypt32Util.cryptProtectData(value.getBytes(StandardCharsets.UTF_8))));
    }

    // Named accessors
    public String adminName() { return get("admin.name"); }
    public String adminPassword() { return secret("admin.password"); }
    public String repoFolder() { return get("repo.folder"); }
}
