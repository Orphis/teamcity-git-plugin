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

import com.intellij.openapi.util.text.StringUtil;
import java.io.File;
import java.util.Map;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Git Vcs Settings
 */
public class GitVcsRoot implements VcsRoot {

  private final MirrorManager myMirrorManager;
  private final VcsRoot myDelegate;
  private final URIish myRepositoryFetchURL;
  private final URIish myRepositoryFetchURLNoFixErrors;
  private final URIish myRepositoryPushURL;
  private final URIish myRepositoryPushURLNoFixErrors;
  private final String myRef;
  private final UserNameStyle myUsernameStyle;
  private final SubmodulesCheckoutPolicy mySubmodulePolicy;
  private final AuthSettings myAuthSettings;
  private final String myUsernameForTags;
  private final String myBranchSpec;
  private final boolean myAutoCrlf;
  private final boolean myReportTags;
  private File myCustomRepositoryDir;

  public GitVcsRoot(@NotNull final MirrorManager mirrorManager, @NotNull final VcsRoot root) throws VcsException {
    this(mirrorManager, root, root.getProperty(Constants.BRANCH_NAME));
  }

  public GitVcsRoot(@NotNull MirrorManager mirrorManager, @NotNull VcsRoot root, @Nullable String ref) throws VcsException {
    myMirrorManager = mirrorManager;
    myDelegate = root;
    myCustomRepositoryDir = getPath();
    myRef = ref;
    myUsernameStyle = readUserNameStyle();
    mySubmodulePolicy = readSubmodulesPolicy();
    myAuthSettings = new AuthSettings(this);
    myRepositoryFetchURL = myAuthSettings.createAuthURI(getProperty(Constants.FETCH_URL));
    myRepositoryFetchURLNoFixErrors = myAuthSettings.createAuthURI(getProperty(Constants.FETCH_URL), false);
    String pushUrl = getProperty(Constants.PUSH_URL);
    myRepositoryPushURL = StringUtil.isEmpty(pushUrl) ? myRepositoryFetchURL : myAuthSettings.createAuthURI(pushUrl);
    myRepositoryPushURLNoFixErrors = StringUtil.isEmpty(pushUrl) ? myRepositoryFetchURLNoFixErrors : myAuthSettings.createAuthURI(pushUrl, false);
    myUsernameForTags = getProperty(Constants.USERNAME_FOR_TAGS);
    myBranchSpec = getProperty(Constants.BRANCH_SPEC);
    myAutoCrlf = Boolean.valueOf(getProperty(Constants.SERVER_SIDE_AUTO_CRLF, "false"));
    myReportTags = Boolean.valueOf(getProperty(Constants.REPORT_TAG_REVISIONS, "false"));
  }

  public GitVcsRoot getRootForBranch(@NotNull String branch) throws VcsException {
    return new GitVcsRoot(myMirrorManager, myDelegate, branch);
  }

  @Nullable
  public String getBranchSpec() {
    return myBranchSpec;
  }

  @NotNull
  public PersonIdent getTagger(@NotNull Repository r) {
    if (myUsernameForTags == null)
      return new PersonIdent(r);
    return parseIdent();
  }

  private File getPath() {
    String path = getProperty(Constants.PATH);
    return path == null ? null : new File(path);
  }

  private UserNameStyle readUserNameStyle() {
    final String style = getProperty(Constants.USERNAME_STYLE);
    if (style == null) {
      return UserNameStyle.USERID;
    } else {
      return Enum.valueOf(UserNameStyle.class, style);
    }
  }

  private SubmodulesCheckoutPolicy readSubmodulesPolicy() {
    final String submoduleCheckout = getProperty(Constants.SUBMODULES_CHECKOUT);
    if (submoduleCheckout == null) {
      return SubmodulesCheckoutPolicy.IGNORE;
    } else {
      return Enum.valueOf(SubmodulesCheckoutPolicy.class, submoduleCheckout);
    }
  }

  /**
   * @return true if submodules should be checked out
   */
  public boolean isCheckoutSubmodules() {
    return mySubmodulePolicy == SubmodulesCheckoutPolicy.CHECKOUT ||
           mySubmodulePolicy == SubmodulesCheckoutPolicy.CHECKOUT_IGNORING_ERRORS ||
           mySubmodulePolicy == SubmodulesCheckoutPolicy.NON_RECURSIVE_CHECKOUT ||
           mySubmodulePolicy == SubmodulesCheckoutPolicy.NON_RECURSIVE_CHECKOUT_IGNORING_ERRORS;
  }

