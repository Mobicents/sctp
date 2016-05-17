package org.mobicents.protocols.sctp.multiclient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.spi.AbstractSelectableChannel;

import javolution.xml.XMLFormat;
import javolution.xml.stream.XMLStreamException;

import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.PayloadData;

/**
 * Abstract super class for associations.
 * It represents an SCTP association with an interface which provides management functionality 
 * like: start, stop, reconnect. 
 * It also defines static classes to describe associations: PeerAddressInfo, HostAddressInfo, 
 * AssociationInfo used by other classes to identify and compare association objects. 
 * 
 * @author balogh.gabor@alerant.hu
 *
 */

public abstract class ManageableAssociation implements Association {
	private static final String NAME = "name";
	private static final String SERVER_NAME = "serverName";
	private static final String HOST_ADDRESS = "hostAddress";
	private static final String HOST_PORT = "hostPort";

	private static final String PEER_ADDRESS = "peerAddress";
	private static final String PEER_PORT = "peerPort";

	private static final String EXTRA_HOST_ADDRESS = "extraHostAddress";
	private static final String EXTRA_HOST_ADDRESS_SIZE = "extraHostAddresseSize";

	protected MultiManagementImpl management;
	protected String hostAddress;
	protected int hostPort;
	protected String peerAddress;
	protected int peerPort;
	protected String name;
	protected String[] extraHostAddresses;
	protected AssociationInfo assocInfo;
	
	//payload data used in the first dummy message which initiate the connect procedure
	protected PayloadData initPayloadData = new PayloadData(0, new byte[1], true, false, 0, 0);

	/**
	 * This is used only for SCTP This is the socket address for peer which will
	 * be null initially. If the Association has multihome support and if peer
	 * address changes, this variable is set to new value so new messages are
	 * now sent to changed peer address
	 */
	protected volatile SocketAddress peerSocketAddress = null;
	
	protected abstract void start() throws Exception;
	protected abstract void stop() throws Exception;
	protected abstract AbstractSelectableChannel getSocketChannel();
	protected abstract void close();
	protected abstract void reconnect();
	protected abstract boolean writePayload(PayloadData payloadData);
	protected abstract void readPayload(PayloadData payloadData);

	protected ManageableAssociation() {
		
	}

	protected ManageableAssociation(String hostAddress, int hostPort, String peerAddress, int peerPort, String assocName,
			String[] extraHostAddresses) throws IOException {
		this.hostAddress = hostAddress;
		this.hostPort = hostPort;
		this.peerAddress = peerAddress;
		this.peerPort = peerPort;
		this.name = assocName;
		this.extraHostAddresses = extraHostAddresses;
		initDerivedFields();
	}

	protected void initDerivedFields() throws IOException {
		this.peerSocketAddress =  new InetSocketAddress(InetAddress.getByName(peerAddress), peerPort);
		String secondaryHostAddress = null;
		if (extraHostAddresses != null && extraHostAddresses.length >= 1) {
			secondaryHostAddress = extraHostAddresses[0];
		}
		this.assocInfo = new AssociationInfo(new PeerAddressInfo(peerSocketAddress),
				 new HostAddressInfo(hostAddress, secondaryHostAddress, hostPort));
	}

	protected void setManagement(MultiManagementImpl management) {
		this.management = management;
	}
	
	protected AssociationInfo getAssocInfo() {
		return assocInfo;
	}
	
	protected void setAssocInfo(AssociationInfo assocInfo) {
		this.assocInfo = assocInfo;
	}	
	
	protected void assignSctpAssociationId(int id) {
		this.assocInfo.getPeerInfo().setSctpAssocId(id);
	}
	
	protected boolean isConnectedToPeerAddresses(String peerAddresses) {
		return peerAddresses.contains(getAssocInfo().getPeerInfo().getPeerSocketAddress().toString());
	}
	
	protected PayloadData getInitPayloadData() {
		return initPayloadData;
	}
	
	protected void setInitPayloadData(PayloadData initPayloadData) {
		this.initPayloadData = initPayloadData;
	}

	static class PeerAddressInfo {
		protected SocketAddress peerSocketAddress;
		protected int sctpAssocId;
		
		public PeerAddressInfo(SocketAddress peerSocketAddress) {
			super();
			this.peerSocketAddress = peerSocketAddress;
		}

		public SocketAddress getPeerSocketAddress() {
			return peerSocketAddress;
		}

		public int getSctpAssocId() {
			return sctpAssocId;
		}

		protected void setPeerSocketAddress(SocketAddress peerSocketAddress) {
			this.peerSocketAddress = peerSocketAddress;
		}

		protected void setSctpAssocId(int sctpAssocId) {
			this.sctpAssocId = sctpAssocId;
		}

		@Override
		public String toString() {
			return "PeerAddressInfo [peerSocketAddress=" + peerSocketAddress
					+ ", sctpAssocId=" + sctpAssocId + "]";
		}
	}
	
