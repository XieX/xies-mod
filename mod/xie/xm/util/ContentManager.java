package mod.xie.xm.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import mod.xie.xm.Work;
import mod.xie.xm.file.PathManager;
import net.minecraft.client.Minecraft;
import net.minecraft.src.ModLoader;

import argo.saj.InvalidSyntaxException;

public class ContentManager {
	
	private static final int BUFFER = 153600;
	
	private ContentManager() {}
	
	private static ContentManager instance;
	
	private ContentPackage localContent = new ContentPackage();
	private ContentPackage currentSMPContent = new ContentPackage();
	
	private ContentPackage contentLoader = localContent;
	
	public static ContentManager get() {
		if (instance==null) instance = new ContentManager();
		return instance;
	}
	
	public void restoreLocalContent() {
		currentSMPContent.undo();
		localContent.apply();
	}
	
	public void addLocalContent(XieContent x) {
		contentLoader.add(x);
	}
	
	public void setSMPContent(ContentPackage c) {
		localContent.undo();
		currentSMPContent = c;
	}

	// load work from a compressed compiled work file
	public void loadWorkFromContentFile(File file) throws IOException, DataFormatException, InvalidSyntaxException {
		FileInputStream reader = new FileInputStream(file);
		ArrayList<Byte> byteArray = new ArrayList<Byte>();
		for (int b = reader.read(); b>=0; b = reader.read()) byteArray.add((byte)b);
		reader.close();
		
		byte data[] = new byte[byteArray.size()];
		for (int i=0; i<byteArray.size(); i++) data[i]=byteArray.get(i);
		
		Inflater inflater = new Inflater();
		inflater.setInput(data);
		byte output[] = new byte[BUFFER];
		int fileLength = inflater.inflate(output);
		
		String work = new String(output,0,fileLength);
		
		Work.instance().findWork(new StringReader(work),file.getParent(),null);
	}
	
}