  public SubmodulesCheckoutPolicy getSubmodulesCheckoutPolicy() {
    return mySubmodulePolicy;
  }

  public UserNameStyle getUsernameStyle() {
    return myUsernameStyle;
  }

  public File getRepositoryDir() {
    String fetchUrl = getRepositoryFetchURL().toString();
    if (myCustomRepositoryDir != null) {
      return myCustomRepositoryDir.isAbsolute() ?
             myCustomRepositoryDir :
             new File(myMirrorManager.getBaseMirrorsDir(), myCustomRepositoryDir.getPath());
    }
    return myMirrorManager.getMirrorDir(fetchUrl);
  }

  public boolean isAutoCrlf() {
    return myAutoCrlf;
  }

  public boolean isReportTags() {
    return myReportTags;
  }

  /**
   * Set repository path
   *
   * @param file the path to set
   */
  public void setCustomRepositoryDir(File file) {
    myCustomRepositoryDir = file;
  }

  /**
   * @return the URL for the repository
   */
  public URIish getRepositoryFetchURL() {
    return myRepositoryFetchURL;
  }

  public URIish getRepositoryFetchURLNoFixedErrors() {
    return myRepositoryFetchURLNoFixErrors;
  }

  /**
   * @return the branch name
   */
  public String getRef() {
    return StringUtil.isEmptyOrSpaces(myRef) ? "master" : myRef;
  }

  /**
   * @return debug information that allows identify repository operation context
   */
  public String debugInfo() {
    return " (" + getRepositoryDir() + ", " + getRepositoryFetchURL().toString() + "#" + getRef() + ")";
  }

  /**
   * @return the push URL for the repository
   */
  public URIish getRepositoryPushURL() {
    return myRepositoryPushURL;
  }

  public URIish getRepositoryPushURLNoFixedErrors() {
    return myRepositoryPushURLNoFixErrors;
  }

  @NotNull
  public AuthSettings getAuthSettings() {
    return myAuthSettings;
  }

  /**
   * The style for user names
   */
  enum UserNameStyle {
    /**
     * Name (John Smith)
     */
    NAME,
    /**
     * User id based on email (jsmith)
     */
    USERID,
    /**
     * Email (jsmith@example.org)
     */
    EMAIL,
    /**
     * Name and Email (John Smith &ltjsmith@example.org&gt)
     */
    FULL
  }

  private PersonIdent parseIdent() {
    int emailStartIdx = myUsernameForTags.indexOf("<");
    if (emailStartIdx == -1)
      return new PersonIdent(myUsernameForTags, "");
    int emailEndIdx = myUsernameForTags.lastIndexOf(">");
    if (emailEndIdx < emailStartIdx)
      return new PersonIdent(myUsernameForTags, "");
    String username = myUsernameForTags.substring(0, emailStartIdx).trim();
    String email = myUsernameForTags.substring(emailStartIdx + 1, emailEndIdx);
    return new PersonIdent(username, email);
  }


  public VcsRoot getOriginalRoot() {
    return myDelegate;
  }

  public String getVcsName() {
    return myDelegate.getVcsName();
  }

  public String getProperty(String propertyName) {
    return myDelegate.getProperty(propertyName);
  }

  public String getProperty(String propertyName, String defaultValue) {
    return myDelegate.getProperty(propertyName, defaultValue);
  }

  public Map<String, String> getProperties() {
    return myDelegate.getProperties();
  }

  public String convertToString() {
    return myDelegate.convertToString();
  }

  public String convertToPresentableString() {
    return myDelegate.convertToPresentableString();
  }

  public long getPropertiesHash() {
    return myDelegate.getPropertiesHash();
  }

  public String getName() {
    return myDelegate.getName();
  }

  public long getId() {
    return myDelegate.getId();
  }

  public Map<String, String> getPublicProperties() {
    return myDelegate.getPublicProperties();
  }

  @Override
  public String toString() {
    return myDelegate.toString();
  }

  @NotNull
  public String describe(final boolean verbose) {
    return myDelegate.describe(verbose);
  }

  public boolean isOnGithub() {
    return "github.com".equals(myRepositoryFetchURL.getHost());
  }

  public boolean isSsh() {
    return myRepositoryFetchURL.getScheme() == null ||
           "ssh".equals(myRepositoryFetchURL.getScheme());
  }

  public boolean isHttp() {
    return "http".equals(myRepositoryFetchURL.getScheme()) ||
           "https".equals(myRepositoryFetchURL.getScheme());
  }
}
