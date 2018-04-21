package org.ajoberstar.gradle.git.publish.tasks;

import java.io.File;
import java.util.Arrays;

import javax.inject.Inject;

import org.ajoberstar.grgit.BranchStatus;
import org.ajoberstar.grgit.Grgit;
import org.gradle.api.DefaultTask;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

public class GitPublishPush extends DefaultTask {
  private final Property<Grgit> grgit;
  private final Property<String> branch;

  @Inject
  public GitPublishPush(ObjectFactory objectFactory) {
    this.grgit = objectFactory.property(Grgit.class);
    this.branch = objectFactory.property(String.class);

    // always consider this task out of date
    this.getOutputs().upToDateWhen(t -> false);

    this.onlyIf(t -> {
      try {
        Grgit git = getGrgit().get();
        BranchStatus status = git.getBranch().status(op -> {
          op.setName(getBranch().get());
        });
        return status.getAheadCount() > 0;
      } catch (IllegalStateException e) {
        // if we're not tracking anything yet (i.e. orphan) we need to push
        return true;
      }
    });
  }

  @Internal
  public Property<Grgit> getGrgit() {
    return grgit;
  }

  @Input
  public Property<String> getBranch() {
    return branch;
  }

  @OutputDirectory
  public File getRepoDirectory() {
    return getGrgit().get().getRepository().getRootDir();
  }

  @TaskAction
  public void push() {
    Grgit git = getGrgit().get();
    String pubBranch = getBranch().get();
    git.push(op -> {
      op.setRefsOrSpecs(Arrays.asList(String.format("refs/heads/%s:refs/heads/%s", pubBranch, pubBranch)));
    });
  }
}
