package studio;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs git on the local clone of the site. Sign-in to GitHub is handled by Git Credential Manager
 * (installed with Git for Windows): the first push opens a GitHub sign-in window, after that it is remembered.
 */
public final class GitOps {
    private final Path repo;
    private final String git;

    public GitOps(Path repo) {
        this.repo = repo;
        File std = new File("C:\\Program Files\\Git\\cmd\\git.exe");
        this.git = std.exists() ? std.getPath() : "git";
    }

    public record Result(int code, String out) {
        public boolean ok() { return code == 0; }
    }

    public Result run(int timeoutSec, String... args) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(git);
        cmd.add("-C");
        cmd.add(repo.toString());
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        CompletableFuture<String> out = CompletableFuture.supplyAsync(() -> {
            try { return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim(); }
            catch (IOException e) { return ""; }
        });
        try {
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return new Result(-1, "git " + args[0] + " timed out");
            }
            return new Result(p.exitValue(), out.get(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new IOException("Interrupted", e);
        } catch (ExecutionException | TimeoutException e) {
            return new Result(p.exitValue(), "");
        }
    }

    /** "owner/math-tests-studio" from the origin remote, or "" when this is not the Studio repo. */
    public String ownerRepo() {
        try {
            Result r = run(15, "remote", "get-url", "origin");
            if (!r.ok()) return "";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("github\\.com[/:]([\\w.-]+)/([\\w.-]+?)(\\.git)?$").matcher(r.out().trim());
            if (!m.find() || !m.group(2).equalsIgnoreCase(Settings.REPO_NAME)) return "";
            return m.group(1) + "/" + m.group(2);
        } catch (IOException e) {
            return "";
        }
    }

    /** True when the folder is a clone of the Studio repo and has the site in docs/. */
    public boolean isPortalRepo() {
        return !ownerRepo().isEmpty() && repo.resolve("docs").resolve("portal.js").toFile().exists();
    }

    /** GitHub Pages address, e.g. https://owner.github.io/math-tests-studio/ */
    public String siteUrl() {
        String or = ownerRepo();
        if (or.isEmpty()) return "";
        String[] p = or.split("/");
        return "https://" + p[0].toLowerCase() + ".github.io/" + p[1] + "/";
    }

    /** Can we reach GitHub and see the main branch? */
    public boolean remoteReachable() {
        try {
            Result r = run(30, "ls-remote", "--heads", "origin", "main");
            return r.ok() && r.out().contains("refs/heads/main");
        } catch (IOException e) {
            return false;
        }
    }

    /** Turns on the repo's answer-key check (.githooks/pre-commit) for every commit, also ones made outside the app. */
    public void ensureHook() {
        if (!repo.resolve(".githooks").resolve("pre-commit").toFile().exists()) return;
        try { run(15, "config", "core.hooksPath", ".githooks"); } catch (IOException ignored) {}
    }

    public Result pull() throws IOException { return run(120, "pull", "--ff-only", "origin", "main"); }

    public Result commitFiles(List<String> relPaths, String message) throws IOException {
        List<String> add = new ArrayList<>(List.of("add", "--"));
        add.addAll(relPaths);
        Result a = run(30, add.toArray(String[]::new));
        if (!a.ok()) return a;
        List<String> commit = new ArrayList<>(List.of("commit", "-m", message, "--"));
        commit.addAll(relPaths);
        return run(30, commit.toArray(String[]::new));
    }

    /** Push can open a GitHub sign-in window the first time, so allow plenty of time. */
    public Result push() throws IOException { return run(600, "push", "origin", "HEAD:main"); }
}
