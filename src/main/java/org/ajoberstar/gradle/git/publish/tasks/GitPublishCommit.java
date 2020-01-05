package org.ajoberstar.gradle.git.publish.tasks;

import java.io.File;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.ajoberstar.grgit.Grgit;
import org.gradle.api.DefaultTask;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

public class GitPublishCommit extends DefaultTask {
  private final Property<Grgit> grgit;
  private final Property<String> message;
  private final Property<Boolean> sign;

  @Inject
  public GitPublishCommit(ObjectFactory objectFactory) {
    this.grgit = objectFactory.property(Grgit.class);
    this.message = objectFactory.property(String.class);
    this.sign = objectFactory.property(Boolean.class);

    // always consider this task out of date
    this.getOutputs().upToDateWhen(t -> false);
  }

  @Internal
  public Property<Grgit> getGrgit() {
    return grgit;
  }

  @Input
  public Property<String> getMessage() {
    return message;
  }

  @Input
  @Optional
  public Property<Boolean> getSign() {
    return sign;
  }

  @OutputDirectory
  public File getRepoDirectory() {
    return getGrgit().get().getRepository().getRootDir();
  }

  @TaskAction
  public void commit() {
    Grgit git = getGrgit().get();
    git.add(op -> {
      op.setPatterns(Stream.of(".").collect(Collectors.toSet()));
    });

    // check if anything has changed
    if (git.status().isClean()) {
      setDidWork(false);
    } else {
      git.commit(op -> {
        op.setMessage(getMessage().get());
        if (getSign().isPresent()) {
          op.setSign(getSign().get());
        }
      });
      setDidWork(true);
    }
  }

}
