/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import com.intellij.openapi.util.io.FileUtil;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.apache.log4j.Logger;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * @author dmitry.neverov
 */
public class UpdaterWithMirror extends UpdaterImpl {

  private final static Logger LOG = Logger.getLogger(UpdaterWithMirror.class);

  protected MirrorManager mirrorManager;

  public UpdaterWithMirror(@NotNull AgentPluginConfig pluginConfig,
                           @NotNull MirrorManager mirrorManager,
                           @NotNull SmartDirectoryCleaner directoryCleaner,
                           @NotNull GitFactory gitFactory,
                           @NotNull AgentRunningBuild build,
                           @NotNull VcsRoot root,
                           @NotNull String version,
                           @NotNull File targetDir) throws VcsException {
    super(pluginConfig, mirrorManager, directoryCleaner, gitFactory, build, root, version, targetDir);
    this.mirrorManager = mirrorManager;
  }

  @Override
  protected void doUpdate() throws VcsException {
    updateLocalMirror();
    super.doUpdate();
  }

  private void updateLocalMirror() throws VcsException {
    File bareRepositoryDir = myRoot.getRepositoryDir();
    updateLocalMirror(bareRepositoryDir, myRoot.getName(), myRoot.getRepositoryFetchURL().toString(), myFullBranchName, myRevision);
  }

  private void updateLocalMirror(File bareRepositoryDir, String name, String fetchUrl, String branchName, String revision) throws VcsException {
    String mirrorDescription = "local mirror of root " + name + " at " + bareRepositoryDir;
    LOG.info("Update " + mirrorDescription);
    boolean fetchRequired = true;
    if (!isValidGitRepo(bareRepositoryDir))
      FileUtil.delete(bareRepositoryDir);
    if (!bareRepositoryDir.exists()) {
      LOG.info("Init " + mirrorDescription);
      bareRepositoryDir.mkdirs();
      GitFacade git = myGitFactory.create(bareRepositoryDir);
      git.init().setBare(true).call();
      git.addRemote().setName("origin").setUrl(fetchUrl).call();
    } else {
      boolean outdatedTagsFound = removeOutdatedRefs(bareRepositoryDir);
      if (!outdatedTagsFound) {
        LOG.debug("Try to find revision  " + myRevision + " in " + mirrorDescription);
        Ref ref = getRef(bareRepositoryDir, GitUtils.expandRef(myRoot.getRef()));
        if (ref != null && revision.equals(ref.getObjectId().name())) {
          LOG.info("No fetch required for revision '" + revision + "' in " + mirrorDescription);
          fetchRequired = false;
        }
      }
    }
    if(branchName != null) {
      Ref ref = getRef(bareRepositoryDir, branchName);
      if (ref == null)
        fetchRequired = true;
    }
    if (fetchRequired) {
      String refspec;
      if(branchName == null)
        refspec = null;
      else
        refspec = "+" + branchName + ":" + GitUtils.expandRef(branchName);
      fetchMirror(bareRepositoryDir, refspec, fetchUrl, false);
    }
    if (hasRevision(bareRepositoryDir, revision))
      return;
    fetchMirror(bareRepositoryDir, "+refs/heads/*:refs/heads/*", fetchUrl, false);
  }


  private void fetchMirror(@NotNull File repositoryDir, String refspec, @NotNull String fetchUrl, boolean shallowClone) throws VcsException {
    removeRefLocks(repositoryDir);
    try {
      fetch(repositoryDir, refspec, shallowClone);
    } catch (VcsException e) {
      FileUtil.delete(repositoryDir);
      repositoryDir.mkdirs();
      GitFacade git = myGitFactory.create(repositoryDir);
      git.init().setBare(true).call();
      git.addRemote().setName("origin").setUrl(fetchUrl).call();
      fetch(repositoryDir, refspec, shallowClone);
    }
  }


  private boolean isValidGitRepo(@NotNull File gitDir) {
    try {
      new RepositoryBuilder().setGitDir(gitDir).setMustExist(true).build();
      return true;
    } catch (IOException e) {
      return false;
    }
  }


  @Override
  protected void setupMirrors() throws VcsException {
    if (!isRepositoryUseLocalMirror())
      setUseLocalMirror();
  }

  @Override
  protected void postInit() throws VcsException {
    setUseLocalMirror();
  }

