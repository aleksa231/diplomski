package com.benchmark.runner.exec;

import com.benchmark.runner.config.RunnerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Runs the bash failure-injection helpers from {@code scripts/} (spec section 10.2). The scripts use
 * the Docker socket that is mounted into the runner to stop/start containers; that is acceptable for
 * a local demo and is called out in the README.
 */
@Component
public class ShellScriptRunner {

    private static final Logger log = LoggerFactory.getLogger(ShellScriptRunner.class);

    private final RunnerProperties properties;

    public ShellScriptRunner(RunnerProperties properties) {
        this.properties = properties;
    }

    public record Outcome(boolean success, int exitCode, String error) {
    }

    public Outcome run(String scriptName, Consumer<String> logSink) {
        return run(scriptName, logSink, (String[]) null);
    }

    public Outcome run(String scriptName, Consumer<String> logSink, String... args) {
        Path script;
        try {
            script = resolve(scriptName);
        } catch (IllegalArgumentException e) {
            return new Outcome(false, -1, e.getMessage());
        }
        if (!Files.isRegularFile(script)) {
            String message = "Skripta nije pronađena: " + script;
            log.warn(message);
            logSink.accept("[runner] " + message);
            return new Outcome(false, -1, message);
        }
        List<String> command = new ArrayList<>();
        command.add("bash");
        command.add(script.toString());
        if (args != null) {
            for (String arg : args) {
                if (arg != null && !arg.isBlank()) {
                    command.add(arg);
                }
            }
        }
        log.info("Running script: {}", String.join(" ", command));
        logSink.accept("[runner] $ " + String.join(" ", command));

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logSink.accept("[script] " + line);
                }
            }
            boolean finished = process.waitFor(properties.getDefaults().getScriptTimeoutSec(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new Outcome(false, -1, "Skripti " + scriptName + " je isteklo vrijeme");
            }
            int exitCode = process.exitValue();
            return exitCode == 0
                    ? new Outcome(true, 0, null)
                    : new Outcome(false, exitCode, "Skripta " + scriptName + " završila sa kodom " + exitCode);
        } catch (IOException e) {
            return new Outcome(false, -1, "Ne mogu pokrenuti " + scriptName + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome(false, -1, "Prekinuto izvršavanje " + scriptName);
        }
    }

    /** Prevents a target definition from escaping the scripts directory. */
    private Path resolve(String scriptName) {
        if (scriptName == null || scriptName.isBlank()) {
            throw new IllegalArgumentException("Prazno ime skripte");
        }
        Path root = Path.of(properties.scriptsDir()).toAbsolutePath().normalize();
        Path resolved = root.resolve(scriptName).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Putanja skripte izlazi iz scripts direktorija: " + scriptName);
        }
        return resolved;
    }
}
