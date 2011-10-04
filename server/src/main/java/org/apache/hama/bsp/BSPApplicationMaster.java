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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
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
import org.apache.mina.util.AvailablePortFinder;

/**
 * BSPApplicationMaster is an application master for Apache Hamas BSP Engine.
 */
public class BSPApplicationMaster {

	private static final Log LOG = LogFactory
			.getLog(BSPApplicationMaster.class);

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

	// RPC info where the AM receive client side requests
	private String hostname;
	private int port;

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
		// TODO this address of the client rpc server
		hostname = InetAddress.getLocalHost().getHostName();
		port = getFreePort();

		amrmRPC = registerWithResourceManager(localConf, appAttemptId,
				hostname, port, null);
	}

	private int getFreePort() {
		int startPort = 14000;
		while (!AvailablePortFinder.available(startPort)) {
			startPort++;
			LOG.debug("Testing port for availability: " + startPort);
		}
		return startPort;
	}

	private AMRMProtocol registerWithResourceManager(Configuration yarnConf,
			ApplicationAttemptId appAttemptID, String appMasterHostName,
			int appMasterRpcPort, String appMasterTrackingUrl)
			throws YarnRemoteException {
		// Connect to the Scheduler of the ResourceManager.
		InetSocketAddress rmAddress = NetUtils.createSocketAddr(yarnConf.get(
				YarnConfiguration.RM_SCHEDULER_ADDRESS,
				YarnConfiguration.DEFAULT_RM_SCHEDULER_ADDRESS));
		LOG.info("Connecting to ResourceManager at " + rmAddress);
		AMRMProtocol resourceManager = (AMRMProtocol) yarnRPC.getProxy(
				AMRMProtocol.class, rmAddress, yarnConf);

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
		return resourceManager;
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

	private void start() {

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