  @Override
  protected void ensureCommitLoaded(boolean fetchRequired) throws VcsException {
    if (myPluginConfig.isUseShallowClone()) {
      File mirrorRepositoryDir = myRoot.getRepositoryDir();
      String tmpBranchName = createTmpBranch(mirrorRepositoryDir, myRevision);
      String tmpBranchRef = "refs/heads/" + tmpBranchName;
      String refspec = "+" + tmpBranchRef + ":" + GitUtils.createRemoteRef(myFullBranchName);
      fetch(myTargetDirectory, refspec, true);
      myGitFactory.create(mirrorRepositoryDir).deleteBranch().setName(tmpBranchName).call();
    } else {
      super.ensureCommitLoaded(fetchRequired);
    }
  }

  private void setUseLocalMirror() throws VcsException {
    setUseLocalMirror(myRoot.getRepositoryFetchURL().toString(), getLocalMirrorUrl(myRoot.getRepositoryDir()), myTargetDirectory);
  }

  private void setUseLocalMirror(String remoteUrl, String localMirrorUrl, File targetDirectory) throws VcsException {
    GitFacade git = myGitFactory.create(targetDirectory);

    git.setConfig()
      .setPropertyName("url." + localMirrorUrl + ".insteadOf")
      .setValue(remoteUrl)
      .call();
    git.setConfig()
      .setPropertyName("url." + remoteUrl + ".pushInsteadOf")
      .setValue(remoteUrl)
      .call();
  }

  private String getLocalMirrorUrl(File repositoryDir) throws VcsException {
    try {
      return new URIish(repositoryDir.toURI().toASCIIString()).toString();
    } catch (URISyntaxException e) {
      throw new VcsException("Cannot create uri for local mirror " + repositoryDir.getAbsolutePath(), e);
    }
  }

  private String createTmpBranch(@NotNull File repositoryDir, @NotNull String branchStartingPoint) throws VcsException {
    String tmpBranchName = getUnusedBranchName(repositoryDir);
    myGitFactory.create(repositoryDir)
      .createBranch()
      .setName(tmpBranchName)
      .setStartPoint(branchStartingPoint)
      .call();
    return tmpBranchName;
  }

  private String getUnusedBranchName(@NotNull File repositoryDir) {
    final String tmpBranchName = "tmp_branch_for_build";
    String branchName = tmpBranchName;
    Map<String, Ref> existingRefs = myGitFactory.create(repositoryDir).showRef().call();
    int i = 0;
    while (existingRefs.containsKey("refs/heads/" + branchName)) {
      branchName = tmpBranchName + i;
      i++;
    }
    return branchName;
  }

  protected void checkoutSubmodules(@NotNull final File repositoryDir) throws VcsException {
    File gitmodules = new File(repositoryDir, ".gitmodules");
    if (gitmodules.exists()) {
      LOG.info("Checkout submodules in " + repositoryDir);
      GitFacade git = myGitFactory.create(repositoryDir);
      git.submoduleInit().call();
      git.submoduleSync().call();

      Map<String, String> submoduleRevisions = git.submoduleList().call();

      try {
        String gitmodulesContents = jetbrains.buildServer.util.FileUtil.readText(gitmodules);
        Config config = new Config();
        config.fromText(gitmodulesContents);

        Set<String> submodules = config.getSubsections("submodule");
        for (String submoduleName : submodules) {
          String submodulePath = config.getString("submodule", submoduleName, "path");
          String submoduleUrl = config.getString("submodule", submoduleName, "url");

          String revision = submoduleRevisions.get(submodulePath);
          if(revision == null)
            throw new VcsException("Error while find submodule revision for " + submoduleName);

          File submoduleMirrorDir = mirrorManager.getMirrorDir(submoduleUrl);
          updateLocalMirror(submoduleMirrorDir, myRoot.getName() + " submodule " + submoduleName, submoduleUrl, null, revision);

          git.setConfig()
            .setPropertyName("submodule." + submoduleName + ".url")
            .setValue(getLocalMirrorUrl(submoduleMirrorDir))
            .call();
          //setUseLocalMirror(submoduleUrl, getLocalMirrorUrl(submoduleMirrorDir), repositoryDir);
        }

        git.submoduleUpdate()
          .setAuthSettings(myRoot.getAuthSettings())
          .setUseNativeSsh(myPluginConfig.isUseNativeSSH())
          .setTimeout(SILENT_TIMEOUT)
          .call();

        if (recursiveSubmoduleCheckout()) {
          for (String submoduleName : submodules) {
            String submodulePath = config.getString("submodule", submoduleName, "path");
            checkoutSubmodules(new File(repositoryDir, submodulePath.replaceAll("/", Matcher.quoteReplacement(File.separator))));
          }
        }

      } catch (IOException e) {
        throw new VcsException("Error while reading " + gitmodules, e);
      } catch (ConfigInvalidException e) {
        throw new VcsException("Error while parsing " + gitmodules, e);
      }
    }
  }
}
