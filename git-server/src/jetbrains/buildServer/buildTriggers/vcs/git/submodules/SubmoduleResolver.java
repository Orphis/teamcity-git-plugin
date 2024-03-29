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

package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.VcsAuthenticationException;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;

/**
 * The resolver for submodules
 */
public abstract class SubmoduleResolver {

  private static Logger LOG = Logger.getInstance(SubmoduleResolver.class.getName());

  private final RevCommit myCommit;
  private final Repository myDb;
  protected final GitVcsSupport myGitSupport;
  private SubmodulesConfig myConfig;

  public SubmoduleResolver(GitVcsSupport gitSupport, Repository db, RevCommit commit) {
    myGitSupport = gitSupport;
    myDb = db;
    myCommit = commit;
  }

  /**
   * Resolve the commit for submodule
   *
   * @param path   the within repository path
   * @param commit the commit identifier
   * @return the the resoled commit in other repository
   * @throws IOException if there is an IO problem during resolving repository or mapping commit
   * @throws VcsAuthenticationException if there are authentication problems
   * @throws URISyntaxException if there are errors in submodule repository URI
   */
  public RevCommit getSubmoduleCommit(String path, ObjectId commit) throws IOException, VcsException, URISyntaxException {
    ensureConfigLoaded();
    String mainRepositoryUrl = myDb.getConfig().getString("teamcity", null, "remote");
    if (myConfig == null) {
      String msg = "Repository '%1$s' has submodule in commit '%2$s' at path '%3$s', but has no .gitmodules configuration at the root directory.";
      throw new CorruptObjectException(String.format(msg, mainRepositoryUrl, myCommit.getId().name(), path));
    }
    final Submodule submodule = myConfig.findSubmodule(path);
    if (submodule == null) {
      String msg = "Repository '%1$s' has submodule in commit '%2$s' at path '%3$s', but has no entry for this path in .gitmodules configuration.";
      throw new CorruptObjectException(String.format(msg, mainRepositoryUrl, myCommit.getId().name(), path, commit.name()));
    }
    Repository r = resolveRepository(path, submodule.getUrl());
    if (!isCommitExist(r, commit))
      fetch(r, path, submodule.getUrl());
    final RevCommit c = myGitSupport.getCommit(r, commit);
    if (c == null) {
      String msg = "Repository '%1$s' has submodule in commit '%2$s' at path '%3$s', but tracked submodule commit '%4$s' is not found in repository '%5$s'. Forget to push it?";
      throw new CorruptObjectException(String.format(msg, mainRepositoryUrl, myCommit.getId().name(), path, commit.name(), submodule.getUrl()));
    }
    return c;
  }

  private boolean isCommitExist(final Repository r, final ObjectId commit) {
    RevWalk walk = new RevWalk(r);
    try {
      walk.parseCommit(commit);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Get repository by the URL. Note that the repository is retrieved but not cleaned up. This should be done by implementer of this component at later time.
   *
   * @param path the local path within repository
   * @param submoduleUrl the URL to resolve
   * @return the resolved repository
   * @throws IOException if repository could not be resolved
   * @throws VcsAuthenticationException in case of authentication problems
   * @throws URISyntaxException if there are errors in submodule repository URI
   */
  protected abstract Repository resolveRepository(String path, String submoduleUrl) throws IOException, VcsException, URISyntaxException;

  protected abstract void fetch(Repository r, String submodulePath, String submoduleUrl) throws VcsException, URISyntaxException, IOException;

  /**
   * Get submodule resolver for the path
   *
   * @param commit the start commit
   * @param path   the local path within repository
   * @return the submodule resolver that handles submodules inside the specified commit
   */
  public abstract SubmoduleResolver getSubResolver(RevCommit commit, String path);

  /**
   * Check if the specified directory is a submodule prefix
   *
   * @param path the path to check
   * @return true if the path contains submodules
   */
  public boolean containsSubmodule(String path) {
    ensureConfigLoaded();
    return myConfig != null && myConfig.isSubmodulePrefix(path);
  }

  /**
   * @return the current repository
   */
  public Repository getRepository() {
    return myDb;
  }

  /**
   * Get submodule url by it's path in current repository
   *
   * @param submodulePath path of submodule in current repository
   * @return submodule repository url or null if no submodules is registered for specified path
   */
  public String getSubmoduleUrl(String submodulePath) {
    ensureConfigLoaded();
    if (myConfig != null) {
      Submodule submodule = myConfig.findSubmodule(submodulePath);
      return submodule != null ? submodule.getUrl() : null;
    } else {
      return null;
    }
  }

  /**
   * Ensure that submodule configuration has been loaded.
   */
  private void ensureConfigLoaded() {
    if (myConfig == null) {
      try {
        myConfig = new SubmodulesConfig(myDb.getConfig(), new BlobBasedConfig(null, myDb, myCommit, ".gitmodules"));
      } catch (FileNotFoundException e) {
        // do nothing
      } catch (Exception e) {
        LOG.error("Unable to load or parse submodule configuration at: " + myCommit.getId().name(), e);
      }
    }
  }
}
