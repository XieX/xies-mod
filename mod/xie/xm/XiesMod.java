package mod.xie.xm;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;


import com.google.common.eventbus.EventBus;

import mod.xie.xm.file.NameMapFile;
import mod.xie.xm.file.PathManager;
import mod.xie.xm.file.SpritesheetManager;
import mod.xie.xm.file.StyleFile;
import mod.xie.xm.file.XieLog;
import mod.xie.xm.parser.Job;
import mod.xie.xm.util.GameState;
import mod.xie.xm.util.ItemWrapper;
import mod.xie.xm.util.JobType;
import mod.xie.xm.util.Xie;
import mod.xie.xm.util.XieCraftingManager;
import mod.xie.xm.util.XieFuelManager;
import mod.xie.xm.util.XieGuiHandler;
import mod.xie.xm.world.XieWorldGenerator;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.src.ModLoader;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;
import argo.saj.InvalidSyntaxException;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.MetadataCollection;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.PostInit;
import cpw.mods.fml.common.Mod.PreInit;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.Mod.Init;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.versioning.ArtifactVersion;

@Mod(modid = "Xie_RuntimeContentLoader", name = "Xie's Mod", version = "beta")
@NetworkMod(clientSideRequired = true, serverSideRequired = false)
public class XiesMod {
	
	@SidedProxy(clientSide = "mod.xie.xm.ClientProxy", serverSide = "mod.xie.xm.ServerProxy")
	public static XieProxy proxy;
	private static XiesMod instance;
	
	// TODO Find a safer way to do this
	public static XiesMod get() {
		if (instance==null) {
			System.err.println("Xie's mod not yet initialised");
			return null;
		}
		return instance;
	}
	
	public XiesMod() {
		instance = this;
	}
	
	private boolean success = false;
	
	protected static final String validExtensions[] = {".xie",".xid"};

	protected ModMetadata modMetadata;
	
	private GameState localContent;
	
	@PreInit
	public void init(FMLPreInitializationEvent event) {
		// register handlers etc
		GameRegistry.registerFuelHandler(XieFuelManager.get());
		GameRegistry.registerCraftingHandler(XieCraftingManager.get());
		GameRegistry.registerWorldGenerator(XieWorldGenerator.get());
		NetworkRegistry.instance().registerGuiHandler(this, XieGuiHandler.get());
		
		// read in files and populate work queue
		loadConfigFiles();
		
		// Process work queue
		try {
			success = Work.instance().doWork();
		} catch (Exception e) {
			XieLog.log().logFailure(e.toString());
			e.printStackTrace();
		}
	}
	
	@Init
	public void load(FMLInitializationEvent event) {

	}
		
	@PostInit
	public void modsLoaded(FMLPostInitializationEvent event) {	
		// take another pass at the work queue, some jobs may have been dependent on other mods
		try {
			success = Work.instance().doWork();
			if (!success) Work.instance().logIncompletes();
		} finally {
			outputLogFile(success);
		}
				
		if (!success)
			System.err.println(toString()+" unresolved - "+Work.instance().dumpAll());
		
		// close logger
		XieLog.log().closeLogger();

		localContent = new GameState();
	
		proxy.preloadTextures();
    }
	    
	protected void loadConfigFiles() {
    	PathManager pm = PathManager.get();    	
    	
    	//getWorkFrom(pm.contentDir());
    	getWorkFrom(pm.modsDir());
		
		getConfigFrom(pm.configDir());
		
		// create map and style files if not found
		boolean newWork = false;
		if (!styleFound)
			if (StyleFile.WriteFile(new File(pm.configDir(),"/xielog.css")))
				newWork=true;
		
		if (!mapFound)
			if (NameMapFile.WriteFile(new File(pm.configDir(),"/names.map"))) 
				newWork=true;
	
		if (newWork) getConfigFrom(pm.configDir());
    }


	protected void getWorkFrom(File dir) {
    	LinkedList<File> searchPath = new LinkedList();
    	searchPath.push(dir);
    	do {
    		File current = searchPath.pop();
    		XieLog.log().config("Searching directory "+current.getPath());
    		
    		if (!isValidDir(current)) continue;
    		if (current.getName().endsWith(".zip")) {
    			readZipFile(current);
    			continue;
    		}
    		
    		File listing[] = current.listFiles();
    		if (listing==null) continue;
    		for (File f : listing) {
    			if (isValidDir(f)) {
    				searchPath.add(f);
    				continue;
    			} else if (isConfigFile(f)) {
    				XieLog.log().config("Found "+f.getPath());
    				try {
    		    		if (f.getName().endsWith(".xie")) Work.instance().findWork(f);
    		    		else if (f.getName().endsWith(".xid")) Work.instance().findIDs(new FileReader(f));
    				} catch (IOException e) {
    					errorLog("Error reading file "+f.getName());
    					e.printStackTrace();
    				} catch (InvalidSyntaxException e) {
    					errorLog("JSON Syntax error in file: "+f.getName()+". "+e.getLocalizedMessage());
    				}
    			}
    		}
    	} while (!searchPath.isEmpty());
	}
	
