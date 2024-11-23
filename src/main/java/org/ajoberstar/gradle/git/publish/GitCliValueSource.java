package org.ajoberstar.gradle.git.publish;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import javax.inject.Inject;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.Nullable;

public abstract class GitCliValueSource implements ValueSource<String, GitCliValueSource.Params> {
  public interface Params extends ValueSourceParameters {
    ListProperty<String> getGitArguments();
  }

  @Inject
  protected abstract ExecOperations getExecOperations();

  @Override
  public @Nullable String obtain() {
    try {
      var output = new ByteArrayOutputStream();
      getExecOperations().exec(spec -> {
        spec.executable("git");
        spec.setArgs(getParameters().getGitArguments().get());
        spec.setStandardOutput(output);
        spec.setErrorOutput(OutputStream.nullOutputStream());
      });
      return output.toString(StandardCharsets.UTF_8).trim();
    } catch (Exception e) {
      return null;
    }
  }
}
