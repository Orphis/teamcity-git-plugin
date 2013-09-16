package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.SubmoduleListCommand;
import jetbrains.buildServer.vcs.VcsException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SubmoduleListCommandImpl implements SubmoduleListCommand {

  private final GitCommandLine myCmd;

  public SubmoduleListCommandImpl(GitCommandLine myCmd) {
    this.myCmd = myCmd;
  }

  public Map<String, String> call() {
    myCmd.addParameter("submodule");
    try {
      ExecResult result = CommandUtil.runCommand(myCmd);
      return parse(result.getStdout());
    } catch (VcsException e) {
      return Collections.emptyMap();
    }
  }

  private Map<String, String> parse(String str) {
    Map<String, String> result = new HashMap<String, String>();
    for (String line : StringUtil.splitByLines(str)) {
      if (line.length() < 41)
        continue;
      String[] array = line.trim().split("\\s+");
      String commit = array[0];
      if(commit.length() == 41)
        commit = commit.substring(1);
      String ref = array[1];
      result.put(ref, commit);
    }
    return result;
  }
}