package mod.xie.xm.file;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


public class BrokenFile {

	private boolean done;
	
	private String filename;
	private byte total;
	private byte datum[][];
	
	public BrokenFile(String filename, byte total) {
		done=false;
		this.filename = filename;
		this.total = total;
		datum = new byte[total][];
	}
	
	public BrokenFile(String filename, byte total, byte piece, byte[] data) {
		this(filename,total);
		setPiece(piece,data);
	}

	public void setPiece(byte piece, byte[] data) {
		if (done) return;
		
		datum[piece] = data;
		
		if (isComplete()) {
			try {
				construct();
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
			done = true;
		}
	}
	
	public boolean isDone() {
		return done;
	}
	
	private boolean isComplete() {
		for (int i=0; i<total; i++) {
			if (datum[i]==null) return false;
		}
		
		return true;
	}
	
	private void construct() throws IOException {
		if (!isComplete()) throw new IOException("File incomplete");
		
		File file = new File(PathManager.getMinecraftPath(),filename);
		if (file.exists()) file.delete();
		file.getParentFile().mkdirs();
		file.createNewFile();
		
		byte buffer[] = new byte[16384];
		FileOutputStream stream = new FileOutputStream(file);
		
		for (int i=0; i<total; i++) {
			ByteArrayInputStream bytes = new ByteArrayInputStream(datum[i]);
			for (int read=bytes.read(buffer); read>0; read=bytes.read(buffer)) {
				stream.write(buffer,0,read);
			}
			bytes.close();
		}
		
		stream.close();
	}
}
