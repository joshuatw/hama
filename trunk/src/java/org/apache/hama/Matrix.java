/**
 * Copyright 2007 The Apache Software Foundation
 *
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
package org.apache.hama;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.io.Text;

/**
 * A library for mathematical operations on matrices of double.
 */
public class Matrix extends AbstractMatrix {

  /**
   * Construct
   * 
   * @param conf configuration object
   */
  public Matrix(Configuration conf) {
    setConfiguration(conf);
  }

  /**
   * Construct an matrix
   * 
   * @param conf configuration object
   * @param matrixName the name of the matrix
   */
  public Matrix(Configuration conf, Text matrixName) {
    try {
      setConfiguration(conf);
      this.matrixName = matrixName;

      if (!admin.tableExists(matrixName)) {
        tableDesc = new HTableDescriptor(matrixName.toString());
        tableDesc.addFamily(new HColumnDescriptor(Constants.COLUMN.toString()));
        create();
      }

      table = new HTable(config, matrixName);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Construct an m-by-n constant matrix.
   * 
   * @param conf configuration object
   * @param m the number of rows.
   * @param n the number of columns.
   * @param s fill the matrix with this scalar value.
   */
  public Matrix(HBaseConfiguration conf, int m, int n, double s) {
    try {
      setConfiguration(conf);
      matrixName = RandomVariable.randMatrixName();

      if (!admin.tableExists(matrixName)) {
        tableDesc = new HTableDescriptor(matrixName.toString());
        tableDesc.addFamily(new HColumnDescriptor(Constants.COLUMN.toString()));
        create();
      }

      table = new HTable(config, matrixName);

      for (int i = 0; i < m; i++) {
        for (int j = 0; j < n; j++) {
          set(i, j, s);
        }
      }

      setDimension(m, n);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Generate matrix with random elements
   * 
   * @param conf configuration object
   * @param m the number of rows.
   * @param n the number of columns.
   * @return an m-by-n matrix with uniformly distributed random elements.
   */
  public static Matrix random(Configuration conf, int m, int n) {
    Matrix rand = new Matrix(conf, RandomVariable.randMatrixName());
    for (int i = 0; i < m; i++) {
      for (int j = 0; j < n; j++) {
        rand.set(i, j, RandomVariable.rand());
      }
    }

    rand.setDimension(m, n);
    return rand;
  }

  public Matrix add(Matrix B) {
    
    
    // TODO Auto-generated method stub
    return null;
  }

  public Matrix add(double alpha, Matrix B) {
    // TODO Auto-generated method stub
    return null;
  }

  public Matrix mult(Matrix B) {
    // TODO Auto-generated method stub
    return null;
  }

  public Matrix multAdd(double alpha, Matrix B, Matrix C) {
    // TODO Auto-generated method stub
    return null;
  }

  public double norm(Norm type) {
    // TODO Auto-generated method stub
    return 0;
  }

  public Matrix set(double alpha, Matrix B) {
    // TODO Auto-generated method stub
    return null;
  }

  public Matrix set(Matrix B) {
    // TODO Auto-generated method stub
    return null;
  }
}
