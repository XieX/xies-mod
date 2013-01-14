package mod.xie.xm.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;


import argo.saj.InvalidSyntaxException;
import mod.xie.xm.Work;
import mod.xie.xm.file.FileChunk;
import mod.xie.xm.file.FileManager;
import mod.xie.xm.file.InvalidXiePacketException;
import mod.xie.xm.file.PacketXieContent;
import mod.xie.xm.file.PacketXieManifest;
import mod.xie.xm.file.PathManager;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.NetLoginHandler;
import net.minecraft.network.packet.NetHandler;
import net.minecraft.network.packet.Packet1Login;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.server.MinecraftServer;
import cpw.mods.fml.common.network.IConnectionHandler;
import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.Player;

public class XieNetworkManager implements IPacketHandler, IConnectionHandler {

	private XieNetworkManager() {};
	private static XieNetworkManager instance;
	public static XieNetworkManager get() {
		if (instance==null) instance = new XieNetworkManager();
		return instance;
	}
	
	private LinkedList<File> fileAcquisitionList;
	private FileManager fileManager;
	private File contentFileSMP;
	
	@Override
	public void onPacketData(INetworkManager network, Packet250CustomPayload packet, Player player) {
  //public void onPacketData(NetworkManager network, String channel, byte[] data) {
		if (packet.channel.equals(PacketXieManifest.CHANNEL)) {
			try {
				processConnectionToXieServer(network,packet.data);
			} catch (InvalidXiePacketException e) {
				System.err.println("Invalid Xie packet from server");
				e.printStackTrace();
			} catch (IOException e) {
				System.err.println("IO Error trying to read");
				e.printStackTrace();
			}
		} else if (packet.channel.equals(PacketXieContent.CHANNEL)) {
			try {
				FileChunk chunk = FileChunk.compose(packet.data);
				chunk.filename = PathManager.get().cacheDir() + chunk.filename.replace("./", "/");
				fileManager.add(chunk);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidXiePacketException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}


	@Override
	public String connectionReceived(NetLoginHandler netHandler, INetworkManager manager) {
		//MessageManager.getInstance().registerChannel(network, this, PacketXieManifest.CHANNEL);
		//MessageManager.getInstance().registerChannel(network, this, PacketXieContent.CHANNEL);
		//vanilla.restore();
		Work.instance().resetJobQueue();
		return "";
	}

	@Override
	public void connectionOpened(NetHandler netClientHandler, String server, int port, INetworkManager manager) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void connectionOpened(NetHandler netClientHandler, MinecraftServer server, INetworkManager manager) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void connectionClosed(INetworkManager manager) {
		ContentManager.get().restoreLocalContent();
	}

	@Override
	public void clientLoggedIn(NetHandler clientHandler,
			INetworkManager manager, Packet1Login login) {
		// TODO Auto-generated method stub
		
	}
	
	public void contentDownloadComplete() {
		try {
			ContentManager.get().loadWorkFromContentFile(contentFileSMP);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DataFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidSyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if (!Work.instance().doWork()) {
			String failMsgs[] = Work.instance().getFailMsgs();
			for (String s : failMsgs) System.err.println(s);
		}
		
	}
	
	private void processConnectionToXieServer(INetworkManager network, byte[] data) throws InvalidXiePacketException, IOException {
		contentFileSMP = null;
		fileAcquisitionList = new LinkedList<File>();
		
		String input = new String(data);
		
		String in[] = input.split(";");
		
		if (in==null) throw new InvalidXiePacketException();
		
		for (String fileEntry : in) {
			String fe[] = fileEntry.split(":");
			if (fe.length!=2) throw new InvalidXiePacketException();
			String filename=fe[0];
			long checksum;
			try {
				checksum = Long.parseLong(fe[1]);
			} catch (NumberFormatException e) {
				throw new InvalidXiePacketException();
			}
			
			checkForCacheFile(filename,checksum);
			if (filename.endsWith(".xix")) {
				contentFileSMP = new File(PathManager.get().cacheDir(),filename);
			}
		}
		
		if (fileAcquisitionList.isEmpty()) {
			contentDownloadComplete();
			return;
		}
		
		String filelist = "";
		for (File f : fileAcquisitionList) {
			filelist += PathManager.get().localiseToCache(f.getPath())+";";
		}
		filelist = filelist.substring(0,filelist.length()-1);
		
		PacketXieManifest packet = new PacketXieManifest(filelist);
		network.addToSendQueue(packet.sendable());
		
		fileManager = new FileManager(fileAcquisitionList);
	}
	
	private void checkForCacheFile(String filename, long checksum) {
		if (!filename.startsWith("/")) filename = "/" + filename;
		File filepath = new File(PathManager.get().cacheDir(), filename);
		if (fileMatches(filepath,checksum)) {
			return;
		}
		fileAcquisitionList.add(filepath);
	}

	@Override
	public void playerLoggedIn(Player player, NetHandler netHandler,
			INetworkManager manager) {
		// TODO Auto-generated method stub
		
	}

	protected boolean fileMatches(File file, long checksum) {
		long localChecksum;
		try {
			localChecksum = getChecksum(file);
		} catch (IOException e) {
			return false;
		}
		return localChecksum==checksum;
	}
	
	protected long getChecksum(File file) throws IOException {
		// calculate file's checksum
		if (!file.isFile()) throw new FileNotFoundException();
		
		InputStream stream;
		stream = new FileInputStream(file);
		
		
		int filesize = (int) file.length();
		byte buffer[] = new byte[filesize];
		stream.read(buffer);
		
		CRC32 chk = new CRC32();
		chk.update(buffer);
		
		stream.close();
		
		return chk.getValue();
	}
}
