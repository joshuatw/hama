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

import java.net.InetSocketAddress;
import java.util.concurrent.Callable;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.yarn.api.ContainerManager;
import org.apache.hadoop.yarn.api.records.Container;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.ipc.YarnRPC;
import org.apache.hadoop.yarn.util.Records;

public class BSPTaskLauncher implements Callable<Integer> {

	private final Container allocatedContainer;

	public BSPTaskLauncher(Container container, Configuration conf, YarnRPC rpc) {
		this.allocatedContainer = container;
		// Connect to ContainerManager on the allocated container
		String cmIpPortStr = container.getNodeId().getHost() + ":"
				+ container.getNodeId().getPort();
		InetSocketAddress cmAddress = NetUtils.createSocketAddr(cmIpPortStr);
		ContainerManager cm = (ContainerManager) rpc.getProxy(
				ContainerManager.class, cmAddress, conf);

		// Now we setup a ContainerLaunchContext
		ContainerLaunchContext ctx = Records
				.newRecord(ContainerLaunchContext.class);

		ctx.setContainerId(container.getId());
		ctx.setResource(container.getResource());
		// TODO set the commands and stuff
	}

	@Override
	public Integer call() throws Exception {
		// TODO just start the context and return a status for the task, maybe
		// we have to refactor this to an enum
		return 0;
	}

}
