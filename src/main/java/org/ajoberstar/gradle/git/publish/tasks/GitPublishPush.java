package org.ajoberstar.gradle.git.publish.tasks;

import java.io.File;
import java.util.Arrays;

import javax.inject.Inject;

import org.ajoberstar.grgit.gradle.GrgitService;
import org.gradle.api.DefaultTask;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

@UntrackedTask(because = "Git tracks the state")
public class GitPublishPush extends DefaultTask {
  private final Property<GrgitService> grgitService;
  private final Property<String> branch;

  @Inject
  public GitPublishPush(ObjectFactory objectFactory) {
    this.grgitService = objectFactory.property(GrgitService.class);
    this.branch = objectFactory.property(String.class);

    this.onlyIf(t -> {
      try {
        var git = getGrgitService().get().getGrgit();
        var status = git.getBranch().status(op -> {
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
  public Property<GrgitService> getGrgitService() {
    return grgitService;
  }

  @Input
  public Property<String> getBranch() {
    return branch;
  }

  @TaskAction
  public void push() {
    var git = getGrgitService().get().getGrgit();
    var pubBranch = getBranch().get();
    git.push(op -> {
      op.setRefsOrSpecs(Arrays.asList(String.format("refs/heads/%s:refs/heads/%s", pubBranch, pubBranch)));
    });
  }
}
