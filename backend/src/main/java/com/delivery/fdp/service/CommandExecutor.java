package com.delivery.fdp.service;

import org.springframework.stereotype.Service;
import java.io.*;

@Service
public class CommandExecutor {

 public String execute(String command){
  try{
   Process p=Runtime.getRuntime().exec(command);
   BufferedReader r=new BufferedReader(
    new InputStreamReader(p.getInputStream()));
   StringBuilder sb=new StringBuilder();
   String line;
   while((line=r.readLine())!=null){
    sb.append(line).append("\n");
   }
   return sb.toString();
  }catch(Exception e){
   return e.getMessage();
  }
 }
}