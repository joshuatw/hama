package org.apache.hama.bsp;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.ipc.VersionedProtocol;

public interface BSPClient extends VersionedProtocol {

  public static final int VERSION = 0;
  
  public LongWritable getCurrentSuperStep(); 
  
}
