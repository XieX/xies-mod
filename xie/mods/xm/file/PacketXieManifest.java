package xie.mods.xm.file;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet250CustomPayload;

public class PacketXieManifest {
    public String channel;

    public int length;

    public byte[] data;
    
	public final static String CHANNEL = "XieManifest";
	
	public String filelist;
	
	public PacketXieManifest(String filelist) {
		channel = CHANNEL;
		this.filelist = filelist;
		pack();
	}
	
	private void pack() {
		data = filelist.getBytes();
		length = data.length;
	}

	public Packet sendable() {
		Packet250CustomPayload packet = new Packet250CustomPayload();
		
		packet.channel = this.channel;
		packet.length = this.length;
		packet.data = this.data;
		
		return packet;
	}
}
