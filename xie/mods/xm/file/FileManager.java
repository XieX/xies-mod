package xie.mods.xm.file;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import xie.mods.xm.util.XieNetworkManager;


public class FileManager {

	private HashMap<String,BrokenFile> files;
	private List<String> fileList;	
	
	private FileManager() {
		files = new HashMap<String,BrokenFile>();
	}
	
	public FileManager(List<String> filelist) {
		this();
		this.fileList = filelist;
	}
	
	public FileManager(LinkedList<File> files) {
		this();
		List<String> filez = new LinkedList<String>();
		for (File f : files) filez.add(f.getPath());
		fileList = filez;
	}

	public void add(String filename, byte sequence, byte total, byte[] data) {
		if (!fileList.contains(filename)) {
			System.err.println("Unexpected file "+filename);
			System.err.print("Expecting ");
			for (String s : fileList) System.err.print(s+"  ");
			System.err.println();
			return;
		}
		
		if (files.containsKey(filename)) {
			files.get(filename).setPiece(sequence, data);
		} else {
			files.put(filename, new BrokenFile(filename, total, sequence, data));
		}
		
		if (complete()) XieNetworkManager.get().contentDownloadComplete();
	}
	
	public boolean complete() {
		if (!(files.size()==fileList.size())) return false;
		
		for (BrokenFile file : files.values()) {
			if (!file.isDone()) return false;
		}
		
		return true;
	}

	public void add(FileChunk chunk) {
		add(chunk.filename, chunk.sequence, chunk.total, chunk.data);
	}
}
