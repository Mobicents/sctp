/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a full listing
 * of individual contributors.
 * 
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License, v. 2.0.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License,
 * v. 2.0 along with this distribution; if not, write to the Free 
 * Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 */

package org.mobicents.protocols.sctp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.List;

import javolution.util.FastList;
import javolution.xml.XMLFormat;
import javolution.xml.stream.XMLStreamException;

import org.apache.log4j.Logger;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.Server;

import com.sun.nio.sctp.SctpServerChannel;

/**
 * @author amit bhayani
 * @author sergey vetyutnev
 * 
 */
public class ServerImpl implements Server {

	private static final Logger logger = Logger.getLogger(ServerImpl.class.getName());

	private static final String NAME = "name";
	private static final String HOST_ADDRESS = "hostAddress";
	private static final String HOST_PORT = "hostPort";
	private static final String IPCHANNEL_TYPE = "ipChannelType";

	private static final String ASSOCIATIONS = "associations";

	private static final String STARTED = "started";

	private String name;
	private String hostAddress;
	private int hostport;
	private volatile boolean started = false;
	private IpChannelType ipChannelType;

	private ManagementImpl management = null;

	protected FastList<String> associations = new FastList<String>();

	// The channel on which we'll accept connections
	private SctpServerChannel serverChannelSctp;
	private ServerSocketChannel serverChannelTcp;

	/**
	 * 
	 */
	public ServerImpl() {
		super();
	}

	/**
	 * @param name
	 * @param ip
	 * @param port
	 * @throws IOException
	 */
	public ServerImpl(String name, String hostAddress, int hostport, IpChannelType ipChannelType) throws IOException {
		super();
		this.name = name;
		this.hostAddress = hostAddress;
		this.hostport = hostport;
		this.ipChannelType = ipChannelType;
	}

	protected void start() throws Exception {
		this.initSocket();
		this.started = true;

		if (logger.isInfoEnabled()) {
			logger.info(String.format("Started Server=%s", this.name));
		}
	}

	protected void stop() throws Exception {
		for (FastList.Node<String> n = this.associations.head(), end = this.associations.tail(); (n = n.getNext()) != end;) {
			String assocName = n.getValue();
			Association associationTemp = this.management.getAssociation(assocName);
			if (associationTemp.isStarted()) {
				throw new Exception(String.format("Stop all the associations first. Association=%s is still started",
						associationTemp.getName()));
			}
		}

		if (this.getIpChannel() != null) {
			try {
				this.getIpChannel().close();
			} catch (Exception e) {
				logger.warn(String.format("Error while stopping the Server=%s", this.name), e);
			}
		}

		this.started = false;

		if (logger.isInfoEnabled()) {
			logger.info(String.format("Stoped Server=%s", this.name));
		}
	}

	private void initSocket() throws IOException {

		if (this.ipChannelType == IpChannelType.SCTP)
			doInitSocketSctp();
		else
			doInitSocketTcp();

		// Register the server socket channel, indicating an interest in
		// accepting new connections
		// this.serverChannel.register(socketSelector, SelectionKey.OP_ACCEPT);

		FastList<ChangeRequest> pendingChanges = this.management.getPendingChanges();
		synchronized (pendingChanges) {

			// Indicate we want the interest ops set changed
			pendingChanges.add(new ChangeRequest(this.getIpChannel(), null, ChangeRequest.REGISTER,
					SelectionKey.OP_ACCEPT));
		}

		this.management.getSocketSelector().wakeup();
	}

	private void doInitSocketSctp() throws IOException {
		// Create a new non-blocking server socket channel
		this.serverChannelSctp = SctpServerChannel.open();
		this.serverChannelSctp.configureBlocking(false);

		// Bind the server socket to the specified address and port
		InetSocketAddress isa = new InetSocketAddress(this.hostAddress, this.hostport);
		this.serverChannelSctp.bind(isa);

		if (logger.isInfoEnabled()) {
			logger.info(String.format("SctpServerChannel bound to=%s ", serverChannelSctp.getAllLocalAddresses()));
		}
	}

	private void doInitSocketTcp() throws IOException {
		// Create a new non-blocking server socket channel
		this.serverChannelTcp = ServerSocketChannel.open();
		this.serverChannelTcp.configureBlocking(false);

		// Bind the server socket to the specified address and port
		InetSocketAddress isa = new InetSocketAddress(this.hostAddress, this.hostport);
		this.serverChannelTcp.bind(isa);

		if (logger.isInfoEnabled()) {
			logger.info(String.format("ServerSocketChannel bound to=%s ", serverChannelTcp.getLocalAddress()));
		}
	}

	public IpChannelType getIpChannelType() {
		return this.ipChannelType;
	}

	protected AbstractSelectableChannel getIpChannel() {
		if (this.ipChannelType == IpChannelType.SCTP)
			return this.serverChannelSctp;
		else
			return this.serverChannelTcp;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return the hostAddress
	 */
	public String getHostAddress() {
		return hostAddress;
	}

	/**
	 * @return the hostport
	 */
	public int getHostport() {
		return hostport;
	}

	/**
	 * @return the started
	 */
	public boolean isStarted() {
		return started;
	}

	/**
	 * @param management
	 *            the management to set
	 */
	public void setManagement(ManagementImpl management) {
		this.management = management;
	}

	/**
	 * @return the associations
	 */
	public List<String> getAssociations() {
		return associations.unmodifiable();
	}

	@Override
	public String toString() {

		StringBuilder sb = new StringBuilder();
		for (FastList.Node<String> n = this.associations.head(), end = this.associations.tail(); (n = n.getNext()) != end;) {
			if (sb.length() > 0)
				sb.append(", ");
			sb.append(n.getValue());
		}

		return "Server [name=" + name + ", hostAddress=" + hostAddress + ", hostPort=" + hostport + ", peerAddress="
				+ ", ipChannelType=" + ipChannelType + ", associations=[" + sb.toString() + "], started=" + started
				+ "]";
	}

	/**
	 * XML Serialization/Deserialization
	 */
	protected static final XMLFormat<ServerImpl> SERVER_XML = new XMLFormat<ServerImpl>(ServerImpl.class) {

		@SuppressWarnings("unchecked")
		@Override
		public void read(javolution.xml.XMLFormat.InputElement xml, ServerImpl server) throws XMLStreamException {
			server.name = xml.getAttribute(NAME, "");
			server.started = xml.getAttribute(STARTED, false);
			server.hostAddress = xml.getAttribute(HOST_ADDRESS, "");
			server.hostport = xml.getAttribute(HOST_PORT, 0);
			server.ipChannelType = IpChannelType.getInstance(xml.getAttribute(IPCHANNEL_TYPE,
					IpChannelType.SCTP.getCode()));
			if (server.ipChannelType == null)
				throw new XMLStreamException("Bad value for server.ipChannelType");

			server.associations = xml.get(ASSOCIATIONS, FastList.class);
		}

		@Override
		public void write(ServerImpl server, javolution.xml.XMLFormat.OutputElement xml) throws XMLStreamException {
			xml.setAttribute(NAME, server.name);
			xml.setAttribute(STARTED, server.started);
			xml.setAttribute(HOST_ADDRESS, server.hostAddress);
			xml.setAttribute(HOST_PORT, server.hostport);
			xml.setAttribute(IPCHANNEL_TYPE, server.ipChannelType.getCode());

			xml.add(server.associations, ASSOCIATIONS, FastList.class);
		}
	};
}
