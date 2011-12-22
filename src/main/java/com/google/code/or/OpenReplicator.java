/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.code.or;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.code.or.binlog.BinlogEventListener;
import com.google.code.or.binlog.BinlogEventV4;
import com.google.code.or.binlog.BinlogParser;
import com.google.code.or.binlog.impl.BinlogParserImpl;
import com.google.code.or.binlog.impl.event.XidEvent;
import com.google.code.or.binlog.impl.parser.DeleteRowsEventParser;
import com.google.code.or.binlog.impl.parser.FormatDescriptionEventParser;
import com.google.code.or.binlog.impl.parser.IncidentEventParser;
import com.google.code.or.binlog.impl.parser.IntvarEventParser;
import com.google.code.or.binlog.impl.parser.QueryEventParser;
import com.google.code.or.binlog.impl.parser.RandEventParser;
import com.google.code.or.binlog.impl.parser.RotateEventParser;
import com.google.code.or.binlog.impl.parser.StopEventParser;
import com.google.code.or.binlog.impl.parser.TableMapEventParser;
import com.google.code.or.binlog.impl.parser.UpdateRowsEventParser;
import com.google.code.or.binlog.impl.parser.UserVarEventParser;
import com.google.code.or.binlog.impl.parser.WriteRowsEventParser;
import com.google.code.or.binlog.impl.parser.XidEventParser;
import com.google.code.or.common.glossary.column.StringColumn;
import com.google.code.or.common.logging.Log4jInitializer;
import com.google.code.or.io.impl.SocketFactoryImpl;
import com.google.code.or.net.Packet;
import com.google.code.or.net.Transport;
import com.google.code.or.net.TransportException;
import com.google.code.or.net.impl.AuthenticatorImpl;
import com.google.code.or.net.impl.TransportImpl;
import com.google.code.or.net.impl.packet.ErrorPacket;
import com.google.code.or.net.impl.packet.command.ComBinlogDumpPacket;

/**
 * 
 * @author Jingqi Xu
 */
public class OpenReplicator {
	//
	protected int port = 3306;
	protected String host;
	protected String user;
	protected String password;
	protected int serverId = 6789;
	protected int binlogPosition = 4;
	protected String binlogFileName;
	protected String encoding = "utf-8";
	protected int level1BufferSize = 1024 * 1024;
	protected int level2BufferSize = 8 * 1024 * 1024;
	
	//
	protected Transport transport;
	protected BinlogParser binlogParser;
	protected BinlogEventListener binlogEventListener;
	protected final AtomicBoolean running = new AtomicBoolean(false);
	
	/**
	 * 
	 */
	public boolean isRunning() {
		return this.running.get();
	}
	
	public void start() throws Exception {
		//
		if(!this.running.compareAndSet(false, true)) {
			return;
		}
		
		//
		if(this.transport == null) this.transport = getDefaultTransport();
		transport.connect(this.host, this.port);
		
		//
		dumpBinlog();
		
		//
		if(this.binlogParser == null) this.binlogParser = getDefaultBinlogParser();
		this.binlogParser.setBinlogEventListener(this.binlogEventListener);
		this.binlogParser.start(this.transport.getInputStream());
	}

	public void stop() throws Exception {
		//
		if(!this.running.compareAndSet(true, false)) {
			return;
		}
		
		//
		this.binlogParser.stop();
	}
	
	/**
	 * 
	 */
	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
	
	public String getEncoding() {
		return encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}
	
	public int getServerId() {
		return serverId;
	}

	public void setServerId(int serverId) {
		this.serverId = serverId;
	}
	
	public int getBinlogPosition() {
		return binlogPosition;
	}

	public void setBinlogPosition(int binlogPosition) {
		this.binlogPosition = binlogPosition;
	}
	
	public String getBinlogFileName() {
		return binlogFileName;
	}

	public void setBinlogFileName(String binlogFileName) {
		this.binlogFileName = binlogFileName;
	}
	
	public int getLevel1BufferSize() {
		return level1BufferSize;
	}

	public void setLevel1BufferSize(int level1BufferSize) {
		this.level1BufferSize = level1BufferSize;
	}

