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

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import com.intellij.execution.configurations.GeneralCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.CheckoutCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public class CheckoutCommandImpl implements CheckoutCommand {

  private final GitCommandLine myCmd;
  private boolean myForce;
  private String myBranch;

  public CheckoutCommandImpl(@NotNull GitCommandLine cmd) {
    myCmd = cmd;
  }

  @NotNull
  public CheckoutCommand setForce(boolean force) {
    myForce = force;
    return this;
  }

  @NotNull
  public CheckoutCommand setBranch(@NotNull String branch) {
    myBranch = branch;
    return this;
  }

  public void call() throws VcsException {
    myCmd.addParameters("checkout", "-q");
    if (myForce)
      myCmd.addParameter("-f");
    myCmd.addParameter(myBranch);
    CommandUtil.runCommand(myCmd);
  }
}