	private void readZipFile(File f) {
		XieLog.log().config("Opening zip file "+f.getPath());
		
		ZipFile zip;

		try {
			zip = new ZipFile(f);
		} catch (Exception e1) {
			errorLog("Could not read zip file: "+f.getName());
			e1.printStackTrace();
			return;
		}
		
		String zipFileName = f.getPath().replace('\\', '/');
		String filePath = zipFileName.substring(0,zipFileName.lastIndexOf(".zip"));
		
		Enumeration<? extends ZipEntry> entries = zip.entries();
		while (entries.hasMoreElements()) {
			ZipEntry e = entries.nextElement();
			if (e.isDirectory()) continue;
			
			String entry = e.getName();

			if (entry.contains("/_")) continue;
			
			if (entry.endsWith(".xie")) {
				XieLog.log().config("Found "+entry);
				String path = filePath;
				if (entry.contains("/")) path += "/"+entry.substring(0,entry.lastIndexOf("/"));
				String name = entry.substring(entry.lastIndexOf("/")+1);
				
				try {
					Work.instance().findWork(zip.getInputStream(e), path, name);
				} catch (InvalidSyntaxException e1) {
					errorLog("Invalid JSON Syntax in file "+entry);
					e1.printStackTrace();
				} catch (IOException e1) {
					errorLog("Error reading file in zip: "+entry);
					e1.printStackTrace();
				}
			} else if (entry.endsWith(".xid")) {
				XieLog.log().config("Found "+entry);
				
				try {
					Work.instance().findIDs(zip.getInputStream(e));
				} catch (IOException e1) {
					errorLog("Error reading file in zip: "+entry);
					e1.printStackTrace();
				} catch (InvalidSyntaxException e1) {
					errorLog("Invalid syntax in ids file "+entry);
					e1.printStackTrace();
				}
			}
		}
		
		try {
			zip.close();
		} catch (IOException e) {
			errorLog("Error closing zip file: "+f.getName());
			e.printStackTrace();
		}
	}

	private boolean styleFound = false;
	private boolean mapFound = false;
	protected void getConfigFrom(File dir) {		
		if (!isValidDir(dir)) return;
		
		File listing[] = dir.listFiles();
		if (listing==null) return;
		for (File f : listing) {
			if (f.getName().endsWith(".css")) {
				try {
					XieLog.log().readStyleFromFile(new FileReader(f));
				} catch (IOException e) {
					e.printStackTrace();
				}
				styleFound=true;
			} else if (f.getName().endsWith(".map")) {
				try {
					XieLog.log().importTranslations(new FileReader(f));
				} catch (IOException e) {
					e.printStackTrace();
				}
				mapFound=true;
			}
		}
	}
    
	protected static boolean isConfigFile(File f) {
    	if (!f.isFile()) return false;
    	if (f.getName().startsWith("_")) return false;
    	for (String extension : validExtensions) if (f.getName().endsWith(extension)) return true;
    	
    	return false;
    }
    
	protected static boolean isValidDir(File f) {
    	if (!f.exists()) return false;
    	if (f.getName().startsWith("_")) return false;
    	
    	if (f.isDirectory()) return true;
    	if (f.getName().endsWith(".zip")) return true;
    	return false;
    }
	
	private void outputLogFile(boolean success) {
		try {
			BufferedWriter logWriter = new BufferedWriter(new FileWriter(PathManager.get().textLogFile()));
			
			logWriter.write(toString()+" Work Log - "+Calendar.getInstance().getTime());
			logWriter.newLine();logWriter.newLine();
			
			if (success) logWriter.write("All jobs successfully resolved! :)");
			else writeAll(logWriter, Work.instance().getFailMsgs());
			logWriter.newLine();logWriter.newLine();
			
			logWriter.write("Found");
			for (JobType t : JobType.values()) 
				logWriter.write(" "+Work.instance().jobsFound[t.ordinal()]+" "+Job.getType(t));
			logWriter.write(" jobs.");
						logWriter.newLine();
			logWriter.write("Added");
			for (JobType t : JobType.values()) 
				logWriter.write(" "+Work.instance().jobsCompleted[t.ordinal()]+" "+Job.getTypePlural(t));
			logWriter.write(".");
			logWriter.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void writeAll(BufferedWriter writer, String out[]) throws IOException {
		for (String s : out) {
			writer.write(s);
			writer.newLine();
		}
	}
	
	private static boolean firstLog = true;
	protected void errorLog(String str) {
		try {
			BufferedWriter logWriter = new BufferedWriter(new FileWriter(PathManager.get().textLogFile(),true));
			if (firstLog) {
				logWriter.newLine();
				firstLog=false;
			}
			logWriter.newLine();
			logWriter.write("["+Calendar.getInstance().getTime()+"] "+str);
			logWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.err.println(str);
	}
	
	private static final int BUFFER = 153600;
	
	private boolean downloadFile(String filename, String urlStr) {
		InputStream reader;
		
	 	try {
			URL url = new URL(urlStr);
			url.openConnection();
			reader = url.openStream();
			return writeFile(filename, reader);
		} catch (MalformedURLException e) {
			// TODO report fail URL
			return false;
		} catch (IOException e) {
			// TODO report io exception
			return false;
		}
	}
	
	private boolean writeFile(String filename, InputStream reader) throws IOException {
		File file = PathManager.get().cacheDir();
		FileOutputStream writer = new FileOutputStream(file);
		byte buffer[] = new byte[BUFFER];
		while (reader.read(buffer) > 0) {
			writer.write(buffer);
		}
		writer.close();
		return true;
	}

}