	static class HostAddressInfo {
		private final String primaryHostAddress;
		private final String secondaryHostAddress;
		private final int hostPort;
		
			
		public HostAddressInfo(String primaryHostAddress,
				String secondaryHostAddress, int hostPort) {
			super();
			if (primaryHostAddress == null || primaryHostAddress.isEmpty()) {
				throw new IllegalArgumentException("Constructor HostAddressInfo: primaryHostAddress can not be null!");
			}
			this.primaryHostAddress = primaryHostAddress;
			this.secondaryHostAddress = secondaryHostAddress;
			this.hostPort = hostPort;
		}
		public String getPrimaryHostAddress() {
			return primaryHostAddress;
		}
		
		public String getSecondaryHostAddress() {
			return secondaryHostAddress;
		}
		
		public int getHostPort() {
			return hostPort;
		}
				
		public boolean matches(HostAddressInfo hostAddressInfo) {
			if (hostAddressInfo == null) {
				return false;
			}
			if (this.hostPort != hostAddressInfo.getHostPort()) {
				return false;
			}
			if (this.equals(hostAddressInfo)) {
				return true;
			}
			if (this.getPrimaryHostAddress().equals(hostAddressInfo.getPrimaryHostAddress())
					|| this.getPrimaryHostAddress().equals(hostAddressInfo.getSecondaryHostAddress())) {
				return true;
			}
			if (this.getSecondaryHostAddress() != null && !this.getSecondaryHostAddress().isEmpty()) {
				if (this.getSecondaryHostAddress().equals(hostAddressInfo.getPrimaryHostAddress()) 
					|| this.getSecondaryHostAddress().equals(hostAddressInfo.getSecondaryHostAddress())) {
					return true; 
				}
			}
			return false;
		}
		
		@Override
		public String toString() {
			return "HostAddressInfo [primaryHostAddress=" + primaryHostAddress
					+ ", secondaryHostAddress=" + secondaryHostAddress
					+ ", hostPort=" + hostPort + "]";
		}
		
	}
	static class AssociationInfo {		
		protected PeerAddressInfo peerInfo;
		protected HostAddressInfo hostInfo;
		public PeerAddressInfo getPeerInfo() {
			return peerInfo;
		}
		public HostAddressInfo getHostInfo() {
			return hostInfo;
		}
		@Override
		public String toString() {
			return "AssociationInfo [peerInfo=" + peerInfo + ", hostInfo="
					+ hostInfo + "]";
		}
		public AssociationInfo(PeerAddressInfo peerInfo,
				HostAddressInfo hostInfo) {
			super();
			this.peerInfo = peerInfo;
			this.hostInfo = hostInfo;
		}
		protected void setPeerInfo(PeerAddressInfo peerInfo) {
			this.peerInfo = peerInfo;
		}
		protected void setHostInfo(HostAddressInfo hostInfo) {
			this.hostInfo = hostInfo;
		}

	}
	
	/**
	 * XML Serialization/Deserialization
	 */
	protected static final XMLFormat<ManageableAssociation> ASSOCIATION_XML = new XMLFormat<ManageableAssociation>(
			ManageableAssociation.class) {

		@Override
		public void read(javolution.xml.XMLFormat.InputElement xml, ManageableAssociation association)
				throws XMLStreamException {
			association.name = xml.getAttribute(NAME, "");
			association.hostAddress = xml.getAttribute(HOST_ADDRESS, "");
			association.hostPort = xml.getAttribute(HOST_PORT, 0);

			association.peerAddress = xml.getAttribute(PEER_ADDRESS, "");
			association.peerPort = xml.getAttribute(PEER_PORT, 0);


			int extraHostAddressesSize = xml.getAttribute(EXTRA_HOST_ADDRESS_SIZE, 0);
			association.extraHostAddresses = new String[extraHostAddressesSize];

			for (int i = 0; i < extraHostAddressesSize; i++) {
				association.extraHostAddresses[i] = xml.get(EXTRA_HOST_ADDRESS, String.class);
			}

		}

		@Override
		public void write(ManageableAssociation association, javolution.xml.XMLFormat.OutputElement xml)
				throws XMLStreamException {
			xml.setAttribute(NAME, association.name);
			//xml.setAttribute(ASSOCIATION_TYPE, association.type.getType());
			xml.setAttribute(HOST_ADDRESS, association.hostAddress);
			xml.setAttribute(HOST_PORT, association.hostPort);

			xml.setAttribute(PEER_ADDRESS, association.peerAddress);
			xml.setAttribute(PEER_PORT, association.peerPort);

			xml.setAttribute(SERVER_NAME,"");
			//xml.setAttribute(IPCHANNEL_TYPE, IpChannelType.SCTP);

			xml.setAttribute(EXTRA_HOST_ADDRESS_SIZE,
					association.extraHostAddresses != null ? association.extraHostAddresses.length : 0);
			if (association.extraHostAddresses != null) {
				for (String s : association.extraHostAddresses) {
					xml.add(s, EXTRA_HOST_ADDRESS, String.class);
				}
			}
		}
	};
}
