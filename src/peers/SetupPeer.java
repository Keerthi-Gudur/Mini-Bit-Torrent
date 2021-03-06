package peers;

import java.util.Properties;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

import Logging.Logging;

import java.net.Socket;
import java.io.IOException;
import java.net.ServerSocket;

/*This class sets up the necessary class objects for a peer to participate in the network 
 * It creates a communication supervisor, a file manager and peer manager for each peer 
 * and establishes connections with the other peers ******/

public class SetupPeer implements Runnable {

	Peer peer;
	Properties commonConfig;
	LinkedList<Peer> otherPeers;
	Socket socket;
	Logging logrecord = new Logging();

	FileSupervisor fileSupervisor;
	PeerSupervisor peerSupervisor;
	ComSupervisor comSupervisor;
	Message msg;

	public AtomicBoolean isFileCompleted = new AtomicBoolean(false);
	public AtomicBoolean areNeighboursCompleted = new AtomicBoolean(false);
	public AtomicBoolean endProcess = new AtomicBoolean(false);
	Vector<ComSupervisor> socketsList = new Vector<ComSupervisor>();

	SetupPeer(Properties common_Config, LinkedList<Peer> all_Peers, Peer p) {
		this.peer = p;
		this.commonConfig = common_Config;
		this.otherPeers = new LinkedList<>(removeThisPeerFromAllPeersList(peer.getPeerID(), all_Peers));
		fileSupervisor = new FileSupervisor(peer, commonConfig, this);
		peerSupervisor = new PeerSupervisor(peer, otherPeers, commonConfig, this, fileSupervisor.sizeOfbitset);
	}

	public LinkedList<Peer> removeThisPeerFromAllPeersList(int peerID, LinkedList<Peer> allPeers) {
		LinkedList<Peer> others = new LinkedList<Peer>();
		for (Peer each : allPeers) {
			if (each.getPeerID() != peerID)
				others.add(each);
		}
		return others;
	}

	void startThreadForPeerManager() {
		Thread t = new Thread(peerSupervisor);
		t.start();
	}

	public void run() {

		System.out.println("create peer thread is running .....");
		try {
			ServerSocket serversoc = new ServerSocket(peer.getListeningPort());
			while (!endProcess.get()) {
				Socket clientSocket = serversoc.accept();
				ComSupervisor con = new ComSupervisor(clientSocket, peer, fileSupervisor, peerSupervisor);
				System.out.println("server listening");
				connect(con);
			}
		} catch (Exception e) {
//			System.exit(0);
			e.printStackTrace();
		}
	}

	/*
	 * this function opens a socket for each neighbor in the network and calls
	 * ComSupervisor to handle communication
	 */

	public void establishConnections(int id, LinkedList<Peer> allPeers) {
//		LinkedList<Peer> connectWith = new LinkedList<Peer>(peersToConnectTo(id, allPeers));
		LinkedList<Peer> connectWith = new LinkedList<Peer>();
		for (Peer each : allPeers) {
			if (each.getPeerID() == id)
				break;
			else {
				connectWith.add(each);
			}
		}
		
		for (Peer each : connectWith) {
			System.out.println("each = " + each);
			try {
				System.out.println("hname =" + each.getHostName() + " port =" + each.getListeningPort());
				Socket socket = new Socket(each.getHostName(), each.getListeningPort());
				ComSupervisor con = new ComSupervisor(socket, peer, fileSupervisor, peerSupervisor);
				System.out.println("at establish connections");
				connect(con);
			} catch (Exception e) {
				System.exit(0);
				e.printStackTrace();
			}
		}
	}

	synchronized void connect(ComSupervisor cs) {
		if (!socketsList.contains(cs)) {
			System.out.println("list currently contains: " + socketsList);
			System.out.println("adding to list: " + cs);
			socketsList.add(cs);
			new Thread(cs).start();
			try {
				wait(10);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void allNeighborsHaveFile() {
		areNeighboursCompleted.set(true);
		if (isFileCompleted.get() && areNeighboursCompleted.get()) {
			endProcess.set(true);
			logrecord.closeLogger();
//          System.exit(0);
		}
	}
	
	public synchronized void chokePeers(Collection<Integer> peerIDsToChoke) {
		if (peerIDsToChoke != null && !peerIDsToChoke.isEmpty()) {
			for (int currentPeer : peerIDsToChoke) {
				if (!socketsList.isEmpty()) {
					for (ComSupervisor temp : socketsList) {
						if (temp.remotePeerId.get() == currentPeer)
							temp.addToQueue(new Message("Choke"));
					}
				}
			}
		}
	}

//  method to broadcast that this piece has received a part
	public synchronized void havePart(int partindex) {
		System.out.println("in broadcast have part");
		for (ComSupervisor conn : socketsList) {
			conn.addToQueue(new Have(partindex));
			conn.addToQueue(new Message("Notinterested"));
//          if (!peerManager.stillInterested(conn.remotePeerId.get(), fileManager.partsWithPeer()))
//          {
//              conn.addToQueue(new NotInterested());
//          }
		}
	}

	public synchronized void unchokePeers(Collection<Integer> peerIDsToUnchoke) {
		if (peerIDsToUnchoke != null && !peerIDsToUnchoke.isEmpty()) {
			for (int currentPeer : peerIDsToUnchoke) {
				if (!socketsList.isEmpty()) {
					for (ComSupervisor temp : socketsList) {
						if (temp.remotePeerId.get() == currentPeer) {
							temp.addToQueue(new Message("Unchoke"));
//                        	System.out.println("sent msg to queue to unchoke "+currentPeer);
						}
					}
				}
			}
		}
	}
	/* the process should close when all have finished downloading the whole file */

	public void fileDownloadIsComplete() {
		peerSupervisor.peerFileCompleted.set(true);
		isFileCompleted.set(true);
		if (isFileCompleted.get() && areNeighboursCompleted.get()) {
			logrecord.fileComplete();
			endProcess.set(true);
			logrecord.closeLogger();
//           System.exit(0);
		}
	}

	public void closeSockets() {
		for (ComSupervisor com : socketsList) {
			try {
				com.socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
