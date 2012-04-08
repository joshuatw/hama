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
package org.apache.hama.monitor.fd;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean; 
import java.util.concurrent.Callable; 
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import static java.util.concurrent.TimeUnit.*; 
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;

/**
 * UDP supervisor is responsible for receiving the 
 * heartbeat and output suspicion level for Interpreter.
 */
public class UDPSupervisor implements Supervisor, Runnable{

  public static final Log LOG = LogFactory.getLog(UDPSupervisor.class);

  private static int WINDOW_SIZE = 100;
  private static final List<Node> nodes = new CopyOnWriteArrayList<Node>();
  private final ScheduledExecutorService sched;
  private final DatagramChannel channel;
  private AtomicBoolean running = new AtomicBoolean(false);

  public static class Hermes implements Callable{
    private final Node node;
    private final long heartbeat;
    private final long sequence;

    /**
     * Logic unit deals with sensors' status. 
     * It first check if the packet coming is 1, indicating the arrival 
     * of new packet. Then it checkes the node position within the list, 
     * i.e., nodes. If -1 returns, a completely refresh packet arrives; 
     * therefore adding node info to the nodes list; otherwise reseting 
     * the node sampling window and the latest heartbeat value.
     * If the packet comes with the sequence other than 1, meaning its
     * status of continuous sending heartbeat, thus retrieve old node
     * from list and process necessary steps, such as manipulating sample 
     * windows and assigning the last heartbeat.
     * @param address of specific node equipted with sensor.
     * @param sequence number is generated by the sensor.
     * @param heartbeat timestamped by the supervisor.
     */
    public Hermes(String address, final long sequence, final long heartbeat){
      Node n = new Node(address, WINDOW_SIZE);
      int p = nodes.indexOf(n);
      Node tmp = null;
      if(1L == sequence) {
        if(-1 == p){// fresh
          tmp = n;
          nodes.add(tmp);
        }else{// node crashed then restarted
          tmp = nodes.get(p);
          tmp.reset();
        }
      }else{
        if(-1 == p){
          LOG.warn("Non existing host ("+address+") is sending heartbeat"+
          " sequence "+sequence+"!!!");
        }else{
          tmp = nodes.get(p);
        }
      }
      this.node = tmp;
      this.heartbeat = heartbeat;
      this.sequence = sequence;
    }

    @Override
    public Object call() throws Exception {
      this.node.add(this.heartbeat);
      return null;
    }
  }

  /**
   * UDP Supervisor.
   */
  public UDPSupervisor(Configuration conf){
    DatagramChannel ch = null;
    try{
      ch = DatagramChannel.open();
    }catch(IOException ioe){
      LOG.error("Fail to open udp channel.", ioe);
    }
    this.channel = ch;
    if(null == this.channel) throw new NullPointerException();
    try{
      this.channel.socket().bind((SocketAddress)new InetSocketAddress(
        conf.getInt("bsp.monitor.fd.udp_port", 16384)));
    }catch(SocketException se){
      LOG.error("Unable to bind the udp socket.", se);
    }
    WINDOW_SIZE = conf.getInt("bsp.monitor.fd.window_size", 100);
    sched = Executors.newScheduledThreadPool(conf.
      getInt("bsp.monitor.fd.supervisor_threads", 20));
  }

  /**
   * The output value represents the level of a node's status, 
   * Normally called by Interpretor.
   * @param addr to be checked. 
   * @return double value as the suspicion level of the endpoint.
   *         -1d indicates not found.
   */
  @Override
  public double suspicionLevel(String addr) {
    if(null == addr || "".equals(addr))
      throw new NullPointerException("Target address is not provided.");
    for(Node n: nodes){
      if(addr.equals(n.getAddress())) {
         return n.phi(System.currentTimeMillis());
      }
    }
    return -1d;
  }

  public void run(){
    ByteBuffer packet = ByteBuffer.allocate(8);
    try{
      running.set(true);
      while(running.get()){
        SocketAddress source = (InetSocketAddress)channel.receive(packet); 
        packet.flip();
        long seq = packet.getLong();
        packet.clear();
        if(LOG.isDebugEnabled()){
          LOG.debug("seqence: "+seq+" src address: "+
          ((InetSocketAddress)source).getAddress().getHostAddress());
        }
        sched.schedule(new Hermes(((InetSocketAddress)source).getAddress()
          .getHostAddress(), seq, System.currentTimeMillis()), 0, SECONDS); 
      }
    }catch(IOException ioe){
      LOG.error("Problem in receiving packet from channel.", ioe);
      Thread.currentThread().interrupt();
    }finally{
      if(null != this.channel) 
        try{ 
          this.channel.socket().close();
          this.channel.close(); 
        }catch(IOException ioe){ 
          LOG.error("Error closing supervisor channel.",ioe); 
        }
    }
  }  

  public void shutdown(){
    running.set(false);
    sched.shutdown();
  }

  public boolean isShutdown(){
    return this.channel.socket().isClosed() && !running.get();
  }
}
