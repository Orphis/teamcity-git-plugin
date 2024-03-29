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

import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.SubmoduleSyncCommand;
import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public interface GitFacade {

  @NotNull
  InitCommand init();

  @NotNull
  CreateBranchCommand createBranch();

  @NotNull
  DeleteBranchCommand deleteBranch();

  @NotNull
  DeleteTagCommand deleteTag();

  @NotNull
  AddRemoteCommand addRemote();

  @NotNull
  CleanCommand clean();

  @NotNull
  ResetCommand reset();

  @NotNull
  UpdateRefCommand updateRef();

  @NotNull
  CheckoutCommand checkout();

  @NotNull
  BranchCommand branch();

  @NotNull
  GetConfigCommand getConfig();

  @NotNull
  SetConfigCommand setConfig();

  @NotNull
  FetchCommand fetch();

  @NotNull
  LogCommand log();

  @NotNull
  SubmoduleInitCommand submoduleInit();

  @NotNull
  SubmoduleSyncCommand submoduleSync();

  @NotNull
  SubmoduleListCommand submoduleList();

  @NotNull
  SubmoduleUpdateCommand submoduleUpdate();

  @NotNull
  ShowRefCommand showRef();

  @NotNull
  VersionCommand version();

  @NotNull
  LsRemoteCommand lsRemote();
}