	public int getLevel2BufferSize() {
		return level2BufferSize;
	}

	public void setLevel2BufferSize(int level2BufferSize) {
		this.level2BufferSize = level2BufferSize;
	}

	/**
	 * 
	 */
	public Transport getTransport() {
		return transport;
	}

	public void setTransport(Transport transport) {
		this.transport = transport;
	}
	
	public BinlogParser getBinlogParser() {
		return binlogParser;
	}

	public void setBinlogParser(BinlogParser binlogParser) {
		this.binlogParser = binlogParser;
	}
	
	public BinlogEventListener getBinlogEventListener() {
		return binlogEventListener;
	}

	public void setBinlogEventListener(BinlogEventListener binlogEventListener) {
		this.binlogEventListener = binlogEventListener;
	}

	/**
	 * 
	 */
	protected void dumpBinlog() throws Exception {
		//
		final ComBinlogDumpPacket command = new ComBinlogDumpPacket();
		command.setBinlogFlag(0);
		command.setServerId(this.serverId);
		command.setBinlogPosition(this.binlogPosition);
		command.setBinlogFileName(StringColumn.valueOf(this.binlogFileName.getBytes(this.encoding)));
		this.transport.getOutputStream().writePacket(command);
		this.transport.getOutputStream().flush();
		
		//
		final Packet packet = this.transport.getInputStream().readPacket();
		if(packet.getPacketBody()[0] == ErrorPacket.PACKET_MARKER) {
			final ErrorPacket error = ErrorPacket.valueOf(packet);
			throw new TransportException(error);
		} 
	}
	
	protected Transport getDefaultTransport() throws Exception {
		//
		final TransportImpl r = new TransportImpl();
		r.setLevel1BufferSize(this.level1BufferSize);
		r.setLevel2BufferSize(this.level2BufferSize);
		
		//
		final AuthenticatorImpl authenticator = new AuthenticatorImpl();
		authenticator.setUser(this.user);
		authenticator.setPassword(this.password);
		authenticator.setEncoding(this.encoding);
		r.setAuthenticator(authenticator);
		
		//
		final SocketFactoryImpl socketFactory = new SocketFactoryImpl();
		socketFactory.setKeepAlive(true);
		socketFactory.setTcpNoDelay(false);
		socketFactory.setReceiveBufferSize(512 * 1024);
		r.setSocketFactory(socketFactory);
		return r;
	}
	
	protected BinlogParser getDefaultBinlogParser() throws Exception {
		final BinlogParserImpl r = new BinlogParserImpl();
		r.registgerEventParser(new StopEventParser());
		r.registgerEventParser(new RotateEventParser());
		r.registgerEventParser(new IntvarEventParser());
		r.registgerEventParser(new XidEventParser());
		r.registgerEventParser(new RandEventParser());
		r.registgerEventParser(new QueryEventParser());
		r.registgerEventParser(new UserVarEventParser());
		r.registgerEventParser(new IncidentEventParser());
		r.registgerEventParser(new TableMapEventParser());
		r.registgerEventParser(new WriteRowsEventParser());
		r.registgerEventParser(new DeleteRowsEventParser());
		r.registgerEventParser(new UpdateRowsEventParser());
		r.registgerEventParser(new FormatDescriptionEventParser());
		return r;
	}

	/**
	 * 
	 */
	public static void main(String args[]) throws Exception {
		//
		Log4jInitializer.initialize();
		
		//
		final OpenReplicator or = new OpenReplicator();
		or.setUser("xjq");
		or.setPassword("123456");
		or.setHost("localhost");
		or.setPort(3306);
		or.setServerId(6789);
		or.setBinlogPosition(4);
		or.setBinlogFileName("mysql_bin.000028");
		or.setBinlogEventListener(new BinlogEventListener() {
			public void onEvents(BinlogEventV4 event) {
				if(event instanceof XidEvent) {
					System.out.println(event);
				}
			}
		});
		or.start();
		
		//
		System.out.println("press 'q' to stop");
		final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		for(String line = br.readLine(); line != null; line = br.readLine()) {
			if(line.equals("q")) {
				or.stop();
				break;
			}
		}
	}
}