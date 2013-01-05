package xie.mods.xm.file;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedList;

import cpw.mods.fml.common.network.NetworkModHandler;

import net.minecraft.network.TcpConnection;
import net.minecraft.network.packet.NetHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet250CustomPayload;

public class PacketXieContent extends Packet250CustomPayload {
    
	private final static int MAX_SIZE = 32659;
	
	public final static String CHANNEL = "XieContent";

	protected String filename;
	protected byte sequence;
	protected byte total;
	
	public PacketXieContent(String filename, byte sequence, byte total, byte[] data) {
		channel = CHANNEL;
		this.filename = filename;
		this.sequence = sequence;
		this.total = total;
		pack(data);
	}
	
	private void pack(byte[] d) {
		byte filenameLength = (byte) filename.length();
		length = 1 + filenameLength + 1 + 1 + d.length;
		ByteArrayOutputStream stream = new ByteArrayOutputStream(length);
		stream.write(filenameLength);
		stream.write(filename.getBytes(),0,filenameLength);
		stream.write(sequence);
		stream.write(total);
		stream.write(d,0,d.length);
		
		data = stream.toByteArray();
	}

	public static void packageAndSend(TcpConnection network, String filename, File file) throws IOException {
		FileInputStream stream = new FileInputStream(file);
		int size = MAX_SIZE - filename.length();
		byte buffer[] = new byte[size];
		int totalBytesRead = 0;
		
		LinkedList<byte[]> datum = new LinkedList<byte[]>();
		for (int bytesRead = stream.read(buffer); bytesRead>0; bytesRead = stream.read(buffer)) {
			ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
			byteStream.write(buffer,0,bytesRead);
			datum.add(byteStream.toByteArray());
			totalBytesRead+=bytesRead;
		}
		
		
		for (byte i=0; i<datum.size(); i++) {
			PacketXieContent packet = new PacketXieContent(filename,i,(byte)datum.size(),datum.get(i));
			network.addToSendQueue(packet.sendable());
		}
		
		stream.close();
	}

	public String dump(int i) {
		if (data==null) return "null packet";
		return new String(data,0,i);
	}

	public Packet sendable() {
		Packet250CustomPayload packet = new Packet250CustomPayload();
		
		packet.channel = this.channel;
		packet.length = this.length;
		packet.data = this.data;
		
		return packet;
	}
	
}
