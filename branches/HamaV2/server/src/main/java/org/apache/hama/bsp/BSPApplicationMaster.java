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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.ipc.RPC;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.yarn.Clock;
import org.apache.hadoop.yarn.SystemClock;
import org.apache.hadoop.yarn.api.AMRMProtocol;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.protocolrecords.RegisterApplicationMasterRequest;
import org.apache.hadoop.yarn.api.protocolrecords.RegisterApplicationMasterResponse;
import org.apache.hadoop.yarn.api.records.ApplicationAttemptId;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnRemoteException;
import org.apache.hadoop.yarn.ipc.YarnRPC;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.apache.hadoop.yarn.util.Records;
import org.apache.hama.HamaConfiguration;
import org.apache.hama.bsp.sync.SyncServer;
import org.apache.hama.bsp.sync.SyncServerImpl;
import org.apache.mina.util.AvailablePortFinder;

/**
 * BSPApplicationMaster is an application master for Apache Hamas BSP Engine.
 */
public class BSPApplicationMaster {

	private static final Log LOG = LogFactory
			.getLog(BSPApplicationMaster.class);
	private static final ExecutorService threadPool = Executors
			.newFixedThreadPool(1);

	private Configuration localConf;
	private Configuration jobConf;

	private FileSystem fs;
	private Clock clock;
	private YarnRPC yarnRPC;
	private AMRMProtocol amrmRPC;

	private ApplicationId appId;
	private ApplicationAttemptId appAttemptId;
	private String applicationName;
	private String userName;
	private long startTime;

	private BSPJob job;

	private SyncServerImpl syncServer;
	private Future<Long> syncServerFuture;

	// RPC info where the AM receive client side requests
	private String hostname;
	private int clientPort;

	private RegisterApplicationMasterResponse applicationMasterResponse;

	private BSPApplicationMaster(String[] args) throws IOException {
		if (args.length != 1) {
			throw new IllegalArgumentException();
		}

		localConf = new YarnConfiguration();
		jobConf = getSubmitConfiguration(args[0]);

		applicationName = jobConf.get("bsp.job.name",
				"<no bsp job name defined>");
		userName = jobConf.get("user.name", "<no user defined>");

		appId = Records.newRecord(ApplicationId.class);
		appAttemptId = getApplicationAttemptId();

		yarnRPC = YarnRPC.create(localConf);
		fs = FileSystem.get(localConf);
		clock = new SystemClock();
		startTime = clock.getTime();

		// TODO this is not localhost, is it?
		hostname = InetAddress.getLocalHost().getCanonicalHostName();
		startSyncServer();
		clientPort = getFreePort();

		amrmRPC = getYarnRPCConnection(localConf);
		applicationMasterResponse = registerApplicationMaster(amrmRPC,
				appAttemptId, hostname, clientPort, null);
	}

	/**
	 * This method starts the sync server on a specific port and waits for it to
	 * come up. Be aware that this method adds the "bsp.sync.server.address"
	 * that is needed for a task to connect to the service.
	 * 
	 * @throws IOException
	 */
	private void startSyncServer() throws IOException {
		int syncPort = getFreePort(15000);
		syncServer = new SyncServerImpl(jobConf.getInt("bsp.peers.num", 1),
				hostname, syncPort);
		syncServerFuture = threadPool.submit(syncServer);
		// wait for the RPC to come up
		InetSocketAddress syncAddress = NetUtils.createSocketAddr(hostname
				+ ":" + syncPort);
		LOG.info("Waiting for the Sync Master at " + syncAddress);
		RPC.waitForProxy(SyncServer.class, SyncServer.versionID, syncAddress,
				jobConf);
		jobConf.set("bsp.sync.server.address", hostname + ":" + syncPort);
	}

	/**
	 * Uses Minas AvailablePortFinder to find a port, starting at 14000.
	 * 
	 * @return a free port.
	 */
	private int getFreePort() {
		int startPort = 14000;
		return getFreePort(startPort);
	}

	/**
	 * Uses Minas AvailablePortFinder to find a port, starting at startPort.
	 * 
	 * @return a free port.
	 */
	private int getFreePort(int startPort) {
		while (!AvailablePortFinder.available(startPort)) {
			startPort++;
			LOG.debug("Testing port for availability: " + startPort);
		}
		return startPort;
	}

	/**
	 * Connects to the Resource Manager.
	 * 
	 * @param yarnConf
	 * @return a new RPC connection to the Resource Manager.
	 */
	private AMRMProtocol getYarnRPCConnection(Configuration yarnConf) {
		// Connect to the Scheduler of the ResourceManager.
		InetSocketAddress rmAddress = NetUtils.createSocketAddr(yarnConf.get(
				YarnConfiguration.RM_SCHEDULER_ADDRESS,
				YarnConfiguration.DEFAULT_RM_SCHEDULER_ADDRESS));
		LOG.info("Connecting to ResourceManager at " + rmAddress);
		return (AMRMProtocol) yarnRPC.getProxy(AMRMProtocol.class, rmAddress,
				yarnConf);
	}

	/**
	 * Registers this application master with the Resource Manager and retrieves
	 * a response which is used to launch additional containers.
	 * 
	 * @throws YarnRemoteException
	 */
	private RegisterApplicationMasterResponse registerApplicationMaster(
			AMRMProtocol resourceManager, ApplicationAttemptId appAttemptID,
			String appMasterHostName, int appMasterRpcPort,
			String appMasterTrackingUrl) throws YarnRemoteException {

		RegisterApplicationMasterRequest appMasterRequest = Records
				.newRecord(RegisterApplicationMasterRequest.class);
		appMasterRequest.setApplicationAttemptId(appAttemptID);
		appMasterRequest.setHost(appMasterHostName);
		appMasterRequest.setRpcPort(appMasterRpcPort);
		// TODO tracking URL
		// appMasterRequest.setTrackingUrl(appMasterTrackingUrl);
		RegisterApplicationMasterResponse response = resourceManager
				.registerApplicationMaster(appMasterRequest);
		LOG.debug("ApplicationMaster has maximum resource capability of: "
				+ response.getMaximumResourceCapability().getMemory());
		return response;
	}

	/**
	 * Gets the application attempt ID from the environment. This should be set
	 * by YARN when the container has been launched.
	 * 
	 * @return a new ApplicationAttemptId which is unique and identifies this
	 *         task.
	 */
	private ApplicationAttemptId getApplicationAttemptId() throws IOException {
		Map<String, String> envs = System.getenv();
		if (!envs.containsKey(ApplicationConstants.APPLICATION_ATTEMPT_ID_ENV)) {
			throw new IllegalArgumentException(
					"ApplicationAttemptId not set in the environment");
		}
		return ConverterUtils.toApplicationAttemptId(envs
				.get(ApplicationConstants.APPLICATION_ATTEMPT_ID_ENV));
	}

	private Configuration getSubmitConfiguration(String path) {
		Path jobSubmitPath = new Path(path);
		Configuration jobConf = new HamaConfiguration();
		jobConf.addResource(jobSubmitPath);
		return jobConf;
	}

	private void start() throws Exception {
		job = new BSPJobImpl(appAttemptId, jobConf, yarnRPC, amrmRPC);
		job.startJob();
	}

	public static void main(String[] args) {
		// TODO we expect getting the qualified path of the job.xml as the first
		// element in the arguments
		try {
			new BSPApplicationMaster(args).start();
		} catch (Exception e) {
			LOG.fatal("Error starting BSPApplicationMaster", e);
			System.exit(1);
		}
	}

}
