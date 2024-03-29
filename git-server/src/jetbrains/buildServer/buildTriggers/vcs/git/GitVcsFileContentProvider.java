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

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleAwareTreeIterator;
import jetbrains.buildServer.vcs.*;
import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeOptions;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.util.io.AutoCRLFOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;

/**
* @author dmitry.neverov
*/
public class GitVcsFileContentProvider implements VcsFileContentProvider {

  private static final Logger LOG = Logger.getInstance(GitVcsFileContentProvider.class.getName());
  private static final Logger PERFORMANCE_LOG = Logger.getInstance(GitVcsFileContentProvider.class.getName() + ".Performance");

  private final GitVcsSupport myVcs;
  private final ServerPluginConfig myConfig;

  public GitVcsFileContentProvider(@NotNull GitVcsSupport vcs,
                                   @NotNull ServerPluginConfig config) {
    myVcs = vcs;
    myConfig = config;
  }

  @NotNull
  public byte[] getContent(@NotNull VcsModification vcsModification,
                           @NotNull VcsChangeInfo change,
                           @NotNull VcsChangeInfo.ContentType contentType,
                           @NotNull VcsRoot vcsRoot) throws VcsException {
    String version = contentType == VcsChangeInfo.ContentType.BEFORE_CHANGE
                     ? change.getBeforeChangeRevisionNumber()
                     : change.getAfterChangeRevisionNumber();
    String file = change.getRelativeFileName();
    return getContent(file, vcsRoot, version);
  }

  @NotNull
  public byte[] getContent(@NotNull String filePath, @NotNull VcsRoot root, @NotNull String version) throws VcsException {
    OperationContext context = myVcs.createContext(root, "retrieving content");
    try {
      final long start = System.currentTimeMillis();
      Repository r = context.getRepository();
      final TreeWalk tw = new TreeWalk(r);
      final GitVcsRoot gitRoot = context.getGitRoot();
      try {
        logStartProcessingFile(gitRoot, version, filePath);
        final String rev = GitUtils.versionRevision(version);
        RevCommit c = myVcs.ensureCommitLoaded(context, gitRoot, rev);
        tw.setFilter(PathFilterGroup.createFromStrings(Collections.singleton(filePath)));
        tw.setRecursive(tw.getFilter().shouldBeRecursive());
        context.addTree(gitRoot, tw, r, c, true);
        if (!tw.next()) {
          throw new VcsFileNotFoundException("The file " + filePath + " could not be found in " + rev + gitRoot.debugInfo());
        }
        final byte[] data = loadObject(gitRoot, r, tw, 0);
        logFileContentLoaded(gitRoot, version, filePath, tw);
        return data;
      } finally {
        logPerformance(gitRoot, filePath, start);
        tw.release();
      }
    } catch (Exception e) {
      throw context.wrapException(e);
    } finally {
      context.close();
    }
  }

  private void logStartProcessingFile(@NotNull GitVcsRoot root, @NotNull String version, @NotNull String filePath) throws VcsException {
    if (LOG.isDebugEnabled() && myConfig.verboseGetContentLog()) {
      LOG.debug("Getting data from " + version + ":" + filePath + " for " + root.debugInfo());
    }
  }

  private void logFileContentLoaded(@NotNull GitVcsRoot root, @NotNull String version, @NotNull String filePath, @NotNull TreeWalk tw) {
    if (LOG.isDebugEnabled() && myConfig.verboseGetContentLog()) {
      LOG.debug("File retrieved " + version + ":" + filePath + " (hash = " + tw.getObjectId(0) + ") for " + root.debugInfo());
    }
  }

  private void logPerformance(@NotNull GitVcsRoot gitRoot, @NotNull String filePath, long start) {
    final long finish = System.currentTimeMillis();
    if (PERFORMANCE_LOG.isDebugEnabled()) {
      PERFORMANCE_LOG.debug("[getContent] root=" + gitRoot.debugInfo() + ", file=" + filePath + " took " + (finish - start) + "ms");
    }
  }

  /**
   * Load bytes that correspond to the position in the tree walker
   *
   * @param r   the initial repository
   * @param tw  the tree walker
   * @param nth the tree in the tree wailer
   * @return loaded bytes
   * @throws IOException if there is an IO error
   */
  private byte[] loadObject(@NotNull GitVcsRoot root, Repository r, TreeWalk tw, final int nth) throws IOException {
    ObjectId id = tw.getObjectId(nth);
    Repository objRep = getRepository(r, tw, nth);
    final String path = tw.getPathString();
    return loadObject(root, objRep, path, id);
  }

  /**
   * Load object by blob ID
   *
   * @param r    the repository
   * @param path the path (might be null)
   * @param id   the object id
   * @return the object's bytes
   * @throws IOException in case of IO problem
   */
  private byte[] loadObject(@NotNull GitVcsRoot root, Repository r, String path, ObjectId id) throws IOException {
    final ObjectLoader loader = r.open(id);
    if (loader == null) {
      throw new IOException("Unable to find blob " + id + (path == null ? "" : "(" + path + ")") + " in repository " + r);
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream((int) loader.getSize());
    OutputStream output = root.isAutoCrlf() ? new AutoCRLFOutputStream(out) : out;
    loader.copyTo(output);
    output.flush();
    return out.toByteArray();
  }

  /**
   * Get repository from tree walker
   *
   * @param r   the initial repository
   * @param tw  the tree walker
   * @param nth the position
   * @return the actual repository
   */
  private Repository getRepository(Repository r, TreeWalk tw, int nth) {
    Repository objRep;
    AbstractTreeIterator ti = tw.getTree(nth, AbstractTreeIterator.class);
    if (ti instanceof SubmoduleAwareTreeIterator) {
      objRep = ((SubmoduleAwareTreeIterator)ti).getRepository();
    } else {
      objRep = r;
    }
    return objRep;
  }
}
