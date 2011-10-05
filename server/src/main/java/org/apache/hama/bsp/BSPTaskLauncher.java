/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hama.bsp;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.util.RunJar;
import org.apache.hadoop.yarn.api.ContainerManager;
import org.apache.hadoop.yarn.api.protocolrecords.GetContainerStatusRequest;
import org.apache.hadoop.yarn.api.protocolrecords.StartContainerRequest;
import org.apache.hadoop.yarn.api.protocolrecords.StopContainerRequest;
import org.apache.hadoop.yarn.api.records.Container;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.ContainerState;
import org.apache.hadoop.yarn.exceptions.YarnRemoteException;
import org.apache.hadoop.yarn.ipc.YarnRPC;
import org.apache.hadoop.yarn.util.Records;
import org.apache.hama.bsp.BSPTaskLauncher.BSPTaskStatus;

public class BSPTaskLauncher implements Callable<BSPTaskStatus> {

  private static final Log LOG = LogFactory.getLog(BSPTaskLauncher.class);

  private static final String SYSTEM_PATH_SEPARATOR = System
      .getProperty("path.separator");

  private final Container allocatedContainer;
  private final ContainerManager cm;
  private final Path jobFile;
  private final String user;
  private final Configuration conf;
  private final int id;

  public BSPTaskLauncher(int id, Container container, Configuration conf,
      YarnRPC rpc, Path jobFile) throws YarnRemoteException {
    this.id = id;
    this.jobFile = jobFile;
    this.user = conf.get("user.name");
    this.conf = conf;
    this.allocatedContainer = container;
    // Connect to ContainerManager on the allocated container
    String cmIpPortStr = container.getNodeId().getHost() + ":"
        + container.getNodeId().getPort();
    InetSocketAddress cmAddress = NetUtils.createSocketAddr(cmIpPortStr);
    cm = (ContainerManager) rpc.getProxy(ContainerManager.class, cmAddress,
        conf);
    LOG.info("Spawned task with id: " + this.id + " for allocated container id: "
        + this.allocatedContainer.getId().toString());
  }

  private File createWorkDirectory(Path jobFile) {
    File workDir = new File(jobFile.getParent().toString(), "work");
    boolean isCreated = workDir.mkdirs();
    if (isCreated) {
      LOG.info("TaskRunner.workDir : " + workDir);
    }
    return workDir;
  }

  @Override
  protected void finalize() throws Throwable {
    stopAndCleanup();
  }

  public void stopAndCleanup() throws YarnRemoteException {
    StopContainerRequest stopRequest = Records
        .newRecord(StopContainerRequest.class);
    stopRequest.setContainerId(allocatedContainer.getId());
    cm.stopContainer(stopRequest);
  }

  private List<String> buildJvmArgs(Configuration jobConf, String classPath,
      Class<?> child) throws IOException {
    // Build exec child jmv args.
    List<String> vargs = new ArrayList<String>();
    File jvm = // use same jvm as parent
    new File(new File(System.getProperty("java.home"), "bin"), "java");
    vargs.add(jvm.toString());

    // bsp.child.java.opts
    String javaOpts = jobConf.get("bsp.child.mem.in.mb", "-Xmx256m");
    javaOpts += " " + jobConf.get("bsp.child.java.opts", "");

    String[] javaOptsSplit = javaOpts.split(" ");
    for (int i = 0; i < javaOptsSplit.length; i++) {
      vargs.add(javaOptsSplit[i]);
    }

    // Add classpath.
    vargs.add("-classpath");
    vargs.add(classPath.toString());
    // Add main class and its arguments
    LOG.debug("Executing child Process " + child.getName());
    vargs.add(child.getName()); // main of bsppeer
    vargs.add(id +"");
    vargs.add(jobConf.get("bsp.sync.server.address"));
    vargs.add(this.jobFile.makeQualified(FileSystem.get(conf)).toString());

    return vargs;
  }

  // TODO for jars should use the containers methods
  private String assembleClasspath(Configuration jobConf, File workDir) {
    StringBuffer classPath = new StringBuffer();
    // start with same classpath as parent process
    classPath.append(System.getProperty("java.class.path"));
    classPath.append(SYSTEM_PATH_SEPARATOR);

    String jar = jobConf.get("bsp.jar");
    if (jar != null) { // if jar exists, it into workDir
      try {
        RunJar.unJar(new File(jar), workDir);
      } catch (IOException ioe) {
        LOG.error("Unable to uncompressing file to " + workDir.toString(), ioe);
      }
      File[] libs = new File(workDir, "lib").listFiles();
      if (libs != null) {
        for (int i = 0; i < libs.length; i++) {
          // add libs from jar to classpath
          classPath.append(SYSTEM_PATH_SEPARATOR);
          classPath.append(libs[i]);
        }
      }
      classPath.append(SYSTEM_PATH_SEPARATOR);
      classPath.append(new File(workDir, "classes"));
      classPath.append(SYSTEM_PATH_SEPARATOR);
      classPath.append(workDir);
    }
    return classPath.toString();
  }

  @Override
  public BSPTaskStatus call() throws Exception {
    // Now we setup a ContainerLaunchContext
    ContainerLaunchContext ctx = Records
        .newRecord(ContainerLaunchContext.class);

    ctx.setContainerId(allocatedContainer.getId());
    ctx.setResource(allocatedContainer.getResource());
    ctx.setUser(user);

    File workDir = createWorkDirectory(jobFile);
    String classPath = assembleClasspath(conf, workDir);
    LOG.info("Spawned child's classpath " + classPath);
    List<String> bspArgs = buildJvmArgs(conf, classPath,
        BSPPeerImpl.class);

    ctx.setCommands(bspArgs);
    StartContainerRequest startReq = Records
        .newRecord(StartContainerRequest.class);
    startReq.setContainerLaunchContext(ctx);
    cm.startContainer(startReq);

    GetContainerStatusRequest statusReq = Records
        .newRecord(GetContainerStatusRequest.class);
    statusReq.setContainerId(allocatedContainer.getId());

    while (cm.getContainerStatus(statusReq).getStatus().getState() != ContainerState.COMPLETE) {
      Thread.sleep(1000l);
    }

    return new BSPTaskStatus(id, cm.getContainerStatus(statusReq).getStatus()
        .getExitStatus());
  }

  public static class BSPTaskStatus {
    private final int id;
    private final int exitStatus;

    public BSPTaskStatus(int id, int exitStatus) {
      super();
      this.id = id;
      this.exitStatus = exitStatus;
    }

    public int getId() {
      return id;
    }

    public int getExitStatus() {
      return exitStatus;
    }
  }

}
