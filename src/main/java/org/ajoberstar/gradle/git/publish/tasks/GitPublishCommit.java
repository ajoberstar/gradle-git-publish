package org.ajoberstar.gradle.git.publish.tasks;

import java.io.File;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.ajoberstar.grgit.gradle.GrgitService;
import org.gradle.api.DefaultTask;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

@UntrackedTask(because = "Git tracks the state")
public class GitPublishCommit extends DefaultTask {
  private final Property<GrgitService> grgitService;
  private final Property<String> message;
  private final Property<Boolean> sign;

  @Inject
  public GitPublishCommit(ObjectFactory objectFactory) {
    this.grgitService = objectFactory.property(GrgitService.class);
    this.message = objectFactory.property(String.class);
    this.sign = objectFactory.property(Boolean.class);
  }

  @Internal
  public Property<GrgitService> getGrgitService() {
    return grgitService;
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

  @TaskAction
  public void commit() {
    var git = getGrgitService().get().getGrgit();
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
