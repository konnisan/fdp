package com.delivery.fdp.service;

import com.delivery.fdp.config.RuntimeProperties;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Map;

@Service
public class CommandExecutor {
    private final RuntimeProperties props;
    public CommandExecutor(RuntimeProperties props){this.props=props;}
    public Result execute(String command,Path cwd){return execute(command,cwd,Map.of());}
    public Result execute(String command,Path cwd,Map<String,String> env){if(!props.isExecutionEnabled())return new Result(0,"[DRY-RUN] "+command);try{boolean win=System.getProperty("os.name").toLowerCase().contains("win");ProcessBuilder b=win?new ProcessBuilder("cmd","/c",command):new ProcessBuilder("bash","-lc",command);if(cwd!=null)b.directory(cwd.toFile());b.redirectErrorStream(true);b.environment().putAll(env);Process p=b.start();StringBuilder out=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()))){String line;while((line=r.readLine())!=null)out.append(line).append('\n');}int code=p.waitFor();return new Result(code,out.toString());}catch(Exception e){return new Result(-1,e.getMessage());}}
    public record Result(int exitCode,String output){public boolean success(){return exitCode==0;}}
}
