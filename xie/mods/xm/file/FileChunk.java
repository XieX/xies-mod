package xie.mods.xm.file;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class FileChunk {
	
	public String filename;
	public byte sequence;
	public byte total;
	public byte[] data;
	
	private FileChunk() {
		
	}
	
	public static FileChunk compose(byte[] bytes) throws IOException, InvalidXiePacketException {
		ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
		
		byte filenameSize = (byte)stream.read();
		if (filenameSize<1) throw new InvalidXiePacketException();
		byte buffer[] = new byte[filenameSize];
		int read = stream.read(buffer);
		if (read!=filenameSize) throw new InvalidXiePacketException();
		String filename = new String(buffer);
		byte seq = (byte) stream.read();
		byte tot = (byte) stream.read();
		buffer = new byte[bytes.length];
		read = stream.read(buffer);
		
		FileChunk chunk = new FileChunk();
		chunk.filename = filename;
		chunk.sequence = seq;
		chunk.total = tot;
		chunk.data = new byte[read];
		for (int i=0; i<read; chunk.data[i]=buffer[i++]);
		
		return chunk;
	}
}
